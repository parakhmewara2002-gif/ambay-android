package com.ambay.billing.core.network

import com.ambay.billing.core.crypto.EnvelopeVerifier
import com.ambay.billing.core.model.ServerKey
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject

/**
 * Validates server response envelopes, updates ClockSync, and catches 401 clock skew / 403 key revoked.
 */
class EnvelopeInterceptor(
    private val getPinnedServerKeys: () -> Map<String, ServerKey>,
    private val onKeyRevoked: () -> Unit
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        // 403 key_revoked handler: "Is device ko owner ne logout kar diya"
        if (response.code == 403) {
            val peekBody = response.peekBody(2048).string()
            if (peekBody.contains("key_revoked")) {
                onKeyRevoked()
            }
        }

        // 401 clock_skew handler: device clock differs by > 5 min
        if (response.code == 401) {
            val peekBody = response.peekBody(2048).string()
            if (peekBody.contains("clock_skew")) {
                // Skew detected: the caller repository will resync via /v1/health
                return response
            }
        }

        if (!response.isSuccessful) {
            return response
        }

        val bodyStr = response.body?.string() ?: return response

        // Verify signed envelope if it contains {p, skid, sig}
        val pinnedKeys = getPinnedServerKeys()
        val expectedNonce = request.header("X-Amb-Nonce") ?: request.url.queryParameter("nonce")
        if (pinnedKeys.isNotEmpty()) {
            // Once a trust root is pinned, EVERY successful response must be a verified envelope bound to our request nonce.
            val verifyResult = EnvelopeVerifier.verify(bodyStr, pinnedKeys, expectedType = null, expectedNonce = expectedNonce)
            if (verifyResult is EnvelopeVerifier.VerifyResult.Success) {
                ClockSync.updateServerTime(verifyResult.serverTs)
                // Pass the verified inner payload 'p' to the application downstream
                val newBody = verifyResult.payloadJson.toResponseBody(response.body?.contentType())
                return response.newBuilder().body(newBody).build()
            } else if (verifyResult is EnvelopeVerifier.VerifyResult.Error) {
                // Signature verification failed: reject response
                return response.newBuilder()
                    .code(400)
                    .message("Envelope verification failed: ${verifyResult.reason}")
                    .body("{}".toResponseBody(response.body?.contentType()))
                    .build()
            }
        }

        // Reconstruct response body if not replaced
        val recreatedBody = bodyStr.toResponseBody(response.body?.contentType())
        return response.newBuilder().body(recreatedBody).build()
    }
}

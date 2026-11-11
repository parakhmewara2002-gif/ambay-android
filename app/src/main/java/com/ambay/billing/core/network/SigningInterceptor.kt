package com.ambay.billing.core.network

import com.ambay.billing.core.crypto.Base64Url
import com.ambay.billing.core.crypto.DeviceKeyManager
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import java.security.SecureRandom

/**
 * Signs outgoing requests using the hardware-backed device key.
 * Headers:
 * X-Amb-Kid: device key id
 * X-Amb-Ts: clock-corrected epoch ms
 * X-Amb-Nonce: random base64url 16 bytes
 * X-Amb-Sig: base64url(sign("AMBREQ1\n" + method + "\n" + pathAndQuery + "\n" + ts + "\n" + nonce + "\n" + sha256hex(body)))
 */
class SigningInterceptor(
    private val deviceKeyManager: DeviceKeyManager,
    private val getDeviceKid: () -> String?
) : Interceptor {

    private val random = SecureRandom()

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val path = original.url.encodedPath

        // Do not sign pairing redeem or public health checks
        if (path.contains("v1/pair/redeem") || (path.contains("v1/health") && original.header("X-Amb-Kid") == null)) {
            return chain.proceed(original)
        }

        val kid = getDeviceKid() ?: return chain.proceed(original)

        val ts = ClockSync.getCorrectedEpochMs().toString()
        val nonceBytes = ByteArray(16)
        random.nextBytes(nonceBytes)
        val nonce = Base64Url.encode(nonceBytes)

        val method = original.method
        val pathAndQuery = original.url.encodedPath + (if (original.url.encodedQuery != null) "?" + original.url.encodedQuery else "")

        val bodyString = getRequestBodyString(original)
        val sig = deviceKeyManager.signRequest(method, pathAndQuery, ts, nonce, bodyString)

        val signedRequest = original.newBuilder()
            .header("X-Amb-Kid", kid)
            .header("X-Amb-Ts", ts)
            .header("X-Amb-Nonce", nonce)
            .header("X-Amb-Sig", sig)
            .build()

        return chain.proceed(signedRequest)
    }

    private fun getRequestBodyString(request: Request): String {
        val body = request.body ?: return ""
        val buffer = Buffer()
        body.writeTo(buffer)
        return buffer.readUtf8()
    }
}

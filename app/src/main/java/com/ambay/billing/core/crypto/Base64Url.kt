package com.ambay.billing.core.crypto

import java.util.Base64

object Base64Url {
    fun encode(bytes: ByteArray): String {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun decode(str: String): ByteArray {
        return Base64.getUrlDecoder().decode(str.trim())
    }
}

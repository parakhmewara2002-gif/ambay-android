package com.ambay.billing.core.network

import java.util.concurrent.atomic.AtomicLong

/**
 * Manages clock skew correction between Android device and Cloudflare Worker server.
 */
object ClockSync {

    private val clockOffsetMs = AtomicLong(0L)

    fun updateServerTime(serverTs: Long) {
        val now = System.currentTimeMillis()
        val offset = serverTs - now
        clockOffsetMs.set(offset)
    }

    fun getCorrectedEpochMs(): Long {
        return System.currentTimeMillis() + clockOffsetMs.get()
    }

    fun getOffset(): Long {
        return clockOffsetMs.get()
    }
}

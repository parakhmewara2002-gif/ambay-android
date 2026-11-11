package com.ambay.billing.core.crypto

import android.os.Build
import android.os.Debug
import java.io.File

/**
 * Soft security audit for root and debugger detection.
 * Emits warnings only; does not block the user or crash the app.
 */
object SecurityAudit {

    data class SecurityStatus(
        val isRooted: Boolean,
        val isDebuggerConnected: Boolean,
        val isEmulator: Boolean,
        val warnings: List<String>
    )

    fun checkSecurity(): SecurityStatus {
        val warnings = mutableListOf<String>()
        val rooted = checkRoot()
        if (rooted) warnings.add("Device par root access detect hua")

        val debugger = Debug.isDebuggerConnected() || Debug.waitingForDebugger()
        if (debugger) warnings.add("Debugger connected hai")

        val emulator = checkEmulator()
        if (emulator) warnings.add("Emulator environment detect hua")

        return SecurityStatus(
            isRooted = rooted,
            isDebuggerConnected = debugger,
            isEmulator = emulator,
            warnings = warnings
        )
    }

    private fun checkRoot(): Boolean {
        val buildTags = Build.TAGS
        if (buildTags != null && buildTags.contains("test-keys")) return true

        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su"
        )
        for (path in paths) {
            if (File(path).exists()) return true
        }
        return false
    }

    private fun checkEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")
                || "google_sdk" == Build.PRODUCT)
    }
}

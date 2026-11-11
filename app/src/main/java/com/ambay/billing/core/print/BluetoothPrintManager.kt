package com.ambay.billing.core.print

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

class BluetoothPrintManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("ambay_printer_prefs", Context.MODE_PRIVATE)
    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // Standard Serial Port Profile

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDevice> {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return emptyList()
        return adapter.bondedDevices?.toList() ?: emptyList()
    }

    fun getSavedPrinterMac(): String? {
        return prefs.getString(KEY_PRINTER_MAC, null)
    }

    fun savePrinterMac(mac: String) {
        prefs.edit().putString(KEY_PRINTER_MAC, mac).apply()
    }

    fun is80mmPrinter(): Boolean {
        return prefs.getBoolean(KEY_IS_80MM, false)
    }

    fun set80mmPrinter(is80mm: Boolean) {
        prefs.edit().putBoolean(KEY_IS_80MM, is80mm).apply()
    }

    @SuppressLint("MissingPermission")
    suspend fun printBytes(deviceMac: String, data: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        val adapter = BluetoothAdapter.getDefaultAdapter()
            ?: return@withContext Result.failure(IOException("Bluetooth is not available on this device"))

        if (!adapter.isEnabled) {
            return@withContext Result.failure(IOException("Bluetooth is turned off"))
        }

        var socket: BluetoothSocket? = null
        try {
            val device = adapter.getRemoteDevice(deviceMac)
            adapter.cancelDiscovery()

            socket = device.createRfcommSocketToServiceRecord(sppUuid)
            socket.connect()

            val os = socket.outputStream
            os.write(data)
            os.flush()

            savePrinterMac(deviceMac)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(IOException("Printer connection failed: ${e.message}"))
        } finally {
            try {
                socket?.close()
            } catch (ignored: Exception) {}
        }
    }

    companion object {
        private const val KEY_PRINTER_MAC = "saved_printer_mac"
        private const val KEY_IS_80MM = "printer_is_80mm"
    }
}

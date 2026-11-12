package com.ambay.billing.hardware

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import com.ambay.billing.core.MoneyEngine
import com.ambay.billing.core.ShopInfo
import com.ambay.billing.core.WSale
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class BtPrinterDevice(
    val name: String,
    val mac: String
)

@Singleton
class EscPosPrinter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    @SuppressLint("MissingPermission")
    fun bondedPrinters(): List<BtPrinterDevice> {
        val mgr = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return emptyList()
        val adapter: BluetoothAdapter = mgr.adapter ?: return emptyList()
        return runCatching {
            adapter.bondedDevices.orEmpty().map { d: BluetoothDevice ->
                BtPrinterDevice(name = d.name ?: "Bluetooth Printer", mac = d.address)
            }
        }.getOrDefault(emptyList())
    }

    @SuppressLint("MissingPermission")
    suspend fun printWSaleReceipt(
        mac: String,
        widthMm: Int,
        shop: ShopInfo,
        sale: WSale
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val mgr = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val adapter = mgr.adapter ?: error("Bluetooth available nahi hai")
            val device = adapter.getRemoteDevice(mac)
            val socket: BluetoothSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            socket.use { sock ->
                runCatching { adapter.cancelDiscovery() }
                sock.connect()
                val payload = buildReceiptBytes(widthMm, shop, sale)
                sock.outputStream.write(payload)
                sock.outputStream.flush()
            }
        }
    }

    fun buildReceiptBytes(widthMm: Int, shop: ShopInfo, sale: WSale): ByteArray {
        val cols = if (widthMm >= 80) 48 else 32
        val out = ByteArrayOutputStream()
        fun cmd(vararg b: Int) { b.forEach { out.write(it) } }
        fun text(s: String) { out.write(s.toByteArray(Charsets.UTF_8)) }
        fun line(s: String = "") { text(s + "\n") }
        fun hr() { line("-".repeat(cols)) }
        fun lr(left: String, right: String) {
            val gap = (cols - left.length - right.length).coerceAtLeast(1)
            line(left.take(cols - right.length - 1) + " ".repeat(gap) + right)
        }

        // ESC @ Initialize
        cmd(0x1B, 0x40)
        // Center align + Bold
        cmd(0x1B, 0x61, 0x01)
        cmd(0x1B, 0x45, 0x01)
        line(shop.name.ifBlank { "AMBAY MOBILE ACCESSORIES" })
        cmd(0x1B, 0x45, 0x00)
        if (shop.ph.isNotBlank()) line("Mob: ${shop.ph}")
        if (shop.addr.isNotBlank()) line(shop.addr)
        hr()

        // Left align
        cmd(0x1B, 0x61, 0x00)
        val dt = SimpleDateFormat("dd/MM/yy hh:mm a", Locale("en", "IN")).format(Date(sale.date))
        lr("No: ${sale.no}", dt)
        if (sale.cust.name.isNotBlank()) line("Cust: ${sale.cust.name} ${sale.cust.phone}")
        hr()

        for (l in sale.lines) {
            line(l.name.take(cols))
            val qtyRate = "${l.qty} x ${MoneyEngine.fmtINR(l.op)}"
            val amt = MoneyEngine.fmtINR(l.total)
            lr(qtyRate, amt)
            if (l.disc > 0L) {
                lr("  Disc:", "-${MoneyEngine.fmtINR(l.disc)}")
            }
        }
        hr()
        if (sale.tot.disc > 0L) {
            lr("Original:", MoneyEngine.fmtINR(sale.tot.orig))
            lr("Discount:", "-${MoneyEngine.fmtINR(sale.tot.disc)}")
        }
        cmd(0x1B, 0x45, 0x01)
        lr("TOTAL:", MoneyEngine.fmtINR(sale.tot.total))
        cmd(0x1B, 0x45, 0x00)
        lr("Payment:", sale.pay)
        hr()

        // Code128 barcode of sale.no
        val codeBytes = sale.no.toByteArray(Charsets.US_ASCII)
        cmd(0x1B, 0x61, 0x01)
        cmd(0x1D, 0x68, 48) // height
        cmd(0x1D, 0x77, 2)  // module width
        cmd(0x1D, 0x6B, 73, codeBytes.size)
        out.write(codeBytes)
        line("")

        // QR Code of Transaction ID
        val qrData = sale.id.toByteArray(Charsets.UTF_8)
        val pLen = qrData.size + 3
        cmd(0x1D, 0x28, 0x6B, 0x04, 0x00, 0x31, 0x41, 0x32, 0x00) // model 2
        cmd(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x43, 0x04)       // size 4
        cmd(0x1D, 0x28, 0x6B, pLen and 0xFF, (pLen shr 8) and 0xFF, 0x31, 0x50, 0x30)
        out.write(qrData)
        cmd(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x51, 0x30)

        line("")
        line("Dhanyavaad! Visit again.")
        line("")
        line("")
        // Feed and cut
        cmd(0x1D, 0x56, 0x42, 0x00)
        return out.toByteArray()
    }
}

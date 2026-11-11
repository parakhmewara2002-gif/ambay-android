package com.ambay.billing.core.print

import com.ambay.billing.core.model.Money
import com.ambay.billing.core.model.Sale
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EscPosBuilder(private val is80mm: Boolean = false) {

    private val lineWidth = if (is80mm) 48 else 32
    private val baos = ByteArrayOutputStream()

    fun reset(): EscPosBuilder {
        baos.write(byteArrayOf(0x1B, 0x40)) // ESC @
        return this
    }

    fun alignCenter(): EscPosBuilder {
        baos.write(byteArrayOf(0x1B, 0x61, 0x01)) // ESC a 1
        return this
    }

    fun alignLeft(): EscPosBuilder {
        baos.write(byteArrayOf(0x1B, 0x61, 0x00)) // ESC a 0
        return this
    }

    fun alignRight(): EscPosBuilder {
        baos.write(byteArrayOf(0x1B, 0x61, 0x02)) // ESC a 2
        return this
    }

    fun bold(on: Boolean): EscPosBuilder {
        baos.write(byteArrayOf(0x1B, 0x45, if (on) 0x01 else 0x00)) // ESC E n
        return this
    }

    fun doubleSize(on: Boolean): EscPosBuilder {
        val size = if (on) 0x11 else 0x00 // Double width & double height
        baos.write(byteArrayOf(0x1D, 0x21, size.toByte())) // GS ! n
        return this
    }

    fun line(text: String = ""): EscPosBuilder {
        baos.write(text.toByteArray(Charsets.UTF_8))
        baos.write(0x0A) // LF
        return this
    }

    fun separator(char: Char = '-'): EscPosBuilder {
        val line = char.toString().repeat(lineWidth)
        return line(line)
    }

    fun row(left: String, right: String): EscPosBuilder {
        val spaces = lineWidth - left.length - right.length
        val lineText = if (spaces > 0) {
            left + " ".repeat(spaces) + right
        } else {
            // If text is too long, truncate left
            val maxLeft = lineWidth - right.length - 1
            val truncatedLeft = if (maxLeft > 0) left.take(maxLeft) else left
            truncatedLeft + " " + right
        }
        return line(lineText)
    }

    fun qrCode(data: String): EscPosBuilder {
        val bytes = data.toByteArray(Charsets.UTF_8)
        val len = bytes.size + 3

        // Function 167: QR Code model 2
        baos.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x04, 0x00, 0x31, 0x41, 0x32, 0x00))

        // Function 169: Module size 4
        baos.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x43, 0x04))

        // Function 180: Store data
        val pL = (len and 0xFF).toByte()
        val pH = ((len shr 8) and 0xFF).toByte()
        baos.write(byteArrayOf(0x1D, 0x28, 0x6B, pL, pH, 0x31, 0x50, 0x30))
        baos.write(bytes)

        // Function 181: Print QR code
        baos.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x51, 0x30))
        return this
    }

    fun feedAndCut(): EscPosBuilder {
        baos.write(byteArrayOf(0x1B, 0x64, 0x04)) // Feed 4 lines
        baos.write(byteArrayOf(0x1D, 0x56, 0x00)) // GS V 0 (full cut)
        return this
    }

    fun build(): ByteArray {
        return baos.toByteArray()
    }

    companion object {
        fun buildSaleReceipt(sale: Sale, is80mm: Boolean = false): ByteArray {
            val builder = EscPosBuilder(is80mm)
            val dateFormat = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.ENGLISH)
            val dateStr = dateFormat.format(Date(sale.date))

            builder.reset()
                .alignCenter()
                .bold(true)
                .doubleSize(true)
                .line("AMBAY MOBILE ACCESSORIES")
                .doubleSize(false)
                .line("Mobile & Accessories Specialist")
                .line("Without Bill / Cash Memo")
                .separator('=')
                .alignLeft()
                .row("Sale No: ${sale.no}", "Type: WB")
                .row("Date: $dateStr", "")
            if (sale.cust.name.isNotEmpty()) {
                builder.line("Customer: ${sale.cust.name}")
            }
            if (sale.cust.phone.isNotEmpty()) {
                builder.line("Phone: ${sale.cust.phone}")
            }
            builder.separator('-')
                .bold(true)
                .row("Item (Qty x Rate)", "Total")
                .bold(false)
                .separator('-')

            for (line in sale.lines) {
                val itemName = line.name + (if (line.model.isNotEmpty()) " (${line.model})" else "")
                val warTag = if (line.ws == "W") " [WAR]" else ""
                builder.line(itemName + warTag)
                val qtyRate = "${line.qty} x ${Money.formatInr(line.op)}"
                val lineTotal = Money.formatInr(line.total)
                builder.row("  $qtyRate", lineTotal)
                if (line.disc > 0) {
                    builder.row("  Discount:", "-${Money.formatInr(line.disc)}")
                }
            }

            builder.separator('-')
                .bold(true)
                .row("Total Items / Qty:", "${sale.lines.size} / ${sale.tot.qty}")
            if (sale.tot.disc > 0) {
                builder.row("Total Discount:", "-${Money.formatInr(sale.tot.disc)}")
            }
            builder.doubleSize(true)
                .row("NET TOTAL:", Money.formatInr(sale.tot.total))
                .doubleSize(false)
                .bold(false)
                .row("Payment Mode:", sale.pay)
                .separator('=')
                .alignCenter()
                .line("Dhanyavaad! Phir Padhaarein.")
                .line("Txn ID: ${sale.id.take(8)}")
                .feedAndCut()

            return builder.build()
        }
    }
}

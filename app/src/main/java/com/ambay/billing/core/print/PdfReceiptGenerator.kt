package com.ambay.billing.core.print

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.ambay.billing.core.model.Money
import com.ambay.billing.core.model.Sale
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfReceiptGenerator {

    fun generatePdf(context: Context, sale: Sale): File {
        val cacheDir = File(context.cacheDir, "receipts").apply { mkdirs() }
        val file = File(cacheDir, "${sale.no}_${sale.id.take(8)}.pdf")

        val pageWidth = 384 // ~58mm thermal width in points (72 dpi)
        val estimatedHeight = 600 + (sale.lines.size * 50)
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, estimatedHeight, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas

        drawReceipt(canvas, sale, pageWidth)

        doc.finishPage(page)
        val fos = FileOutputStream(file)
        doc.writeTo(fos)
        doc.close()
        fos.close()

        return file
    }

    private fun drawReceipt(canvas: Canvas, sale: Sale, width: Int) {
        val paint = Paint().apply {
            color = Color.BLACK
            isAntiAlias = true
        }

        var y = 40f
        val centerX = width / 2f
        val leftX = 20f
        val rightX = width - 20f

        // Header
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 18f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("AMBAY MOBILE ACCESSORIES", centerX, y, paint)
        y += 20f

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 12f
        canvas.drawText("Mobile & Accessories Specialist", centerX, y, paint)
        y += 18f
        canvas.drawText("Without Bill / Cash Memo", centerX, y, paint)
        y += 15f

        // Separator
        paint.strokeWidth = 2f
        canvas.drawLine(leftX, y, rightX, y, paint)
        y += 20f

        // Sale meta
        paint.textSize = 12f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("Sale No: ${sale.no}", leftX, y, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("WB", rightX, y, paint)
        y += 18f

        val dateFormat = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.ENGLISH)
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("Date: ${dateFormat.format(Date(sale.date))}", leftX, y, paint)
        y += 18f

        if (sale.cust.name.isNotEmpty()) {
            canvas.drawText("Customer: ${sale.cust.name}", leftX, y, paint)
            y += 18f
        }
        if (sale.cust.phone.isNotEmpty()) {
            canvas.drawText("Phone: ${sale.cust.phone}", leftX, y, paint)
            y += 18f
        }

        // Table Header
        y += 5f
        canvas.drawLine(leftX, y, rightX, y, paint)
        y += 16f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Item / Qty", leftX, y, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Amount", rightX, y, paint)
        y += 10f
        canvas.drawLine(leftX, y, rightX, y, paint)
        y += 18f

        // Items
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        for (line in sale.lines) {
            val title = line.name + (if (line.model.isNotEmpty()) " (${line.model})" else "")
            val warTag = if (line.ws == "W") " [WAR]" else ""
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText(title + warTag, leftX, y, paint)
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(Money.formatInr(line.total), rightX, y, paint)
            y += 16f

            paint.color = Color.DKGRAY
            paint.textSize = 10f
            paint.textAlign = Paint.Align.LEFT
            val subText = "${line.qty} x ${Money.formatInr(line.op)}" + (if (line.disc > 0) " (Disc: -${Money.formatInr(line.disc)})" else "")
            canvas.drawText(subText, leftX + 10f, y, paint)
            paint.color = Color.BLACK
            paint.textSize = 12f
            y += 20f
        }

        // Totals
        canvas.drawLine(leftX, y, rightX, y, paint)
        y += 18f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("Total Items / Qty: ${sale.lines.size} / ${sale.tot.qty}", leftX, y, paint)
        y += 18f

        if (sale.tot.disc > 0) {
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("Total Discount:", leftX, y, paint)
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("-${Money.formatInr(sale.tot.disc)}", rightX, y, paint)
            y += 18f
        }

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 16f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("NET TOTAL:", leftX, y, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(Money.formatInr(sale.tot.total), rightX, y, paint)
        y += 22f

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 12f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("Payment Mode: ${sale.pay}", leftX, y, paint)
        y += 20f

        canvas.drawLine(leftX, y, rightX, y, paint)
        y += 24f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("Dhanyavaad! Phir Padhaarein.", centerX, y, paint)
        y += 16f
        paint.textSize = 10f
        paint.color = Color.GRAY
        canvas.drawText("Txn: ${sale.id}", centerX, y, paint)
    }
}

package com.ambay.billing.hardware

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.core.content.FileProvider
import com.ambay.billing.core.Bill
import com.ambay.billing.core.Code128Encoder
import com.ambay.billing.core.Item
import com.ambay.billing.core.MoneyEngine
import com.ambay.billing.core.ShopInfo
import com.ambay.billing.core.TrustCrypto
import com.ambay.billing.core.WSale
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PdfBillGenerator @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale("en", "IN"))

    fun generateBillPdf(bill: Bill, paperSize: String = "A4"): File {
        val (wPt, hPt) = when (paperSize) {
            "A5" -> 420 to 595
            "4X6" -> 288 to 432
            else -> 595 to 842
        }
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(wPt, hPt, 1).create()
        val page = doc.startPage(pageInfo)
        drawPaperBill(page.canvas, wPt.toFloat(), hPt.toFloat(), bill)
        doc.finishPage(page)

        val dir = File(context.cacheDir, "bills").apply { mkdirs() }
        val safeNo = bill.no.replace(Regex("[^\\w.\\-]+"), "_").trim('_').ifBlank { "Bill" }
        val outFile = File(dir, "${safeNo}_${paperSize}.pdf")
        FileOutputStream(outFile).use { doc.writeTo(it) }
        doc.close()

        // Append AMBM1 manifest token as PDF comment so offline PDF verifier can extract it
        if (bill.dsig != null) {
            val manifest = TrustCrypto.buildManifestMap(bill, bill.dsig.kid, bill.dsig.tz)
            val tok = TrustCrypto.makeToken(manifest, bill.dsig)
            outFile.appendText("\n% $tok\n")
        }
        return outFile
    }

    fun generateWSalePdf(shop: ShopInfo, sale: WSale): File {
        val wPt = 288
        val hPt = 432 + (sale.lines.size * 24)
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(wPt, hPt, 1).create())
        val c = page.canvas
        val titleP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 14f; typeface = Typeface.DEFAULT_BOLD }
        val bodyP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 9.5f }
        val boldP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 10f; typeface = Typeface.DEFAULT_BOLD }
        val lineP = Paint().apply { color = Color.DKGRAY; strokeWidth = 0.8f }

        var y = 24f
        c.drawText(shop.name.ifBlank { "AMBAY MOBILE ACCESSORIES" }, 14f, y, titleP); y += 14f
        if (shop.ph.isNotBlank()) { c.drawText("Mob: ${shop.ph}", 14f, y, bodyP); y += 12f }
        c.drawLine(14f, y, wPt - 14f, y, lineP); y += 14f
        c.drawText("No: ${sale.no}", 14f, y, boldP)
        c.drawText(dateFormat.format(Date(sale.date)), 120f, y, bodyP); y += 14f
        if (sale.cust.name.isNotBlank()) { c.drawText("Customer: ${sale.cust.name} ${sale.cust.phone}", 14f, y, bodyP); y += 14f }
        c.drawLine(14f, y, wPt - 14f, y, lineP); y += 14f

        for (l in sale.lines) {
            c.drawText("${l.name} (${l.qty} x ${MoneyEngine.fmtINR(l.op)})", 14f, y, bodyP)
            c.drawText(MoneyEngine.fmtINR(l.total), wPt - 70f, y, bodyP)
            y += 16f
        }
        c.drawLine(14f, y, wPt - 14f, y, lineP); y += 16f
        c.drawText("TOTAL (${sale.pay}):", 14f, y, titleP)
        c.drawText(MoneyEngine.fmtINR(sale.tot.total), wPt - 85f, y, titleP); y += 22f
        c.drawText("Dhanyavaad! Visit again.", 14f, y, bodyP)
        doc.finishPage(page)

        val dir = File(context.cacheDir, "bills").apply { mkdirs() }
        val outFile = File(dir, "${sale.no}.pdf")
        FileOutputStream(outFile).use { doc.writeTo(it) }
        doc.close()
        return outFile
    }

    fun generateBarcodeLabelsPdf(items: List<Item>): File {
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
        val c = page.canvas
        val titleP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 9f; typeface = Typeface.DEFAULT_BOLD }
        val subP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; textSize = 8f }
        val barP = Paint().apply { color = Color.BLACK }
        val boxP = Paint().apply { color = Color.LTGRAY; style = Paint.Style.STROKE; strokeWidth = 0.8f }

        var col = 0
        var row = 0
        val cellW = 180f
        val cellH = 95f
        for (it in items.take(24)) {
            val x0 = 22f + col * (cellW + 8f)
            val y0 = 24f + row * (cellH + 8f)
            c.drawRect(x0, y0, x0 + cellW, y0 + cellH, boxP)
            c.drawText(it.name.take(26), x0 + 8f, y0 + 14f, titleP)
            c.drawText("${MoneyEngine.fmtINR(it.price)}  [${it.ws}]", x0 + 8f, y0 + 26f, subP)
            val code = it.code.ifBlank { it.id.take(8) }
            runCatching {
                val enc = Code128Encoder.encode(code)
                val barW = (cellW - 20f) / enc.totalModules.toFloat()
                for ((start, width) in enc.runs) {
                    val bx = x0 + 10f + start * barW
                    c.drawRect(bx, y0 + 32f, bx + width * barW, y0 + 72f, barP)
                }
            }
            c.drawText(code, x0 + 10f, y0 + 84f, subP)
            col++
            if (col >= 3) { col = 0; row++ }
        }
        doc.finishPage(page)
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val outFile = File(dir, "barcode_labels.pdf")
        FileOutputStream(outFile).use { doc.writeTo(it) }
        doc.close()
        return outFile
    }

    private fun drawPaperBill(c: Canvas, w: Float, h: Float, bill: Bill) {
        val m = 24f
        val headP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 15f; typeface = Typeface.DEFAULT_BOLD }
        val boldP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 9.5f; typeface = Typeface.DEFAULT_BOLD }
        val bodyP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 9f }
        val smallP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; textSize = 7.5f }
        val boxP = Paint().apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 1.2f }
        val thinP = Paint().apply { color = Color.GRAY; strokeWidth = 0.6f }

        var y = m + 16f
        c.drawRect(m, m, w - m, y + 18f, boxP)
        c.drawText(bill.shop.name.ifBlank { "AMBAY MOBILE ACCESSORIES" }, m + 8f, y, headP)
        c.drawText("TAX INVOICE", w - m - 90f, y, boldP)
        y += 28f

        if (bill.shop.addr.isNotBlank() || bill.shop.gst.isNotBlank()) {
            c.drawText("${bill.shop.addr}  GSTIN: ${bill.shop.gst}", m, y, smallP); y += 12f
        }
        if (bill.shop.ph.isNotBlank()) {
            c.drawText("Shop Mobile: ${bill.shop.ph}", m, y, bodyP); y += 14f
        }

        c.drawLine(m, y, w - m, y, thinP); y += 14f
        c.drawText("Bill No: ${bill.no}", m, y, boldP)
        c.drawText("Date: ${dateFormat.format(Date(bill.date))}", w * 0.45f, y, bodyP); y += 13f
        c.drawText("Customer: ${bill.cust.name.ifBlank { "Walk-in" }}  ${bill.cust.phone}", m, y, bodyP)
        c.drawText("Pay: ${bill.pay}", w * 0.72f, y, boldP); y += 16f

        // Table Header
        c.drawLine(m, y, w - m, y, boxP); y += 12f
        c.drawText("#", m + 2f, y, boldP)
        c.drawText("Item", m + 22f, y, boldP)
        c.drawText("Qty", w * 0.56f, y, boldP)
        c.drawText("Rate", w * 0.66f, y, boldP)
        c.drawText("Amount", w - m - 52f, y, boldP)
        y += 5f
        c.drawLine(m, y, w - m, y, boxP); y += 13f

        bill.lines.forEachIndexed { idx, l ->
            c.drawText("${idx + 1}", m + 2f, y, bodyP)
            val title = buildString {
                append(l.name)
                if (l.model.isNotBlank()) append(" (${l.model})")
                if (l.war.isNotBlank()) append(" [W:${MoneyEngine.warLabel(l.war)}]")
            }
            c.drawText(title.take(38), m + 22f, y, bodyP)
            c.drawText("${l.qty}", w * 0.56f, y, bodyP)
            c.drawText(MoneyEngine.fmtINR(l.rate), w * 0.66f, y, bodyP)
            c.drawText(MoneyEngine.fmtINR(l.total), w - m - 52f, y, bodyP)
            y += 15f
            if (l.ph != null && (l.ph.imei1.isNotBlank() || l.ph.sn.isNotBlank())) {
                c.drawText("  IMEI: ${l.ph.imei1} ${l.ph.imei2} SN: ${l.ph.sn}".trim(), m + 22f, y, smallP)
                y += 12f
            }
        }

        c.drawLine(m, y, w - m, y, thinP); y += 16f
        c.drawText("Subtotal: ${MoneyEngine.fmtINR(bill.tot.sub)}", w * 0.55f, y, bodyP); y += 13f
        if (bill.tot.disc > 0L) {
            c.drawText("Discount: -${MoneyEngine.fmtINR(bill.tot.disc)}", w * 0.55f, y, bodyP); y += 13f
        }
        if (bill.tot.tax > 0L) {
            if (bill.taxType == "inter") {
                c.drawText("IGST: ${MoneyEngine.fmtINR(bill.tot.igst)}", w * 0.55f, y, bodyP); y += 13f
            } else {
                c.drawText("CGST: ${MoneyEngine.fmtINR(bill.tot.cgst)}  SGST: ${MoneyEngine.fmtINR(bill.tot.sgst)}", w * 0.50f, y, bodyP); y += 13f
            }
        }
        c.drawText("GRAND TOTAL: ${MoneyEngine.fmtINR(bill.tot.total)}", w * 0.50f, y, headP); y += 18f
        c.drawText("In Words: ${MoneyEngine.amtWords(bill.tot.total)}", m, y, smallP); y += 16f

        // Anti-fake signed QR
        if (bill.dsig != null) {
            val qrPayload = TrustCrypto.encodePayload(bill)
            val qrBmp = createQrBitmap(qrPayload, 110)
            c.drawBitmap(qrBmp, w - m - 84f, y, null)
            c.drawText("Verified Digital Bill (Scan QR)", m, y + 14f, boldP)
            c.drawText("Key ID: ${bill.dsig.kid} | ${bill.dsig.alg}", m, y + 26f, smallP)
        }

        val terms = MoneyEngine.warTermsParts(bill.shop, bill.shop.warLang)
        if (terms.en.isNotBlank()) c.drawText(terms.en.take(90), m, h - m - 26f, smallP)
        if (terms.hi.isNotBlank()) c.drawText(terms.hi.take(90), m, h - m - 14f, smallP)
        c.drawText(bill.shop.foot, m, h - m, smallP)
    }

    fun createQrBitmap(content: String, sizePx: Int = 512): Bitmap {
        val hints = mapOf(EncodeHintType.MARGIN to 1)
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bmp
    }

    fun sharePdf(file: File, title: String = "Share Bill PDF") {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun printPdfWithSystemManager(activityContext: Context, file: File, jobName: String) {
        val pm = activityContext.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback?,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onLayoutCancelled()
                    return
                }
                val info = PrintDocumentInfo.Builder(file.name)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .build()
                callback?.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor?,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback?
            ) {
                try {
                    FileInputStream(file).use { input ->
                        FileOutputStream(destination!!.fileDescriptor).use { output ->
                            input.copyTo(output)
                        }
                    }
                    callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback?.onWriteFailed(e.message)
                }
            }
        }
        pm.print(jobName, adapter, PrintAttributes.Builder().build())
    }
}

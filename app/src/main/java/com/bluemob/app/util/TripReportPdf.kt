package com.bluemob.app.util

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.bluemob.app.chat.TripReport
import java.io.File

/** Writes a trip report as an A4 PDF: a title, then each section with its lines, page after page. */
object TripReportPdf {
    private const val W = 595
    private const val H = 842
    private const val M = 48

    fun write(file: File, title: String, subtitle: String, sections: List<TripReport.Section>): File {
        val doc = PdfDocument()
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 22f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = 0xFF0E3B2E.toInt() }
        val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; color = 0xFF5B6B66.toInt() }
        val headPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = 0xFF1F6FB2.toInt(); letterSpacing = 0.06f }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10.5f; color = 0xFF1A1A1A.toInt() }
        val rule = Paint().apply { color = 0xFFDDE3E0.toInt(); strokeWidth = 1f }
        var pageNo = 0
        var page: PdfDocument.Page? = null
        var y = 0f
        fun footer() = page?.canvas?.drawText("BlueMob trip report · page $pageNo", M.toFloat(), (H - 24).toFloat(), subPaint)
        fun newPage() {
            page?.let { footer(); doc.finishPage(it) }
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
            y = M.toFloat()
        }
        fun block(text: String, paint: TextPaint, before: Float = 0f, after: Float = 4f) {
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, W - 2 * M).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
            if (y + before + layout.height > H - 50) newPage()
            y += before
            val c = page!!.canvas
            c.save(); c.translate(M.toFloat(), y); layout.draw(c); c.restore()
            y += layout.height + after
        }
        newPage()
        block(title, titlePaint, after = 2f)
        block(subtitle, subPaint, after = 14f)
        sections.forEach { s ->
            if (y + 60 > H - 50) newPage()
            page!!.canvas.drawLine(M.toFloat(), y + 6, (W - M).toFloat(), y + 6, rule)
            block(s.title.uppercase(), headPaint, before = 14f, after = 6f)
            s.lines.forEach { block("•  $it", bodyPaint, after = 3f) }
        }
        footer(); doc.finishPage(page)
        file.parentFile?.mkdirs()
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }
}

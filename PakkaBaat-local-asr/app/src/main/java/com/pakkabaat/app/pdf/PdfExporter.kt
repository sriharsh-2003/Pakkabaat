package com.pakkabaat.app.pdf

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.pakkabaat.app.data.db.CertificateEntity
import com.pakkabaat.app.data.db.StructuredDocumentEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object PdfExporter {

    private const val PAGE_WIDTH = 595   // A4 at 72dpi
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 48f

    fun export(context: Context, document: StructuredDocumentEntity, certificate: CertificateEntity?): File {
        val pdf = PdfDocument()
        val titlePaint = Paint().apply { textSize = 20f; isFakeBoldText = true; typeface = Typeface.SANS_SERIF }
        val headingPaint = Paint().apply { textSize = 13f; isFakeBoldText = true; typeface = Typeface.SANS_SERIF; color = 0xFF6D00.toInt() or (0xFF shl 24) }
        val bodyPaint = Paint().apply { textSize = 11f; typeface = Typeface.SANS_SERIF }
        val smallPaint = Paint().apply { textSize = 9f; typeface = Typeface.SANS_SERIF; color = 0xFF666666.toInt() }

        var pageNumber = 1
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas = page.canvas
        var y = MARGIN

        fun newPageIfNeeded(lineHeight: Float) {
            if (y + lineHeight > PAGE_HEIGHT - MARGIN) {
                pdf.finishPage(page)
                pageNumber++
                page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
                canvas = page.canvas
                y = MARGIN
            }
        }

        canvas.drawText("PakkaBaat — Agreement Record", MARGIN, y, titlePaint)
        y += 28f
        canvas.drawText(
            SimpleDateFormat("d MMMM yyyy, h:mm a", Locale.getDefault()).format(Date(document.generatedAt)),
            MARGIN, y, smallPaint
        )
        y += 24f

        // Wrap and draw the human-readable document body, which already follows the
        // spec section 10.1 field structure (Date / Party A / Party B / Type / Terms / ...).
        val maxWidth = PAGE_WIDTH - 2 * MARGIN
        document.conditions.split("\n").forEach { rawLine ->
            val isHeadingLine = rawLine.trimEnd().endsWith(":") || rawLine.startsWith("PART")
            val paint = if (isHeadingLine) headingPaint else bodyPaint
            val wrapped = wrapText(rawLine, paint, maxWidth)
            wrapped.forEach { line ->
                newPageIfNeeded(16f)
                canvas.drawText(line, MARGIN, y, paint)
                y += 16f
            }
        }

        y += 12f
        newPageIfNeeded(20f)
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, smallPaint)
        y += 20f
        canvas.drawText("Proof details", MARGIN, y, headingPaint)
        y += 18f

        certificate?.let { cert ->
            val lines = listOf(
                "Recording hash (SHA-256): ${cert.audioSha256}",
                "Transcript hash (SHA-256): ${cert.transcriptSha256}",
                "Generated: ${SimpleDateFormat("d MMM yyyy, h:mm a z", Locale.getDefault()).format(Date(cert.generatedAt))}",
                "",
                cert.certificateStatement
            )
            lines.forEach { rawLine ->
                wrapText(rawLine, smallPaint, maxWidth).forEach { line ->
                    newPageIfNeeded(13f)
                    canvas.drawText(line, MARGIN, y, smallPaint)
                    y += 13f
                }
            }
        }

        pdf.finishPage(page)

        val outDir = File(context.filesDir, "documents").apply { mkdirs() }
        val outFile = File(outDir, "pakkabaat_${document.documentId}.pdf")
        outFile.outputStream().use { pdf.writeTo(it) }
        pdf.close()
        return outFile
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isBlank()) return listOf("")
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "${current} $word"
            if (paint.measureText(candidate) > maxWidth && current.isNotEmpty()) {
                lines.add(current.toString())
                current = StringBuilder(word)
            } else {
                current = StringBuilder(candidate)
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines
    }
}

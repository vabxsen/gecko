package com.gecko.feature.chat.component

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.gecko.core.model.chat.DocumentAttachment
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Bounded local extraction. Nothing leaves the device until the user presses Send. */
internal suspend fun readDocument(context: Context, uri: Uri): DocumentAttachment = withContext(Dispatchers.IO) {
    val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0) else null
    }?.take(160) ?: "Document"
    val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            if (output.size() + count > MAX_DOCUMENT_BYTES) throw IOException("Choose a document smaller than 10 MB.")
            output.write(buffer, 0, count)
        }
        output.toByteArray()
    } ?: throw IOException("Couldn't open that document. Select it again.")
    extractDocument(context, name, bytes, context.contentResolver.getType(uri).orEmpty())
}

internal suspend fun extractDocument(context: Context, name: String, bytes: ByteArray, mime: String): DocumentAttachment = withContext(Dispatchers.IO) {
    if (bytes.size > MAX_DOCUMENT_BYTES) throw IOException("Choose a document smaller than 10 MB.")
    val isPdf = bytes.take(5).toByteArray().toString(Charsets.US_ASCII) == "%PDF-"
    if (isPdf) {
        PDFBoxResourceLoader.init(context.applicationContext)
        PDDocument.load(bytes).use { pdf ->
            if (pdf.isEncrypted) throw IOException("This PDF is password protected. Choose an unlocked copy.")
            if (pdf.numberOfPages > 100) throw IOException("Choose a PDF with 100 pages or fewer.")
            if (!pdf.currentAccessPermission.canExtractContent()) throw IOException("This PDF doesn't allow text extraction.")
            val stripper = PDFTextStripper()
            var readablePages = 0
            val text = buildString {
                for (page in 1..pdf.numberOfPages) {
                    ensureActive()
                    stripper.startPage = page
                    stripper.endPage = page
                    val pageText = stripper.getText(pdf).trim()
                    if (pageText.isNotEmpty()) readablePages++
                    append("[Page $page]\n${pageText.ifBlank { "[No extractable text on this page]" }}\n\n")
                    if (length > MAX_DOCUMENT_CHARS) throw IOException("This document contains too much text. Attach a shorter section (up to 40,000 characters).")
                }
            }.trim()
            if (readablePages == 0) throw IOException("This PDF has no readable text. For a scanned page, attach an image instead.")
            DocumentAttachment(name, text, pdf.numberOfPages)
        }
    } else {
        if (!mime.startsWith("text/") && !name.endsWith(".md", true) && !name.endsWith(".txt", true))
            throw IOException("Choose a PDF, plain text, or Markdown document.")
        val text = Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString().trim()
        if (text.isBlank()) throw IOException("This document is empty.")
        if (text.length > MAX_DOCUMENT_CHARS) throw IOException("Attach a shorter section (up to 40,000 characters).")
        DocumentAttachment(name, text, null)
    }
}

private const val MAX_DOCUMENT_BYTES = 10 * 1024 * 1024
private const val MAX_DOCUMENT_CHARS = 40_000

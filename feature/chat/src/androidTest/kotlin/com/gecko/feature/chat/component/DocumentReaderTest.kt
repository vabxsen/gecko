package com.gecko.feature.chat.component

import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DocumentReaderTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun pdf(vararg pages: String): ByteArray {
        PDFBoxResourceLoader.init(context)
        return PDDocument().use { document ->
            pages.forEach { text ->
                val page = PDPage()
                document.addPage(page)
                if (text.isNotEmpty()) PDPageContentStream(document, page).use {
                    it.beginText()
                    it.setFont(PDType1Font.HELVETICA, 12f)
                    it.newLineAtOffset(50f, 700f)
                    it.showText(text)
                    it.endText()
                }
            }
            ByteArrayOutputStream().use { output -> document.save(output); output.toByteArray() }
        }
    }

    @Test fun pdfExtractionKeepsRealPageNumbers() = runBlocking {
        val document = extractDocument(context, "report.pdf", pdf("Revenue 100", "", "Costs 20"), "application/pdf")
        assertEquals(3, document.pageCount)
        assertTrue(document.text.contains("[Page 1]\nRevenue 100"))
        assertTrue(document.text.contains("[Page 2]\n[No extractable text"))
        assertTrue(document.text.contains("[Page 3]\nCosts 20"))
    }
    @Test fun unreadableAndOversizedDocumentsGiveActionableErrors() = runBlocking {
        try {
            extractDocument(context, "scan.pdf", pdf(""), "application/pdf")
            fail("Scanned PDF must not silently send an empty source")
        } catch (error: IOException) { assertTrue(error.message!!.contains("attach an image")) }
        try {
            extractDocument(context, "long.txt", "x".repeat(40_001).toByteArray(), "text/plain")
            fail("Oversized text must not silently truncate")
        } catch (error: IOException) { assertTrue(error.message!!.contains("shorter section")) }
    }
    @Test fun markdownKeepsContentWithoutInventingPageNumbers() = runBlocking {
        val text = "# Notes\nA useful fact."
        val document = extractDocument(context, "notes.md", text.toByteArray(), "text/markdown")
        assertEquals(text, document.text)
        assertNull(document.pageCount)
    }
}

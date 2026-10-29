package io.kopitiam.app.docs

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import io.kopitiam.app.pdf.PdfEngine
import io.kopitiam.app.scan.IdCardPdf
import io.kopitiam.app.scan.IdPageLayout
import io.kopitiam.app.scan.PaperSize
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A PDF kept on the phone. [id] is its file name. */
data class SavedDocument(
    val file: File,
    val name: String,
    val modified: Long,
    val pageCount: Int,
) {
    val id: String get() = file.name
}

/**
 * Scans saved inside the app (filesDir/documents). They never leave the phone
 * beyond the user's own device backup, and are listed newest first on Home
 * (Recents) and in the Files tab. Call from the main thread.
 */
object DocumentStore {

    private val _documents = MutableStateFlow<List<SavedDocument>>(emptyList())
    val documents: StateFlow<List<SavedDocument>> = _documents

    private fun folder(context: Context): File =
        File(context.filesDir, "documents").apply { mkdirs() }

    fun reload(context: Context) {
        val files = folder(context).listFiles { f -> f.isFile && f.name.endsWith(".pdf", true) }.orEmpty()
        _documents.value = files
            .map { f -> SavedDocument(f, f.name.removeSuffix(".pdf"), f.lastModified(), pageCount(f)) }
            .sortedByDescending { it.modified }
    }

    fun find(id: String): SavedDocument? = _documents.value.firstOrNull { it.id == id }

    /** Copy a freshly made PDF into the store under a unique, readable name. */
    fun save(context: Context, source: File, title: String): SavedDocument {
        val dest = uniqueFile(context, title)
        source.copyTo(dest, overwrite = false)
        reload(context)
        return find(dest.name) ?: SavedDocument(dest, dest.name.removeSuffix(".pdf"), dest.lastModified(), 0)
    }

    fun delete(context: Context, doc: SavedDocument) {
        doc.file.delete()
        reload(context)
    }

    /** Returns the renamed document, or null if the rename failed. */
    fun rename(context: Context, doc: SavedDocument, newName: String): SavedDocument? {
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || trimmed == doc.name) return doc
        val dest = uniqueFile(context, trimmed)
        if (!doc.file.renameTo(dest)) return null
        reload(context)
        return find(dest.name)
    }

    private fun uniqueFile(context: Context, title: String): File {
        val dir = folder(context)
        val base = safeName(title)
        var dest = File(dir, "$base.pdf")
        var n = 2
        while (dest.exists()) {
            dest = File(dir, "$base ($n).pdf")
            n++
        }
        return dest
    }

    fun safeName(raw: String): String {
        val cleaned = raw.replace('/', '-').replace(':', '.').trim()
        return if (cleaned.isEmpty()) "Document" else cleaned.take(80)
    }

    /** "Scan 29 Oct 2026, 19.06" (dots, not colons, so it is a valid file name). */
    fun title(prefix: String, date: Date = Date()): String =
        "$prefix ${SimpleDateFormat("d MMM yyyy, HH.mm", Locale.getDefault()).format(date)}"

    private fun pageCount(file: File): Int = try {
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        try { renderer.pageCount } finally { renderer.close(); pfd.close() }
    } catch (_: Exception) { 0 }

    /** Render one page to a bitmap [targetWidth] pixels wide, white background. */
    fun renderPage(file: File, index: Int, targetWidth: Int): Bitmap? = try {
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        try {
            if (index !in 0 until renderer.pageCount) {
                null
            } else {
                val page = renderer.openPage(index)
                try {
                    val w = targetWidth.coerceAtLeast(1)
                    val h = (w.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp
                } finally { page.close() }
            }
        } finally { renderer.close(); pfd.close() }
    } catch (_: Exception) { null }

    /**
     * Launch extra `--ez seed true` fills an empty store with two sample
     * documents, for screenshots on an emulator that has no camera.
     */
    fun seedSamplesIfEmpty(context: Context) {
        reload(context)
        if (_documents.value.isNotEmpty()) return
        runCatching { save(context, PdfEngine.makeSampleDocument(context), "Lease agreement") }
        runCatching {
            val id = IdCardPdf.make(context, IdCardPdf.sampleCards(), IdPageLayout.ONE_PAGE, PaperSize.A4)
            save(context, id, title("ID Card"))
        }
    }
}

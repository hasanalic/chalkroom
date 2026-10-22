package app.chalkroom.data.pdf

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/** What the system knows about a picked file. Either can be missing. */
data class PdfFileInfo(val name: String?, val size: Long?)

sealed interface PdfOpenResult {
    data class Opened(val pageCount: Int) : PdfOpenResult
    data object Locked : PdfOpenResult
    data object Unreadable : PdfOpenResult
}

/** Thrown when reading pages fails partway; cancellation is never turned into this. */
class PdfReadException(cause: Throwable) : IOException(cause)

/** Reads a picked PDF on the device. The file never leaves it. [uri] is the picked document's content URI. */
interface PdfReader {
    suspend fun info(uri: String): PdfFileInfo
    suspend fun open(uri: String): PdfOpenResult

    /** One page at a time, cancellable between pages; [onPage] gets how many pages are done. */
    suspend fun readPages(uri: String, pages: IntRange, onPage: (done: Int) -> Unit): List<PdfPage>
}

class PdfBoxReader(private val context: Context) : PdfReader {

    override suspend fun info(uri: String): PdfFileInfo = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.query(Uri.parse(uri), arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (!c.moveToFirst()) return@use null
                val name = c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 && !c.isNull(it) }?.let(c::getString)
                val size = c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }?.let(c::getLong)
                PdfFileInfo(name, size)
            }
        }.getOrNull() ?: PdfFileInfo(null, null)
    }

    override suspend fun open(uri: String): PdfOpenResult = withContext(Dispatchers.IO) {
        try {
            load(uri).use { PdfOpenResult.Opened(it.numberOfPages) }
        } catch (e: InvalidPasswordException) {
            PdfOpenResult.Locked
        } catch (e: IOException) {
            PdfOpenResult.Unreadable
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PdfOpenResult.Unreadable
        }
    }

    override suspend fun readPages(uri: String, pages: IntRange, onPage: (done: Int) -> Unit): List<PdfPage> = withContext(Dispatchers.IO) {
        try {
            load(uri).use { doc ->
                val stripper = PDFTextStripper().apply {
                    paragraphStart = ""
                    paragraphEnd = "\n"
                }
                pages.mapIndexed { i, n ->
                    ensureActive()
                    stripper.startPage = n
                    stripper.endPage = n
                    val text = stripper.getText(doc)
                    onPage(i + 1)
                    PdfPage(n, text)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw PdfReadException(e)
        } catch (e: OutOfMemoryError) {
            throw PdfReadException(e)
        }
    }

    /** Spills to a temp file, not to memory, so a big PDF doesn't take the app down. */
    private fun load(uri: String): PDDocument {
        val stream = context.contentResolver.openInputStream(Uri.parse(uri)) ?: throw IOException("No stream for the picked file")
        return stream.use { PDDocument.load(it, MemoryUsageSetting.setupTempFileOnly()) }
    }
}

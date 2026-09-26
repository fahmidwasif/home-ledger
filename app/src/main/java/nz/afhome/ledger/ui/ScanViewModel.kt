package nz.afhome.ledger.ui

import android.app.Application
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import nz.afhome.ledger.data.Category
import nz.afhome.ledger.data.Person
import nz.afhome.ledger.data.today
import nz.afhome.ledger.ledger
import nz.afhome.ledger.scan.Ocr
import nz.afhome.ledger.scan.ReceiptDraft
import nz.afhome.ledger.scan.ReceiptParser
import java.io.File

/** Holds the receipt being scanned/reviewed. Scoped to the activity so it survives moving between screens. */
class ScanViewModel(app: Application) : AndroidViewModel(app) {
    private val ledger = app.ledger

    private val _draft = MutableStateFlow<ReceiptDraft?>(null)
    val draft: StateFlow<ReceiptDraft?> = _draft
    private val _busy = MutableStateFlow<String?>(null)
    val busy: StateFlow<String?> = _busy
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    /** Category the parser suggested per item, so corrections can be learned on save. */
    private var suggested: Map<Long, Category> = emptyMap()
    private val rows = mutableListOf<String>()
    private var photoFile: File? = null
    private var photoUri: Uri? = null

    /** A private temp file for the camera. It is deleted as soon as the text has been read. */
    fun newPhotoUri(): Uri {
        val dir = File(getApplication<Application>().cacheDir, "scans").apply { mkdirs() }
        val f = File(dir, "receipt-${System.currentTimeMillis()}.jpg")
        photoFile = f
        return FileProvider.getUriForFile(getApplication(), "${getApplication<Application>().packageName}.files", f)
            .also { photoUri = it }
    }

    fun onCameraResult(success: Boolean) {
        val file = photoFile
        val uri = photoUri
        photoFile = null
        photoUri = null
        if (!success || uri == null) { file?.delete(); return }
        read(uri) { file?.delete() }
    }

    fun onGalleryPick(uri: Uri) = read(uri) {}

    private fun read(uri: Uri, cleanup: () -> Unit) {
        viewModelScope.launch {
            _busy.value = "Reading the receipt…"
            _error.value = null
            try {
                rows += Ocr.readRows(getApplication(), uri)
                val parsed = ReceiptParser.parse(rows, ledger.repo.categorizer())
                val previous = _draft.value
                // Keep answers already given if this is the 2nd photo of a long receipt.
                _draft.value = parsed.copy(
                    purchaser = previous?.purchaser,
                    account = parsed.account ?: previous?.account,
                )
                suggested = parsed.items.associate { it.key to it.category }
            } catch (t: Throwable) {
                _error.value = "Couldn't read that photo: ${t.message}"
            } finally {
                cleanup() // the image is never kept
                _busy.value = null
            }
        }
    }

    fun startManual() {
        rows.clear()
        suggested = emptyMap()
        _draft.value = ReceiptDraft(manual = true, date = today())
    }

    fun startScan() {
        rows.clear()
        suggested = emptyMap()
        _draft.value = null
        _error.value = null
    }

    fun update(f: (ReceiptDraft) -> ReceiptDraft) { _draft.value = _draft.value?.let(f) }

    fun setPurchaser(p: Person) = update { d ->
        d.copy(purchaser = p, account = d.account ?: ledger.prefs.lastAccount(p))
    }

    fun refineWithAi() {
        val d = _draft.value ?: return
        viewModelScope.launch {
            _busy.value = "Asking the on-device AI to re-read the receipt… (can take ~30s)"
            try {
                val refined = ledger.assistant.refineReceipt(d, ledger.repo.categorizer())
                _draft.value = refined
                suggested = suggested + refined.items.filter { it.key !in suggested }.associate { it.key to it.category }
            } catch (t: Throwable) {
                _error.value = "AI couldn't help: ${t.message}"
            } finally {
                _busy.value = null
            }
        }
    }

    fun save(vehicleId: Long?, done: () -> Unit) {
        val d = _draft.value ?: return
        viewModelScope.launch {
            ledger.repo.saveReceipt(d, suggested, vehicleId)
            _draft.value = null
            rows.clear()
            done()
        }
    }

    fun clearError() { _error.value = null }
}

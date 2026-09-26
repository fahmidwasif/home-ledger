package nz.afhome.ledger.scan

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device OCR with ML Kit's bundled Latin model (the model ships inside the APK; nothing is downloaded).
 * Receipts are two-column (name … price) and ML Kit often returns the columns as separate blocks,
 * so lines are re-assembled into visual rows by their vertical position.
 */
object Ocr {
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun readRows(context: Context, uri: Uri): List<String> {
        val image = InputImage.fromFilePath(context, uri)
        val text = suspendCancellableCoroutine<Text> { cont ->
            recognizer.process(image)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
        return toRows(text)
    }

    private data class Piece(val text: String, val top: Int, val bottom: Int, val left: Int) {
        val center get() = (top + bottom) / 2
        val height get() = (bottom - top).coerceAtLeast(1)
    }

    private fun toRows(text: Text): List<String> {
        val pieces = text.textBlocks.flatMap { it.lines }.mapNotNull { line ->
            val b = line.boundingBox ?: return@mapNotNull null
            Piece(line.text, b.top, b.bottom, b.left)
        }.sortedBy { it.center }
        if (pieces.isEmpty()) return text.text.lines()

        val rows = mutableListOf<MutableList<Piece>>()
        for (p in pieces) {
            val row = rows.lastOrNull()
            // Same visual row if the vertical centres are within ~60% of the line height.
            if (row != null && kotlin.math.abs(row.map { it.center }.average() - p.center) < 0.6 * row.first().height) {
                row += p
            } else {
                rows += mutableListOf(p)
            }
        }
        return rows.map { r -> r.sortedBy { it.left }.joinToString("  ") { it.text } }
    }
}

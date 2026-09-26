package nz.afhome.ledger.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import nz.afhome.ledger.data.Category
import nz.afhome.ledger.data.PayAccount
import nz.afhome.ledger.data.Person
import nz.afhome.ledger.data.Prefs
import nz.afhome.ledger.data.Repository
import nz.afhome.ledger.data.Snapshot
import nz.afhome.ledger.data.fmtDate
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Encrypted backup to a file the user picks with the system file picker — normally in Google Drive.
 * The Drive app does the uploading, so this app never needs internet access.
 *
 * File layout: "AFHL" ‖ version(1) ‖ salt(16) ‖ iv(12) ‖ AES-GCM(gzip(json)).
 */
class BackupManager(private val context: Context, private val repo: Repository, private val prefs: Prefs) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val magic = "AFHL".toByteArray()
    private val version: Byte = 1

    val hasPin: Boolean get() = prefs.wrappedKey != null && prefs.backupSalt != null
    val hasTarget: Boolean get() = prefs.backupUri != null

    fun setPin(pin: String) {
        require(pin.length >= 6) { "Use at least 6 digits." }
        val salt = Crypto.randomBytes(16)
        prefs.backupSalt = Crypto.b64(salt)
        prefs.wrappedKey = Crypto.wrap(Crypto.deriveKey(pin, salt))
    }

    /** Remember the Drive file chosen with ACTION_CREATE_DOCUMENT / ACTION_OPEN_DOCUMENT. */
    fun setTarget(uri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        prefs.backupUri = uri.toString()
    }

    suspend fun backupNow(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val uri = Uri.parse(prefs.backupUri ?: error("Choose a backup file in Google Drive first."))
            val key = Crypto.unwrap(prefs.wrappedKey ?: error("Set a backup PIN first."))
            val salt = Crypto.unb64(prefs.backupSalt!!)
            val snapshot = repo.snapshot().copy(settings = prefs.exportable())
            val plain = gzip(json.encodeToString(Snapshot.serializer(), snapshot).toByteArray())
            val header = magic + byteArrayOf(version) + salt
            val body = header + Crypto.encrypt(key, plain, aad = header)
            context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(body) }
            prefs.lastBackupAt = System.currentTimeMillis()
            prefs.lastBackupError = null
            prefs.dirty = false
        }.onFailure { prefs.lastBackupError = it.message ?: it.javaClass.simpleName }
    }

    /** Reads a backup from Drive, decrypts it with [pin], and replaces everything on this phone. */
    suspend fun restore(uri: Uri, pin: String): Result<Snapshot> = withContext(Dispatchers.IO) {
        runCatching {
            val data = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            require(data.size > 33 && data.copyOfRange(0, 4).contentEquals(magic)) { "This isn't an A&F Home backup file." }
            val header = data.copyOfRange(0, 21)
            val salt = data.copyOfRange(5, 21)
            val key = Crypto.deriveKey(pin, salt)
            val plain = try {
                Crypto.decrypt(key, data.copyOfRange(21, data.size), aad = header)
            } catch (e: javax.crypto.AEADBadTagException) {
                error("Wrong PIN (or the file is damaged).")
            }
            val snapshot = json.decodeFromString(Snapshot.serializer(), String(gunzip(plain)))
            repo.restore(snapshot)
            prefs.import(snapshot.settings)
            // Keep backing up to the same file with the same PIN from this phone.
            prefs.backupSalt = Crypto.b64(salt)
            prefs.wrappedKey = Crypto.wrap(key)
            runCatching { setTarget(uri) }
            prefs.lastBackupAt = System.currentTimeMillis()
            snapshot
        }
    }

    /** Plain CSV of every purchased item (not encrypted — for spreadsheets). */
    suspend fun exportCsv(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val receipts = repo.dao.allReceipts().associateBy { it.id }
            val items = repo.dao.allItems()
            fun q(s: String?) = "\"" + (s ?: "").replace("\"", "\"\"") + "\""
            val sb = StringBuilder("date,store,purchaser,paid_from,item,qty,unit_price,total,category,gift,gift_for,occasion\n")
            items.sortedBy { receipts[it.receiptId]?.date }.forEach { li ->
                val r = receipts[li.receiptId] ?: return@forEach
                sb.append(listOf(
                    q(r.date.fmtDate()), q(r.store), q(Person.of(r.purchaser).label), q(PayAccount.of(r.payment)?.label), q(li.name), li.qty, li.unitPrice, li.total,
                    q(Category.of(li.category).label), li.isGift, q(li.giftFor), q(li.giftOccasion),
                ).joinToString(",")).append('\n')
            }
            context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(sb.toString().toByteArray()) }
            items.size
        }
    }

    private fun gzip(b: ByteArray): ByteArray = ByteArrayOutputStream().also { o -> GZIPOutputStream(o).use { it.write(b) } }.toByteArray()
    private fun gunzip(b: ByteArray): ByteArray = GZIPInputStream(ByteArrayInputStream(b)).use { it.readBytes() }
}

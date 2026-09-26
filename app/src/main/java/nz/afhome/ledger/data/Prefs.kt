package nz.afhome.ledger.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Small settings that don't belong in the database. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("ledger", Context.MODE_PRIVATE)
    private val _changes = MutableStateFlow(0)
    /** Bumped on every write so Compose screens can recompose. */
    val changes: StateFlow<Int> = _changes

    private fun bump() { _changes.value++ }

    var backupUri: String?
        get() = sp.getString("backupUri", null)
        set(v) { sp.edit { putString("backupUri", v) }; bump() }

    var lastBackupAt: Long
        get() = sp.getLong("lastBackupAt", 0)
        set(v) { sp.edit { putLong("lastBackupAt", v) }; bump() }

    var lastBackupError: String?
        get() = sp.getString("lastBackupError", null)
        set(v) { sp.edit { putString("lastBackupError", v) }; bump() }

    var autoBackup: Boolean
        get() = sp.getBoolean("autoBackup", true)
        set(v) { sp.edit { putBoolean("autoBackup", v) }; bump() }

    /** Backup key derived from the PIN, wrapped by an Android Keystore key. Base64. */
    var wrappedKey: String?
        get() = sp.getString("wrappedKey", null)
        set(v) { sp.edit { putString("wrappedKey", v) }; bump() }

    var backupSalt: String?
        get() = sp.getString("backupSalt", null)
        set(v) { sp.edit { putString("backupSalt", v) }; bump() }

    var dirty: Boolean
        get() = sp.getBoolean("dirty", false)
        set(v) { sp.edit { putBoolean("dirty", v) } }

    var modelPath: String?
        get() = sp.getString("modelPath", null)
        set(v) { sp.edit { putString("modelPath", v) }; bump() }

    var useGpu: Boolean
        get() = sp.getBoolean("useGpu", false)
        set(v) { sp.edit { putBoolean("useGpu", v) }; bump() }

    var defaultPerson: String
        get() = sp.getString("defaultPerson", Person.FAHMID.name)!!
        set(v) { sp.edit { putString("defaultPerson", v) }; bump() }

    /** Typical cost of buying lunch near work in Auckland (CBD cafés/food courts run ~$15–25). */
    var lunchBoughtCost: Double
        get() = sp.getFloat("lunchBoughtCost", 20f).toDouble()
        set(v) { sp.edit { putFloat("lunchBoughtCost", v.toFloat()) }; bump() }

    /** Rough cost of ingredients for one packed lunch from home. */
    var lunchPackedCost: Double
        get() = sp.getFloat("lunchPackedCost", 5f).toDouble()
        set(v) { sp.edit { putFloat("lunchPackedCost", v.toFloat()) }; bump() }

    /** Weekly grocery target. Default is the middle of the 2026 Auckland couple range ($160–220). */
    var weeklyGroceryTarget: Double
        get() = sp.getFloat("weeklyGroceryTarget", 190f).toDouble()
        set(v) { sp.edit { putFloat("weeklyGroceryTarget", v.toFloat()) }; bump() }

    /** The account each person used last, offered as the default next time. */
    fun lastAccount(person: Person): PayAccount? = PayAccount.of(sp.getString("lastAccount_${person.name}", null))
    fun setLastAccount(person: Person, account: PayAccount) { sp.edit { putString("lastAccount_${person.name}", account.name) } }

    var askedNotifications: Boolean
        get() = sp.getBoolean("askedNotifications", false)
        set(v) { sp.edit { putBoolean("askedNotifications", v) } }

    /** Settings worth carrying to a new phone inside the backup. */
    fun exportable(): Map<String, String> = mapOf(
        "defaultPerson" to defaultPerson,
        "lunchBoughtCost" to lunchBoughtCost.toString(),
        "lunchPackedCost" to lunchPackedCost.toString(),
        "weeklyGroceryTarget" to weeklyGroceryTarget.toString(),
    )

    fun import(map: Map<String, String>) {
        map["defaultPerson"]?.let { defaultPerson = it }
        map["lunchBoughtCost"]?.toDoubleOrNull()?.let { lunchBoughtCost = it }
        map["lunchPackedCost"]?.toDoubleOrNull()?.let { lunchPackedCost = it }
        map["weeklyGroceryTarget"]?.toDoubleOrNull()?.let { weeklyGroceryTarget = it }
    }
}

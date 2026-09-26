package nz.afhome.ledger.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

// Dates are stored as epoch days (LocalDate.toEpochDay) so they sort, group and serialise simply.

@Serializable
@Entity(tableName = "receipts", indices = [Index("date")])
data class Receipt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val store: String,
    val date: Long,
    val total: Double,
    val gst: Double? = null,
    val purchaser: String = Person.BOTH.name,
    /** [PayAccount] name: which account paid. */
    val payment: String? = null,
    val notes: String? = null,
    /** OCR text is kept (the photo is not) so the receipt can be re-read later if needed. */
    val rawText: String? = null,
    val manual: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(
    tableName = "line_items",
    foreignKeys = [ForeignKey(entity = Receipt::class, parentColumns = ["id"], childColumns = ["receiptId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("receiptId"), Index("normName"), Index("category")],
)
data class LineItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val receiptId: Long,
    val name: String,
    val normName: String,
    val qty: Double = 1.0,
    val unit: String? = null,
    val unitPrice: Double,
    val total: Double,
    val category: String,
    val isGift: Boolean = false,
    val giftFor: String? = null,
    val giftOccasion: String? = null,
)

@Serializable
@Entity(tableName = "inventory", indices = [Index("normName", unique = true)])
data class InventoryItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val normName: String,
    val category: String,
    val room: String,
    /** Free text: "top shelf", "blue box under the sink"... */
    val spot: String? = null,
    val quantity: Double,
    val unit: String? = null,
    val lowThreshold: Double = 0.0,
    val expiry: Long? = null,
    val lastPurchased: Long? = null,
    val lastPrice: Double? = null,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(tableName = "usage_events", indices = [Index("normName"), Index("date")])
data class UsageEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val inventoryId: Long?,
    val itemName: String,
    val normName: String,
    val qty: Double,
    val date: Long,
    val purpose: String,
    val person: String,
    val value: Double? = null,
)

@Serializable
@Entity(tableName = "shopping_list")
data class ShoppingItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val normName: String,
    val qty: Double = 1.0,
    val category: String,
    val source: String = ShoppingSource.MANUAL.name,
    val estPrice: Double? = null,
    val bestStore: String? = null,
    val done: Boolean = false,
    val addedAt: Long = System.currentTimeMillis(),
)

/** One packed lunch taken to work. */
@Serializable
@Entity(tableName = "lunch_log", indices = [Index("date")])
data class LunchLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val person: String,
    val meal: String? = null,
    val packedCost: Double,
    /** What buying lunch near work would have cost instead. */
    val boughtCost: Double,
)

@Serializable
@Entity(tableName = "vehicles")
data class Vehicle(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val plate: String? = null,
    val fuelType: String = "Petrol 91",
    val wofExpiry: Long? = null,
    val regoExpiry: Long? = null,
    val insuranceRenewal: Long? = null,
    val insuranceAnnual: Double? = null,
    val nextServiceKm: Int? = null,
    val odometer: Int? = null,
    /** Fuel tank size; a 2008 Civic 1.8 holds 50 L. */
    @ColumnInfo(defaultValue = "50") val tankLitres: Double = 50.0,
    /** How many bars the dashboard fuel gauge shows when full. */
    @ColumnInfo(defaultValue = "8") val gaugeBars: Int = 8,
    /** Bars showing at the last reading, and when that was (epoch day). */
    val fuelBars: Int? = null,
    val fuelBarsDay: Long? = null,
)

@Serializable
@Entity(tableName = "fuel_log", indices = [Index("date")])
data class FuelLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long?,
    val date: Long,
    val litres: Double,
    val pricePerL: Double,
    val total: Double,
    val odometer: Int? = null,
    val station: String? = null,
    val receiptId: Long? = null,
    val person: String = Person.BOTH.name,
)

/** Categories the user corrected by hand; consulted before the keyword rules. */
@Serializable
@Entity(tableName = "category_rules")
data class CategoryRule(
    @PrimaryKey val normName: String,
    val category: String,
)

@Serializable
@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey val category: String,
    val monthly: Double,
)

/** A recurring payment (Netflix, gym, phone plan, insurance...). Logged as spending automatically when due. */
@Serializable
@Entity(tableName = "subscriptions")
data class Subscription(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amount: Double,
    val category: String = Category.SUBSCRIPTIONS.name,
    val frequency: String = Frequency.MONTHLY.name,
    /** Epoch day of the next payment. */
    val nextDue: Long,
    val purchaser: String = Person.BOTH.name,
    val payment: String? = null,
    /** Record a purchase automatically on each due date. */
    val autoLog: Boolean = true,
    val active: Boolean = true,
    /** Days before the due date to send a reminder (0 = no reminder). */
    val remindDays: Int = 3,
    val notes: String? = null,
)

/** Everything in the database, used for backup and restore. */
@Serializable
data class Snapshot(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val receipts: List<Receipt>,
    val lineItems: List<LineItem>,
    val inventory: List<InventoryItem>,
    val usage: List<UsageEvent>,
    val shopping: List<ShoppingItem>,
    val lunches: List<LunchLog>,
    val vehicles: List<Vehicle>,
    val fuel: List<FuelLog>,
    val rules: List<CategoryRule>,
    val budgets: List<Budget>,
    /** Absent in backups made before subscriptions existed. */
    val subscriptions: List<Subscription> = emptyList(),
    val settings: Map<String, String> = emptyMap(),
)

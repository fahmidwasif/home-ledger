package nz.afhome.ledger.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class ReceiptWithCount(
    val id: Long,
    val store: String,
    val date: Long,
    val total: Double,
    val purchaser: String,
    val itemCount: Int,
)

@Dao
interface LedgerDao {
    // ---- receipts ----
    @Insert suspend fun insertReceipt(r: Receipt): Long
    @Update suspend fun updateReceipt(r: Receipt)
    @Delete suspend fun deleteReceipt(r: Receipt)
    @Insert suspend fun insertItems(items: List<LineItem>)
    @Query("DELETE FROM line_items WHERE receiptId = :receiptId") suspend fun deleteItemsOf(receiptId: Long)

    @Query(
        """SELECT r.id, r.store, r.date, r.total, r.purchaser, COUNT(li.id) AS itemCount
           FROM receipts r LEFT JOIN line_items li ON li.receiptId = r.id
           GROUP BY r.id ORDER BY r.date DESC, r.id DESC"""
    )
    fun receiptsFlow(): Flow<List<ReceiptWithCount>>

    @Query("SELECT * FROM receipts ORDER BY date DESC") fun allReceiptsFlow(): Flow<List<Receipt>>
    @Query("SELECT * FROM receipts WHERE id = :id") suspend fun receipt(id: Long): Receipt?
    @Query("SELECT * FROM line_items WHERE receiptId = :id") suspend fun itemsOf(id: Long): List<LineItem>
    @Query("SELECT * FROM line_items") fun allItemsFlow(): Flow<List<LineItem>>
    @Query("SELECT * FROM receipts") suspend fun allReceipts(): List<Receipt>
    @Query("SELECT * FROM line_items") suspend fun allItems(): List<LineItem>
    @Query(
        """SELECT li.* FROM line_items li JOIN receipts r ON r.id = li.receiptId
           WHERE li.normName = :norm ORDER BY r.date"""
    )
    suspend fun historyOf(norm: String): List<LineItem>

    // ---- inventory ----
    @Query("SELECT * FROM inventory ORDER BY room, name") fun inventoryFlow(): Flow<List<InventoryItem>>
    @Query("SELECT * FROM inventory") suspend fun inventory(): List<InventoryItem>
    @Query("SELECT * FROM inventory WHERE normName = :norm") suspend fun inventoryByNorm(norm: String): InventoryItem?
    @Query("SELECT * FROM inventory WHERE id = :id") suspend fun inventoryById(id: Long): InventoryItem?
    @Upsert suspend fun upsertInventory(i: InventoryItem): Long
    @Delete suspend fun deleteInventory(i: InventoryItem)

    @Insert suspend fun insertUsage(u: UsageEvent)
    @Query("SELECT * FROM usage_events ORDER BY date DESC") fun usageFlow(): Flow<List<UsageEvent>>
    @Query("SELECT * FROM usage_events") suspend fun usage(): List<UsageEvent>

    // ---- shopping list ----
    @Query("SELECT * FROM shopping_list ORDER BY done, category, name") fun shoppingFlow(): Flow<List<ShoppingItem>>
    @Query("SELECT * FROM shopping_list") suspend fun shopping(): List<ShoppingItem>
    @Insert suspend fun insertShopping(s: ShoppingItem)
    @Update suspend fun updateShopping(s: ShoppingItem)
    @Delete suspend fun deleteShopping(s: ShoppingItem)
    @Query("DELETE FROM shopping_list WHERE done = 1") suspend fun clearDoneShopping()
    @Query("DELETE FROM shopping_list WHERE normName = :norm AND done = 0") suspend fun removeOpenShopping(norm: String)

    // ---- lunches ----
    @Insert suspend fun insertLunch(l: LunchLog)
    @Delete suspend fun deleteLunch(l: LunchLog)
    @Query("SELECT * FROM lunch_log ORDER BY date DESC, id DESC") fun lunchFlow(): Flow<List<LunchLog>>
    @Query("SELECT * FROM lunch_log") suspend fun lunches(): List<LunchLog>

    // ---- car ----
    @Query("SELECT * FROM vehicles") fun vehiclesFlow(): Flow<List<Vehicle>>
    @Query("SELECT * FROM vehicles") suspend fun vehicles(): List<Vehicle>
    @Upsert suspend fun upsertVehicle(v: Vehicle): Long
    @Delete suspend fun deleteVehicle(v: Vehicle)
    @Insert suspend fun insertFuel(f: FuelLog)
    @Delete suspend fun deleteFuel(f: FuelLog)
    @Query("SELECT * FROM fuel_log ORDER BY date DESC, id DESC") fun fuelFlow(): Flow<List<FuelLog>>
    @Query("SELECT * FROM fuel_log") suspend fun fuel(): List<FuelLog>

    // ---- rules & budgets ----
    @Upsert suspend fun upsertRule(r: CategoryRule)
    @Query("SELECT * FROM category_rules") suspend fun rules(): List<CategoryRule>
    @Upsert suspend fun upsertBudget(b: Budget)
    @Query("DELETE FROM budgets WHERE category = :category") suspend fun deleteBudget(category: String)
    @Query("SELECT * FROM budgets") fun budgetsFlow(): Flow<List<Budget>>
    @Query("SELECT * FROM budgets") suspend fun budgets(): List<Budget>

    // ---- restore ----
    @Query("DELETE FROM line_items") suspend fun wipeItems()
    @Query("DELETE FROM receipts") suspend fun wipeReceipts()
    @Query("DELETE FROM inventory") suspend fun wipeInventory()
    @Query("DELETE FROM usage_events") suspend fun wipeUsage()
    @Query("DELETE FROM shopping_list") suspend fun wipeShopping()
    @Query("DELETE FROM lunch_log") suspend fun wipeLunch()
    @Query("DELETE FROM fuel_log") suspend fun wipeFuel()
    @Query("DELETE FROM vehicles") suspend fun wipeVehicles()
    @Query("DELETE FROM category_rules") suspend fun wipeRules()
    @Query("DELETE FROM budgets") suspend fun wipeBudgets()

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putReceipts(x: List<Receipt>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putItems(x: List<LineItem>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putInventory(x: List<InventoryItem>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putUsage(x: List<UsageEvent>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putShopping(x: List<ShoppingItem>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putLunches(x: List<LunchLog>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putVehicles(x: List<Vehicle>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putFuel(x: List<FuelLog>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putRules(x: List<CategoryRule>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putBudgets(x: List<Budget>)

    @Transaction
    suspend fun replaceAll(s: Snapshot) {
        wipeItems(); wipeReceipts(); wipeInventory(); wipeUsage(); wipeShopping()
        wipeLunch(); wipeFuel(); wipeVehicles(); wipeRules(); wipeBudgets()
        putReceipts(s.receipts); putItems(s.lineItems); putInventory(s.inventory); putUsage(s.usage)
        putShopping(s.shopping); putLunches(s.lunches); putVehicles(s.vehicles); putFuel(s.fuel)
        putRules(s.rules); putBudgets(s.budgets)
    }
}

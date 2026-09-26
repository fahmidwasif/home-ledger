package nz.afhome.ledger.data

import androidx.room.withTransaction
import nz.afhome.ledger.analysis.Predictor
import nz.afhome.ledger.scan.Categorizer
import nz.afhome.ledger.scan.ReceiptDraft

class Repository(private val db: AppDatabase, private val prefs: Prefs) {
    val dao = db.dao()

    private fun touched() { prefs.dirty = true }

    suspend fun categorizer(): Categorizer =
        Categorizer(dao.rules().associate { it.normName to Category.of(it.category) })

    /**
     * Saves a reviewed receipt: the receipt + its lines, learns category corrections, stocks the
     * inventory, ticks matching shopping-list entries, and logs fuel for car receipts.
     */
    suspend fun saveReceipt(d: ReceiptDraft, originalCategories: Map<Long, Category>, vehicleId: Long?): Long = db.withTransaction {
        val date = d.date ?: today()
        val purchaser = (d.purchaser ?: Person.BOTH).name
        d.account?.let { prefs.setLastAccount(d.purchaser ?: Person.BOTH, it) }
        val receiptId = dao.insertReceipt(
            Receipt(
                store = d.store.trim().ifEmpty { "Unknown shop" }, date = date, total = d.total ?: d.itemsSum,
                gst = d.gst, purchaser = purchaser, payment = d.account?.name, notes = d.notes.ifBlank { null },
                rawText = d.rawText, manual = d.manual,
            )
        )
        dao.insertItems(d.items.map { i ->
            LineItem(
                receiptId = receiptId, name = i.name.trim(), normName = normalizeName(i.name), qty = i.qty, unit = i.unit,
                unitPrice = i.unitPrice, total = i.total, category = i.category.name, isGift = i.isGift,
                giftFor = i.giftFor.ifBlank { null }, giftOccasion = i.giftOccasion.ifBlank { null },
            )
        })

        for (i in d.items) {
            val norm = normalizeName(i.name)
            // Learn from corrections so the same product is categorised right next time.
            if (originalCategories[i.key] != null && originalCategories[i.key] != i.category) {
                dao.upsertRule(CategoryRule(norm, i.category.name))
            }
            if (i.toInventory && !i.isGift) {
                val existing = dao.inventoryByNorm(norm)
                val addQty = if (i.unit == "kg" || i.unit == "g" || i.unit == "l") 1.0 else i.qty
                dao.upsertInventory(
                    existing?.copy(
                        quantity = existing.quantity.coerceAtLeast(0.0) + addQty, lastPurchased = date,
                        lastPrice = i.unitPrice, updatedAt = System.currentTimeMillis(),
                    ) ?: InventoryItem(
                        name = i.name.trim(), normName = norm, category = i.category.name, room = i.room.name,
                        quantity = addQty, lastPurchased = date, lastPrice = i.unitPrice,
                    )
                )
            }
            dao.removeOpenShopping(norm)
        }

        val litres = d.fuelLitres
        if (litres != null && litres > 0) {
            val fuelTotal = d.items.filter { it.category == Category.FUEL }.sumOf { it.total }.takeIf { it > 0 } ?: (d.total ?: 0.0)
            dao.insertFuel(
                FuelLog(
                    vehicleId = vehicleId, date = date, litres = litres,
                    pricePerL = d.fuelPricePerL?.takeIf { it > 0 } ?: (fuelTotal / litres),
                    total = fuelTotal, odometer = d.odometer, station = d.store, receiptId = receiptId, person = purchaser,
                )
            )
        }
        touched()
        receiptId
    }

    suspend fun deleteReceipt(id: Long) {
        dao.receipt(id)?.let { dao.deleteReceipt(it) }
        touched()
    }

    // ---------- inventory ----------

    suspend fun saveInventory(i: InventoryItem) {
        dao.upsertInventory(i.copy(normName = normalizeName(i.name), updatedAt = System.currentTimeMillis()))
        touched()
    }

    suspend fun deleteInventory(i: InventoryItem) { dao.deleteInventory(i); touched() }

    /** Takes [qty] out of stock. When it runs low it is put on the shopping list automatically. */
    suspend fun useItem(item: InventoryItem, qty: Double, purpose: UsePurpose, person: Person) {
        val left = (item.quantity - qty).coerceAtLeast(0.0)
        dao.upsertInventory(item.copy(quantity = left, updatedAt = System.currentTimeMillis()))
        dao.insertUsage(
            UsageEvent(
                inventoryId = item.id, itemName = item.name, normName = item.normName, qty = qty, date = today(),
                purpose = purpose.name, person = person.name, value = item.lastPrice?.let { it * qty },
            )
        )
        if (purpose == UsePurpose.LUNCHBOX) {
            // Using food for a packed lunch counts as a lunch from home.
            val alreadyToday = dao.lunches().any { it.date == today() && it.person == person.name }
            if (!alreadyToday) logLunch(person, item.name)
        }
        if (left <= item.lowThreshold) {
            addShopping(item.name, Category.of(item.category), if (left <= 0.0) ShoppingSource.USED_UP else ShoppingSource.LOW_STOCK)
        }
        touched()
    }

    // ---------- shopping ----------

    suspend fun addShopping(name: String, category: Category, source: ShoppingSource = ShoppingSource.MANUAL, qty: Double = 1.0) {
        val norm = normalizeName(name)
        if (dao.shopping().any { it.normName == norm && !it.done }) return
        val history = dao.historyOf(norm)
        val receipts = history.mapNotNull { dao.receipt(it.receiptId)?.let { r -> r to it } }
        val cheapest = receipts.minByOrNull { it.second.unitPrice }
        dao.insertShopping(
            ShoppingItem(
                name = name.trim(), normName = norm, qty = qty, category = category.name, source = source.name,
                estPrice = history.lastOrNull()?.unitPrice, bestStore = cheapest?.first?.store,
            )
        )
        touched()
    }

    suspend fun toggleShopping(s: ShoppingItem) { dao.updateShopping(s.copy(done = !s.done)); touched() }
    suspend fun deleteShopping(s: ShoppingItem) { dao.deleteShopping(s); touched() }
    suspend fun clearDoneShopping() { dao.clearDoneShopping(); touched() }

    /** Adds items that are "usually due" by now based on how often they are bought. */
    suspend fun refreshPredictions(): Int {
        val receipts = dao.allReceipts().associateBy { it.id }
        val suggestions = Predictor.dueItems(dao.allItems(), receipts, dao.inventory(), today())
        var added = 0
        val open = dao.shopping().filter { !it.done }.map { it.normName }.toSet()
        for (s in suggestions) if (s.normName !in open) {
            dao.insertShopping(
                ShoppingItem(
                    name = s.name, normName = s.normName, category = s.category.name, source = ShoppingSource.PREDICTED.name,
                    estPrice = s.lastPrice, bestStore = s.cheapestStore,
                )
            )
            added++
        }
        if (added > 0) touched()
        return added
    }

    // ---------- lunches ----------

    suspend fun logLunch(person: Person, meal: String?) {
        dao.insertLunch(LunchLog(date = today(), person = person.name, meal = meal, packedCost = prefs.lunchPackedCost, boughtCost = prefs.lunchBoughtCost))
        touched()
    }

    suspend fun deleteLunch(l: LunchLog) { dao.deleteLunch(l); touched() }

    // ---------- car ----------

    suspend fun saveVehicle(v: Vehicle): Long = dao.upsertVehicle(v).also { touched() }
    suspend fun addFuel(f: FuelLog) { dao.insertFuel(f); touched() }
    suspend fun deleteFuel(f: FuelLog) { dao.deleteFuel(f); touched() }

    suspend fun setBudget(category: Category, monthly: Double?) {
        if (monthly == null || monthly <= 0) dao.deleteBudget(category.name) else dao.upsertBudget(Budget(category.name, monthly))
        touched()
    }

    // ---------- backup ----------

    suspend fun snapshot() = Snapshot(
        receipts = dao.allReceipts(), lineItems = dao.allItems(), inventory = dao.inventory(), usage = dao.usage(),
        shopping = dao.shopping(), lunches = dao.lunches(), vehicles = dao.vehicles(), fuel = dao.fuel(),
        rules = dao.rules(), budgets = dao.budgets(),
    )

    suspend fun restore(s: Snapshot) { dao.replaceAll(s); prefs.dirty = false }
}

package nz.afhome.ledger.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        Receipt::class, LineItem::class, InventoryItem::class, UsageEvent::class, ShoppingItem::class,
        LunchLog::class, Vehicle::class, FuelLog::class, CategoryRule::class, Budget::class, Subscription::class,
    ],
    version = 3,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): LedgerDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "home-ledger.db").build()
    }
}

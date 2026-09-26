package nz.afhome.ledger

import android.app.Application
import android.content.Context
import nz.afhome.ledger.ai.Assistant
import nz.afhome.ledger.ai.LocalLlm
import nz.afhome.ledger.backup.BackupManager
import nz.afhome.ledger.data.AppDatabase
import nz.afhome.ledger.data.Prefs
import nz.afhome.ledger.data.Repository
import nz.afhome.ledger.work.Jobs

class LedgerApp : Application() {
    val prefs by lazy { Prefs(this) }
    val db by lazy { AppDatabase.build(this) }
    val repo by lazy { Repository(db, prefs) }
    val llm by lazy { LocalLlm(this, prefs) }
    val assistant by lazy { Assistant(repo, prefs, llm) }
    val backup by lazy { BackupManager(this, repo, prefs) }

    override fun onCreate() {
        super.onCreate()
        Jobs.schedule(this)
    }
}

val Context.ledger: LedgerApp get() = applicationContext as LedgerApp

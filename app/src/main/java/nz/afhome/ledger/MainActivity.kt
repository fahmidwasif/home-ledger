package nz.afhome.ledger

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import nz.afhome.ledger.ui.AppNav
import nz.afhome.ledger.ui.LedgerTheme
import nz.afhome.ledger.work.Jobs

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Ask once, for WoF / rego / expiry reminders.
        if (!ledger.prefs.askedNotifications) {
            ledger.prefs.askedNotifications = true
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        // Log any subscription payments that fell due while the app was closed.
        lifecycleScope.launch { ledger.repo.processSubscriptions() }
        setContent { LedgerTheme { AppNav() } }
    }

    override fun onStop() {
        super.onStop()
        // Push recent changes to the Drive backup shortly after leaving the app.
        if (ledger.prefs.dirty) Jobs.backupSoon(this)
    }
}

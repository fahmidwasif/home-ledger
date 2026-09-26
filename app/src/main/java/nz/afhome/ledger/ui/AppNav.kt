package nz.afhome.ledger.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import nz.afhome.ledger.ui.screens.AskScreen
import nz.afhome.ledger.ui.screens.CarScreen
import nz.afhome.ledger.ui.screens.HomeScreen
import nz.afhome.ledger.ui.screens.InsightsScreen
import nz.afhome.ledger.ui.screens.InventoryScreen
import nz.afhome.ledger.ui.screens.LunchScreen
import nz.afhome.ledger.ui.screens.ReceiptDetailScreen
import nz.afhome.ledger.ui.screens.ReceiptsScreen
import nz.afhome.ledger.ui.screens.ScanScreen
import nz.afhome.ledger.ui.screens.SettingsScreen
import nz.afhome.ledger.ui.screens.ShoppingScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "Home", Magic.Castle),
    Tab("stock", "Stock", Magic.Trunk),
    Tab("list", "List", Magic.Scroll),
    Tab("insights", "Insights", Magic.Galleons),
    Tab("ask", "Owl", Magic.Owl),
)

private val titles = mapOf(
    "home" to "A&F Home", "stock" to "Home stock", "list" to "Shopping list", "insights" to "Insights", "ask" to "Ask",
    "scan" to "Scan receipt", "receipts" to "Receipts", "receipt/{id}" to "Receipt", "car" to "Car", "lunch" to "Packed lunches",
    "settings" to "Settings",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNav() {
    val nav = rememberNavController()
    val scanVm: ScanViewModel = viewModel()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "home"
    val isTab = tabs.any { it.route == route }

    fun go(r: String) = nav.navigate(r) { launchSingleTop = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titles[route] ?: "") },
                navigationIcon = { if (!isTab) IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { if (isTab) IconButton(onClick = { go("settings") }) { Icon(Magic.Hat, "Settings") } },
            )
        },
        bottomBar = {
            if (isTab) NavigationBar {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = route == t.route,
                        onClick = {
                            nav.navigate(t.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(t.icon, t.label) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(pad)) {
            composable("home") {
                HomeScreen(
                    onScan = { scanVm.startScan(); go("scan") },
                    onManual = { scanVm.startManual(); go("scan") },
                    onReceipts = { go("receipts") },
                    onReceipt = { go("receipt/$it") },
                    onCar = { go("car") },
                    onLunch = { go("lunch") },
                    onSettings = { go("settings") },
                )
            }
            composable("stock") { InventoryScreen() }
            composable("list") { ShoppingScreen() }
            composable("insights") { InsightsScreen() }
            composable("ask") { AskScreen() }
            composable("scan") {
                ScanScreen(scanVm) {
                    nav.popBackStack()
                    scope.launch { snackbar.showSnackbar("Mischief managed ✨ Purchase saved.") }
                }
            }
            composable("receipts") { ReceiptsScreen { go("receipt/$it") } }
            composable("receipt/{id}") { e ->
                ReceiptDetailScreen(e.arguments?.getString("id")?.toLongOrNull() ?: 0L) { nav.popBackStack() }
            }
            composable("car") { CarScreen() }
            composable("lunch") { LunchScreen() }
            composable("settings") { SettingsScreen() }
        }
    }
}

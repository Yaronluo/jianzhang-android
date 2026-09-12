package com.jianzhang.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.jianzhang.app.ui.FinanceViewModel
import com.jianzhang.app.ui.screens.AddTransactionScreen
import com.jianzhang.app.ui.screens.AssetsScreen
import com.jianzhang.app.ui.screens.LedgerScreen
import com.jianzhang.app.ui.screens.RecurringScreen
import com.jianzhang.app.ui.screens.SettingsScreen
import com.jianzhang.app.ui.screens.StatisticsScreen
import com.jianzhang.app.ui.screens.TransferScreen

private data class BottomDestination(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private val bottomDestinations = listOf(
    BottomDestination("ledger", "账本", Icons.Default.MenuBook),
    BottomDestination("recurring", "固支", Icons.Default.CalendarMonth),
    BottomDestination("add", "记账", Icons.Default.Add),
    BottomDestination("assets", "资产", Icons.Default.AccountBalance),
    BottomDestination("statistics", "统计", Icons.Default.Assessment)
)

@Composable
fun JianzhangApp(viewModel: FinanceViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "ledger"
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { message -> snackbarHostState.showSnackbar(message) }
    }

    val showBottomBar = route in setOf("ledger", "recurring", "assets", "statistics")
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomDestinations.forEach { destination ->
                        val selected = route == destination.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (destination.route == "add") {
                                    navController.navigate("add")
                                } else {
                                    navController.navigate(destination.route) {
                                        popUpTo("ledger") { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "ledger",
            modifier = Modifier.padding(padding)
        ) {
            composable("ledger") {
                LedgerScreen(
                    state = state,
                    onMonthPrevious = viewModel::previousMonth,
                    onMonthNext = viewModel::nextMonth,
                    onOpenSettings = { navController.navigate("settings") },
                    onAdd = { navController.navigate("add") },
                        onEdit = { transaction ->
                            if (transaction.type == com.jianzhang.app.data.local.entity.TransactionType.TRANSFER) {
                                navController.navigate("transfer/${transaction.id}")
                            } else {
                                navController.navigate("add/${transaction.id}")
                            }
                        },
                    onDelete = viewModel::deleteTransaction,
                    onCreateAccount = viewModel::createRecommendedAccount,
                    onGenerateDemo = viewModel::generateDemoData
                )
            }
            composable("recurring") {
                RecurringScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onSave = viewModel::saveRecurringExpense,
                    onConfirm = viewModel::confirmRecurringExpense,
                    onDelete = viewModel::deleteRecurringExpense
                )
            }
            composable("add") {
                AddTransactionScreen(
                    state = state,
                    transactionId = null,
                    onBack = { navController.popBackStack() },
                    onSave = { id, amount, type, category, account, date, note ->
                        viewModel.saveTransaction(id, amount, type, category, account, date, note)
                        navController.popBackStack()
                    }
                )
            }
            composable(
                route = "add/{transactionId}",
                arguments = listOf(navArgument("transactionId") { type = NavType.StringType })
            ) { backStackEntry ->
                AddTransactionScreen(
                    state = state,
                    transactionId = backStackEntry.arguments?.getString("transactionId"),
                    onBack = { navController.popBackStack() },
                    onSave = { id, amount, type, category, account, date, note ->
                        viewModel.saveTransaction(id, amount, type, category, account, date, note)
                        navController.popBackStack()
                    }
                )
            }
            composable("assets") {
                AssetsScreen(
                    state = state,
                    onCreateAccount = viewModel::createAccount,
                    onUpdateAccount = viewModel::updateAccount,
                    onRemoveAccount = viewModel::removeAccount,
                    onAdjustBalance = viewModel::adjustAccountBalance,
                    onTransfer = { navController.navigate("transfer") }
                )
            }
            composable("transfer") {
                TransferScreen(
                    state = state,
                    transactionId = null,
                    onBack = { navController.popBackStack() },
                    onSave = { id, amount, from, to, date, note ->
                        viewModel.saveTransfer(id, amount, from, to, date, note)
                        navController.popBackStack()
                    }
                )
            }
            composable(
                route = "transfer/{transactionId}",
                arguments = listOf(navArgument("transactionId") { type = NavType.StringType })
            ) { backStackEntry ->
                TransferScreen(
                    state = state,
                    transactionId = backStackEntry.arguments?.getString("transactionId"),
                    onBack = { navController.popBackStack() },
                    onSave = { id, amount, from, to, date, note ->
                        viewModel.saveTransfer(id, amount, from, to, date, note)
                        navController.popBackStack()
                    }
                )
            }
            composable("statistics") {
                StatisticsScreen(
                    state = state,
                    onMonthPrevious = viewModel::previousMonth,
                    onMonthNext = viewModel::nextMonth,
                    onSetBudget = viewModel::setMonthlyBudget,
                    onSetCategoryBudget = viewModel::setCategoryBudget
                )
            }
            composable("settings") {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onGenerateDemo = viewModel::generateDemoData,
                    onClearDemo = viewModel::clearDemoData,
                    onOpenAssets = {
                        navController.popBackStack()
                        navController.navigate("assets")
                    },
                    themeMode = themeMode,
                    onSetThemeMode = viewModel::setThemeMode,
                    exportText = viewModel::exportText
                )
            }
        }
    }
}

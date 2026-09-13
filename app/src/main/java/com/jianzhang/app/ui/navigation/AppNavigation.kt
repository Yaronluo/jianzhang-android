package com.jianzhang.app.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
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
    BottomDestination("ledger", "账本", Icons.AutoMirrored.Filled.MenuBook),
    BottomDestination("recurring", "固支", Icons.Default.CalendarMonth),
    BottomDestination("assets", "资产", Icons.Default.AccountBalance),
    BottomDestination("statistics", "统计", Icons.Default.Assessment)
)

@Composable
fun JianzhangApp(viewModel: FinanceViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    var recurringAddRequested by rememberSaveable { mutableStateOf(false) }
    var assetAddRequested by rememberSaveable { mutableStateOf(false) }
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "ledger"
    val snackbarHostState = remember { SnackbarHostState() }
    val navigationBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LaunchedEffect(Unit) {
        viewModel.events.collect { message -> snackbarHostState.showSnackbar(message) }
    }

    val showBottomBar = route in setOf("ledger", "recurring", "assets", "statistics")
    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = "ledger",
                // Navigation Compose defaults to a fade transition. These routes are
                // lightweight app surfaces, so switch them immediately.
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None },
                modifier = Modifier.fillMaxSize()
            ) {
            composable("ledger") {
                ScreenWithInsets(scaffoldPadding = padding, topInset = false, bottomInset = false) {
                    LedgerScreen(
                        state = state,
                        onMonthPrevious = viewModel::previousMonth,
                        onMonthNext = viewModel::nextMonth,
                        onMonthCurrent = { viewModel.setMonth(java.time.YearMonth.now().toString()) },
                        onOpenSettings = { navController.navigate("settings") },
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
            }
            composable("recurring") {
                ScreenWithInsets(scaffoldPadding = padding, topInset = false, bottomInset = false) {
                    RecurringScreen(
                        state = state,
                        openAdd = recurringAddRequested,
                        onAddConsumed = { recurringAddRequested = false },
                        onSave = viewModel::saveRecurringExpense,
                        onConfirm = viewModel::confirmRecurringExpense,
                        onDelete = viewModel::deleteRecurringExpense
                    )
                }
            }
            composable("add") {
                ScreenWithInsets(scaffoldPadding = padding) {
                    AddTransactionScreen(
                        state = state,
                        transactionId = null,
                        onBack = { navController.popBackStack() },
                        onSave = { id, amount, type, category, account, date, note ->
                            viewModel.saveTransaction(id, amount, type, category, account, date, note)
                            navController.popBackStack()
                        },
                        onSaveTransfer = { id, amount, from, to, date, note ->
                            viewModel.saveTransfer(id, amount, from, to, date, note)
                            navController.popBackStack()
                        }
                    )
                }
            }
            composable(
                route = "add/{transactionId}",
                arguments = listOf(navArgument("transactionId") { type = NavType.StringType })
            ) { backStackEntry ->
                ScreenWithInsets(scaffoldPadding = padding) {
                    AddTransactionScreen(
                        state = state,
                        transactionId = backStackEntry.arguments?.getString("transactionId"),
                        onBack = { navController.popBackStack() },
                        onSave = { id, amount, type, category, account, date, note ->
                            viewModel.saveTransaction(id, amount, type, category, account, date, note)
                            navController.popBackStack()
                        },
                        onSaveTransfer = { id, amount, from, to, date, note ->
                            viewModel.saveTransfer(id, amount, from, to, date, note)
                            navController.popBackStack()
                        }
                    )
                }
            }
            composable("assets") {
                ScreenWithInsets(scaffoldPadding = padding, topInset = false, bottomInset = false) {
                    AssetsScreen(
                        state = state,
                        openAdd = assetAddRequested,
                        onAddConsumed = { assetAddRequested = false },
                        onCreateAccount = viewModel::createAccount,
                        onUpdateAccount = viewModel::updateAccount,
                        onRemoveAccount = viewModel::removeAccount,
                        onAdjustBalance = viewModel::adjustAccountBalance
                    )
                }
            }
            composable(
                route = "transfer/{transactionId}",
                arguments = listOf(navArgument("transactionId") { type = NavType.StringType })
            ) { backStackEntry ->
                ScreenWithInsets(scaffoldPadding = padding) {
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
            }
            composable("statistics") {
                ScreenWithInsets(scaffoldPadding = padding, bottomInset = false) {
                    StatisticsScreen(
                        state = state,
                        onMonthPrevious = viewModel::previousMonth,
                        onMonthNext = viewModel::nextMonth,
                        onSetBudget = viewModel::setMonthlyBudget,
                        onSetCategoryBudget = viewModel::setCategoryBudget
                    )
                }
            }
            composable("settings") {
                ScreenWithInsets(scaffoldPadding = padding) {
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

            if (showBottomBar) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                        .zIndex(2f)
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                        shape = RoundedCornerShape(32.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 8.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            bottomDestinations.forEach { destination ->
                                val selected = route == destination.route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(destination.route) {
                                            popUpTo("ledger") { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = { Icon(destination.icon, contentDescription = destination.label) },
                                    label = { Text(destination.label) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                Surface(
                    onClick = {
                        if (route == "recurring") {
                            recurringAddRequested = true
                        } else if (route == "assets") {
                            assetAddRequested = true
                        } else {
                            navController.navigate("add")
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(
                            end = 28.dp,
                            bottom = navigationBarInset + 12.dp + 64.dp + 16.dp
                        )
                        .size(56.dp)
                        .zIndex(3f),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shadowElevation = 8.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = when (route) {
                                "recurring" -> "新增固定支出"
                                "assets" -> "添加资产"
                                else -> "记账"
                            },
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScreenWithInsets(
    scaffoldPadding: PaddingValues,
    topInset: Boolean = true,
    bottomInset: Boolean = true,
    content: @Composable () -> Unit
) {
    val layoutDirection = LocalLayoutDirection.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = scaffoldPadding.calculateStartPadding(layoutDirection),
                    top = if (topInset) scaffoldPadding.calculateTopPadding() else 0.dp,
                    end = scaffoldPadding.calculateEndPadding(layoutDirection),
                    bottom = if (bottomInset) scaffoldPadding.calculateBottomPadding() else 0.dp
                )
        ) {
            content()
        }
    }
}

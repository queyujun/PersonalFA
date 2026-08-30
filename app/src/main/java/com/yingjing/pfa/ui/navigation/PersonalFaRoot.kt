package com.yingjing.pfa.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.yingjing.pfa.R
import com.yingjing.pfa.ui.screens.alerts.AlertsScreen
import com.yingjing.pfa.ui.screens.overview.OverviewScreen
import com.yingjing.pfa.ui.screens.overview.TrendDetailScreen
import com.yingjing.pfa.ui.screens.portfolio.AddTypePickerScreen
import com.yingjing.pfa.ui.screens.portfolio.HoldingDetailScreen
import com.yingjing.pfa.ui.screens.portfolio.HoldingFormScreen
import com.yingjing.pfa.ui.screens.portfolio.PortfolioScreen
import com.yingjing.pfa.ui.screens.settings.SettingsScreen
import com.yingjing.pfa.ui.screens.settings.SettingsViewModel
import com.yingjing.pfa.ui.screens.settings.ProfileEditScreen
import com.yingjing.pfa.ui.screens.settings.SyncDetailScreen
import com.yingjing.pfa.ui.screens.settings.BackupDetailScreen
import kotlinx.coroutines.launch

private const val ROUTE_ADD_TYPE = "add_type"
private const val ROUTE_FORM = "holding_form/{type}?holdingId={holdingId}"
private const val ROUTE_DETAIL = "holding_detail/{id}"
private const val ROUTE_TREND = "trend_detail"
private const val ROUTE_PROFILE_EDIT = "settings_profile"
private const val ROUTE_SYNC_DETAIL = "settings_sync"
private const val ROUTE_BACKUP_DETAIL = "settings_backup"

/** 应用根：顶栏(左上设置 · 居中标题 · 右上立即刷新) + 底部导航 + 各主区域 NavHost。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalFaRoot(rootViewModel: RootViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isTabRoute = currentRoute == null || TopDestination.entries.any { it.route == currentRoute }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val snackbarQuoteRefreshed = stringResource(R.string.snackbar_quote_refreshed)

    // 资产页列表状态：提升到根，使 FAB 能感知滚动——停留在顶部时显示 ＋，向下滚动浏览明细时
    // 隐藏，避免遮挡正好位于右下角的资产价值。
    val portfolioListState = rememberLazyListState()
    val fabVisible by remember {
        derivedStateOf {
            portfolioListState.firstVisibleItemIndex == 0 &&
                portfolioListState.firstVisibleItemScrollOffset == 0
        }
    }

    fun goTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        topBar = {
            if (isTabRoute) {
                CenterAlignedTopAppBar(
                    title = { Text(stringResource(R.string.app_name)) },
                    navigationIcon = {
                        IconButton(onClick = { goTab(TopDestination.Settings.route) }) {
                            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.cd_settings))
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            rootViewModel.refreshQuotesNow()
                            scope.launch { snackbarHostState.showSnackbar(snackbarQuoteRefreshed) }
                        }) {
                            Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.cd_refresh_quotes))
                        }
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (isTabRoute) {
                NavigationBar {
                    TopDestination.entries.forEach { dest ->
                        val selected =
                            backStackEntry?.destination?.hierarchy?.any { it.route == dest.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { goTab(dest.route) },
                            icon = { Icon(dest.icon, contentDescription = stringResource(dest.labelRes)) },
                            label = { Text(stringResource(dest.labelRes)) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (currentRoute == TopDestination.Portfolio.route) {
                AnimatedVisibility(
                    visible = fabVisible,
                    enter = scaleIn(),
                    exit = scaleOut(),
                ) {
                    FloatingActionButton(onClick = { navController.navigate(ROUTE_ADD_TYPE) }) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.cd_add_asset))
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopDestination.Overview.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(TopDestination.Overview.route) {
                OverviewScreen(onOpenTrend = { navController.navigate(ROUTE_TREND) })
            }
            composable(TopDestination.Portfolio.route) {
                PortfolioScreen(
                    onOpenHolding = { id -> navController.navigate("holding_detail/$id") },
                    listState = portfolioListState,
                )
            }
            composable(TopDestination.Alerts.route) { AlertsScreen() }
            composable(TopDestination.Settings.route) {
                SettingsScreen(
                    onOpenProfile = { navController.navigate(ROUTE_PROFILE_EDIT) },
                    onOpenSync = { navController.navigate(ROUTE_SYNC_DETAIL) },
                    onOpenBackup = { navController.navigate(ROUTE_BACKUP_DETAIL) },
                )
            }

            // 设置二级界面：共享 settings 路由的 SettingsViewModel 实例，编辑后一级摘要自动更新。
            composable(ROUTE_PROFILE_EDIT) { entry ->
                val parentEntry = remember(entry) { navController.getBackStackEntry(TopDestination.Settings.route) }
                val vm = hiltViewModel<SettingsViewModel>(parentEntry)
                ProfileEditScreen(viewModel = vm, onBack = { navController.popBackStack() })
            }
            composable(ROUTE_SYNC_DETAIL) { entry ->
                val parentEntry = remember(entry) { navController.getBackStackEntry(TopDestination.Settings.route) }
                val vm = hiltViewModel<SettingsViewModel>(parentEntry)
                SyncDetailScreen(viewModel = vm, onBack = { navController.popBackStack() })
            }
            composable(ROUTE_BACKUP_DETAIL) { entry ->
                val parentEntry = remember(entry) { navController.getBackStackEntry(TopDestination.Settings.route) }
                val vm = hiltViewModel<SettingsViewModel>(parentEntry)
                BackupDetailScreen(viewModel = vm, onBack = { navController.popBackStack() })
            }

            composable(ROUTE_TREND) {
                TrendDetailScreen(onBack = { navController.popBackStack() })
            }

            composable(ROUTE_ADD_TYPE) {
                AddTypePickerScreen(
                    onPick = { type -> navController.navigate("holding_form/${type.name}") },
                    onClose = { navController.popBackStack() },
                )
            }
            composable(
                route = ROUTE_FORM,
                arguments = listOf(
                    navArgument("type") { type = NavType.StringType },
                    navArgument("holdingId") {
                        type = NavType.StringType
                        defaultValue = "-1"
                    },
                ),
            ) {
                HoldingFormScreen(
                    onSaved = {
                        navController.popBackStack(TopDestination.Portfolio.route, inclusive = false)
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = ROUTE_DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) {
                HoldingDetailScreen(
                    onEdit = { type, id ->
                        navController.navigate("holding_form/${type.name}?holdingId=$id")
                    },
                    onDeleted = {
                        navController.popBackStack(TopDestination.Portfolio.route, inclusive = false)
                    },
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

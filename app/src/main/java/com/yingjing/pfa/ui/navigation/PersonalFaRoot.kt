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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.yingjing.pfa.ui.screens.alerts.AlertsScreen
import com.yingjing.pfa.ui.screens.overview.OverviewScreen
import com.yingjing.pfa.ui.screens.overview.TrendDetailScreen
import com.yingjing.pfa.ui.screens.portfolio.AddTypePickerScreen
import com.yingjing.pfa.ui.screens.portfolio.HoldingDetailScreen
import com.yingjing.pfa.ui.screens.portfolio.HoldingFormScreen
import com.yingjing.pfa.ui.screens.portfolio.PortfolioScreen
import com.yingjing.pfa.ui.screens.settings.SettingsScreen
import kotlinx.coroutines.launch

private const val ROUTE_ADD_TYPE = "add_type"
private const val ROUTE_FORM = "holding_form/{type}?holdingId={holdingId}"
private const val ROUTE_DETAIL = "holding_detail/{id}"
private const val ROUTE_TREND = "trend_detail"

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
                    title = { Text("盈景私助") },
                    navigationIcon = {
                        IconButton(onClick = { goTab(TopDestination.Settings.route) }) {
                            Icon(Icons.Filled.Settings, contentDescription = "设置")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            rootViewModel.refreshQuotesNow()
                            scope.launch { snackbarHostState.showSnackbar("已触发行情刷新") }
                        }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "立即刷新行情")
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
                            icon = { Icon(dest.icon, contentDescription = dest.label) },
                            label = { Text(dest.label) },
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
                        Icon(Icons.Filled.Add, contentDescription = "添加资产")
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
            composable(TopDestination.Settings.route) { SettingsScreen() }

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

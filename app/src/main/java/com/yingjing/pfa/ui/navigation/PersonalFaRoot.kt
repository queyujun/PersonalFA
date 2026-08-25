package com.yingjing.pfa.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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

private const val ROUTE_ADD_TYPE = "add_type"
private const val ROUTE_FORM = "holding_form/{type}?holdingId={holdingId}"
private const val ROUTE_DETAIL = "holding_detail/{id}"
private const val ROUTE_TREND = "trend_detail"

/** 应用根：顶栏(品牌 + 设置入口) + 底部导航 + 各主区域 NavHost；资产的添加/编辑/详情作为独立路由。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalFaRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isTabRoute = currentRoute == null || TopDestination.entries.any { it.route == currentRoute }

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
                TopAppBar(
                    title = { Text("盈景私助") },
                    actions = {
                        IconButton(onClick = { goTab(TopDestination.Settings.route) }) {
                            Icon(Icons.Filled.Settings, contentDescription = "设置")
                        }
                    },
                )
            }
        },
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
                FloatingActionButton(onClick = { navController.navigate(ROUTE_ADD_TYPE) }) {
                    Icon(Icons.Filled.Add, contentDescription = "添加资产")
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
                PortfolioScreen(onOpenHolding = { id -> navController.navigate("holding_detail/$id") })
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

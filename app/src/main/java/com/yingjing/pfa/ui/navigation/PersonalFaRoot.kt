package com.yingjing.pfa.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import com.yingjing.pfa.domain.model.HousePriceSyncStatus
import com.yingjing.pfa.domain.model.SyncSource
import com.yingjing.pfa.ui.screens.alerts.AlertsScreen
import com.yingjing.pfa.ui.screens.ai.AiInsightScreen
import com.yingjing.pfa.ui.screens.ai.AiReportScreen
import com.yingjing.pfa.ui.screens.overview.OverviewScreen
import com.yingjing.pfa.ui.screens.overview.TrendDetailScreen
import com.yingjing.pfa.ui.screens.portfolio.AddTypePickerScreen
import com.yingjing.pfa.ui.screens.portfolio.HoldingDetailScreen
import com.yingjing.pfa.ui.screens.portfolio.HoldingFormScreen
import com.yingjing.pfa.ui.screens.portfolio.PortfolioScreen
import com.yingjing.pfa.ui.screens.settings.AiProfilesScreen
import com.yingjing.pfa.ui.screens.settings.AiSettingsScreen
import com.yingjing.pfa.ui.screens.settings.SettingsScreen
import com.yingjing.pfa.ui.screens.settings.SettingsViewModel
import com.yingjing.pfa.ui.screens.settings.ProfileEditScreen
import com.yingjing.pfa.ui.screens.settings.SyncDetailScreen
import com.yingjing.pfa.ui.screens.settings.BackupDetailScreen
import com.yingjing.pfa.ui.screens.subscription.SubscriptionFormScreen
import com.yingjing.pfa.ui.screens.subscription.SubscriptionScreen
import kotlinx.coroutines.launch

private const val ROUTE_ADD_TYPE = "add_type"
private const val ROUTE_FORM = "holding_form/{type}?holdingId={holdingId}"
private const val ROUTE_DETAIL = "holding_detail/{id}"
private const val ROUTE_TREND = "trend_detail"
private const val ROUTE_PROFILE_EDIT = "settings_profile"
private const val ROUTE_SYNC_DETAIL = "settings_sync"
private const val ROUTE_BACKUP_DETAIL = "settings_backup"
private const val ROUTE_AI_SETTINGS = "settings_ai"
private const val ROUTE_AI_SETTINGS_EDIT = "settings_ai_edit/{profileId}"
private const val ROUTE_AI_REPORT = "ai_report"
private const val ROUTE_AI_INSIGHT = "ai_insight"
private const val ROUTE_SUB_FORM = "subscription_form?subId={subId}"

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

    // 同步完成结果上报：用户手动「立即刷新」后，由 SyncStateStore 持久化的最近结果驱动弹窗，
    // 提示更新是否成功；若失败，逐项列出未能更新的数据源（用 SyncSource.labelRes 映射展示名）。
    val pendingResult by rootViewModel.pendingResult.collectAsState()
    val context = LocalContext.current
    val failedList = remember(pendingResult) {
        pendingResult?.failedSources?.joinToString("\n") { src -> context.getString(src.labelRes) }
            .orEmpty()
    }

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
                    // 顶栏标题用图片 logo 代替文字：按系统语言自动选取英文/中文版（资源限定符）。
                    title = {
                        Image(
                            painter = painterResource(R.drawable.logo_title),
                            contentDescription = stringResource(R.string.app_name),
                            modifier = Modifier.height(28.dp),
                            contentScale = ContentScale.Fit,
                        )
                    },
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
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
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
            } else if (currentRoute == TopDestination.Subscriptions.route) {
                // 订阅页固定显示 ＋：新增订阅是主操作，列表短无需滚动隐藏。
                FloatingActionButton(onClick = { navController.navigate(ROUTE_SUB_FORM) }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.cd_add_subscription))
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
                OverviewScreen(
                    onOpenTrend = { navController.navigate(ROUTE_TREND) },
                    onOpenAiReport = { navController.navigate(ROUTE_AI_REPORT) },
                    onOpenAiInsight = { navController.navigate(ROUTE_AI_INSIGHT) },
                )
            }
            composable(TopDestination.Portfolio.route) {
                PortfolioScreen(
                    onOpenHolding = { id -> navController.navigate("holding_detail/$id") },
                    listState = portfolioListState,
                )
            }
            composable(TopDestination.Subscriptions.route) {
                SubscriptionScreen(
                    onOpenEdit = { id -> navController.navigate("subscription_form?subId=$id") },
                )
            }
            composable(TopDestination.Alerts.route) { AlertsScreen() }
            composable(TopDestination.Settings.route) {
                SettingsScreen(
                    onOpenProfile = { navController.navigate(ROUTE_PROFILE_EDIT) },
                    onOpenSync = { navController.navigate(ROUTE_SYNC_DETAIL) },
                    onOpenBackup = { navController.navigate(ROUTE_BACKUP_DETAIL) },
                    onOpenAi = { navController.navigate(ROUTE_AI_SETTINGS) },
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

            // AI 配置一级页（档案列表）：独立 ViewModel；返回时一级设置页经 aiSettingsStore 流自动刷新摘要。
            composable(ROUTE_AI_SETTINGS) {
                AiProfilesScreen(
                    onBack = { navController.popBackStack() },
                    onOpenEdit = { profileId -> navController.navigate("settings_ai_edit/$profileId") },
                )
            }

            // AI 档案编辑页：profileId = 档案 id 或 new（新建自定义）；删除成功后返回列表页。
            composable(
                route = ROUTE_AI_SETTINGS_EDIT,
                arguments = listOf(navArgument("profileId") { type = NavType.StringType }),
            ) {
                AiSettingsScreen(
                    onBack = { navController.popBackStack() },
                    onDeleted = { navController.popBackStack() },
                )
            }

            // AI 报告：独立 ViewModel；未配置/无 key 时可从错误态直接跳设置页。
            composable(ROUTE_AI_REPORT) {
                AiReportScreen(
                    onBack = { navController.popBackStack() },
                    onGoSettings = { navController.navigate(ROUTE_AI_SETTINGS) },
                )
            }

            // AI 持仓分析：同报告页骨架 + 可选聚焦问题，无导出。
            composable(ROUTE_AI_INSIGHT) {
                AiInsightScreen(
                    onBack = { navController.popBackStack() },
                    onGoSettings = { navController.navigate(ROUTE_AI_SETTINGS) },
                )
            }

            composable(
                route = ROUTE_SUB_FORM,
                arguments = listOf(
                    navArgument("subId") {
                        type = NavType.StringType
                        defaultValue = "-1"
                    },
                ),
            ) {
                SubscriptionFormScreen(
                    onSaved = {
                        navController.popBackStack(TopDestination.Subscriptions.route, inclusive = false)
                    },
                    onBack = { navController.popBackStack() },
                )
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

    pendingResult?.let { result ->
        val isTotal = result.failedSources.size >= SyncSource.entries.size
        AlertDialog(
            onDismissRequest = { rootViewModel.dismissResult() },
            title = {
                Text(
                    when {
                        !result.hasFailure -> stringResource(R.string.sync_result_success_title)
                        isTotal -> stringResource(R.string.sync_result_failed_title)
                        else -> stringResource(R.string.sync_result_partial_title)
                    }
                )
            },
            text = {
                Column {
                    Text(
                        when {
                            !result.hasFailure -> stringResource(R.string.sync_result_success_body)
                            isTotal -> stringResource(R.string.sync_result_failed_body, failedList)
                            else -> stringResource(R.string.sync_result_partial_body, failedList)
                        }
                    )
                    // 房价指数状态备注：让用户显式看到指数已更新至 X 月 / 已是最新（X 月）。
                    result.housePriceNote?.let { note ->
                        Spacer(Modifier.height(12.dp))
                        val month = note.latestMonth ?: ""
                        Text(
                            text = if (note.status == HousePriceSyncStatus.REFRESHED) {
                                stringResource(R.string.sync_house_refreshed, month)
                            } else {
                                stringResource(R.string.sync_house_skipped, month)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { rootViewModel.dismissResult() }) {
                    Text(stringResource(R.string.common_ok))
                }
            },
        )
    }
}

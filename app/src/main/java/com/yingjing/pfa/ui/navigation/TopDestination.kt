package com.yingjing.pfa.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.ui.graphics.vector.ImageVector

/** 底部导航的四个主区域。 */
enum class TopDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    Overview("overview", "总览", Icons.Filled.PieChart),
    Portfolio("portfolio", "资产", Icons.Filled.AccountBalanceWallet),
    Alerts("alerts", "提醒", Icons.Filled.Notifications),
    Settings("settings", "我的", Icons.Filled.Person),
}

package com.yingjing.pfa.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.ui.graphics.vector.ImageVector
import com.yingjing.pfa.R

/** 底部导航的五个主区域。 */
enum class TopDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    Overview("overview", R.string.nav_overview, Icons.Filled.PieChart),
    Portfolio("portfolio", R.string.nav_portfolio, Icons.Filled.AccountBalanceWallet),
    Subscriptions("subscriptions", R.string.nav_subscriptions, Icons.Filled.Subscriptions),
    Alerts("alerts", R.string.nav_alerts, Icons.Filled.Notifications),
    Settings("settings", R.string.nav_settings, Icons.Filled.Person),
}

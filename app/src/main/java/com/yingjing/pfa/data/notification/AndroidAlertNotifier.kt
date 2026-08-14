package com.yingjing.pfa.data.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.yingjing.pfa.R
import com.yingjing.pfa.domain.alert.AlertNotifier
import com.yingjing.pfa.domain.model.Alert
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.absoluteValue

/** Android 本地通知实现。 */
@Singleton
class AndroidAlertNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) : AlertNotifier {

    override fun notify(alert: Alert) {
        ensureChannel()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return // 未授予通知权限则静默跳过
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(alert.title)
            .setContentText(alert.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        runCatching {
            NotificationManagerCompat.from(context)
                .notify(alert.dedupKey.hashCode().absoluteValue, notification)
        }
    }

    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "资产提醒",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "持仓 / 新股 / 市场 / 国际 提醒" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "pfa_alerts"
    }
}

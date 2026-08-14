package com.yingjing.pfa.domain.alert

import com.yingjing.pfa.domain.model.Alert

/** 通知发送抽象（便于测试注入假实现）。 */
fun interface AlertNotifier {
    fun notify(alert: Alert)
}

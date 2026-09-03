package com.yingjing.pfa.domain.usecase

import com.yingjing.pfa.domain.model.BillingCycle
import com.yingjing.pfa.domain.model.Subscription
import java.time.LocalDate

/**
 * 订阅续费日顺延（纯函数，可单测）。
 *
 * 每次同步把「下次续费日」之后、到 [nowMs] 为止所有已过的周期各顺延一遍（错过多次一次补齐，
 * 始终指向未来）。例如月付订阅一个月未打开应用，续费日仍只顺延到下一个月，不重复计费。
 *
 * 返回顺延后的订阅；无需顺延返回 null（调用方保留原值）。日期按 UTC 计算，与快照口径一致。
 */
object SubscriptionRenewal {

    fun advance(subscription: Subscription, nowMs: Long): Subscription? {
        if (!subscription.active) return null
        var next = Subscription.dateOf(subscription.nextRenewalEpochMs)
        if (next > today(nowMs)) return null

        val cycle = subscription.cycle
        // 防御：异常老日期（如系统时间被回拨）也有限步收敛，避免死循环。
        var steps = 0
        while (!next.isAfter(today(nowMs)) && steps < MAX_STEPS) {
            next = cycle.advance(next)
            steps++
        }
        if (steps == 0) return null
        return subscription.copy(nextRenewalEpochMs = Subscription.epochMsOf(next))
    }

    /** 展示用：不动库，仅把库中续费日内存顺延到未来（如同步尚未跑）。 */
    fun displayRenewal(subscription: Subscription, nowMs: Long): LocalDate {
        var next = Subscription.dateOf(subscription.nextRenewalEpochMs)
        val cycle = subscription.cycle
        var steps = 0
        while (!next.isAfter(today(nowMs)) && steps < MAX_STEPS) {
            next = cycle.advance(next)
            steps++
        }
        return next
    }

    private fun today(nowMs: Long): LocalDate = Subscription.dateOf(nowMs)

    private const val MAX_STEPS = 1000
}

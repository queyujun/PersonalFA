package com.yingjing.pfa.ui.screens.portfolio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 资产页分类折叠/展开状态（进程级，跨页面切换与配置变更存活）。
 *
 * 语义：**集合中存在的 key 表示「展开」，不存在表示「折叠」**。初始为空集 → 首次进入资产页全部折叠。
 * 用户展开过的分类加入集合，折叠则移除；再次进入资产页时集合不变 → 恢复上次离开时的状态。
 *
 * 进程存活期间状态在内存中保留；不持久化到磁盘（重启后回到全折叠的「首次」语义）。
 */
@Singleton
class PortfolioCollapseStore @Inject constructor() {

    private val _expandedCategories = MutableStateFlow<Set<String>>(emptySet())
    val expandedCategories: StateFlow<Set<String>> = _expandedCategories.asStateFlow()

    private val _expandedSubGroups = MutableStateFlow<Set<String>>(emptySet())
    val expandedSubGroups: StateFlow<Set<String>> = _expandedSubGroups.asStateFlow()

    /** 切换一级分类的展开/折叠（不可变更新）。 */
    fun toggleCategory(categoryKey: String) {
        _expandedCategories.update { current ->
            if (categoryKey in current) current - categoryKey else current + categoryKey
        }
    }

    /** 切换二级子类目（股票市场）的展开/折叠（不可变更新）。 */
    fun toggleSubGroup(subGroupKey: String) {
        _expandedSubGroups.update { current ->
            if (subGroupKey in current) current - subGroupKey else current + subGroupKey
        }
    }
}

package com.yingjing.pfa.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 70 城名单守护测试：名单必须与国家统计局官方口径一致（恰好 70 个、无重复），
 * 且保存校验（HousePriceValidator 依赖的 contains）按精确匹配工作。
 */
class HousePriceCitiesTest {

    @Test
    fun `list contains exactly 70 cities`() {
        assertEquals(70, HousePriceCities.ALL.size)
    }

    @Test
    fun `list has no duplicates`() {
        assertEquals(70, HousePriceCities.ALL.toSet().size)
    }

    @Test
    fun `contains matches exact name`() {
        assertTrue(HousePriceCities.contains("北京"))
        assertTrue(HousePriceCities.contains("乌鲁木齐"))
    }

    @Test
    fun `contains rejects non-list names`() {
        assertFalse(HousePriceCities.contains("北京市"))
        assertFalse(HousePriceCities.contains("苏州"))
        assertFalse(HousePriceCities.contains(null))
        assertFalse(HousePriceCities.contains(""))
    }

    @Test
    fun `all four municipalities are present`() {
        listOf("北京", "天津", "上海", "重庆").forEach {
            assertTrue("missing $it", HousePriceCities.contains(it))
        }
    }
}

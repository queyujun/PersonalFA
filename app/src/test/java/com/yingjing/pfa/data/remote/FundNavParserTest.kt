package com.yingjing.pfa.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FundNavParserTest {

    @Test
    fun parsesJsonp_prefersGsz() {
        // 估算值 gsz 优先于官方净值 dwjz
        val body = """jsonpgz({"fundcode":"005827","name":"易方达蓝筹","dwjz":"2.5432","gsz":"2.5500","gszzl":"0.27","gztime":"2024-01-05 15:00"});"""
        val result = FundNavParser.parse(body)
        assertEquals(2.5500, result["005827"]!!, 0.0001)
    }

    @Test
    fun parsesJsonp_fallsBackToDwjz_whenGszMissing() {
        // 非交易时段无 gsz → 回退 dwjz（最近公布的单位净值）
        val body = """jsonpgz({"fundcode":"110011","dwjz":"1.2345","gsz":"","gztime":""});"""
        val result = FundNavParser.parse(body)
        assertEquals(1.2345, result["110011"]!!, 0.0001)
    }

    @Test
    fun parsesJsonp_fallsBackToDwjz_whenGszInvalid() {
        // gsz 为 0/非法 → 回退 dwjz
        val body = """jsonpgz({"fundcode":"110011","dwjz":"1.2345","gsz":"0"});"""
        val result = FundNavParser.parse(body)
        assertEquals(1.2345, result["110011"]!!, 0.0001)
    }

    @Test
    fun returnsEmpty_whenBothNavsMissing() {
        val body = """jsonpgz({"fundcode":"005827","dwjz":"","gsz":""});"""
        val result = FundNavParser.parse(body)
        assertTrue(result.isEmpty())
    }

    @Test
    fun returnsEmpty_whenNoJsonObject() {
        val body = """var v = "";"""
        val result = FundNavParser.parse(body)
        assertTrue(result.isEmpty())
    }

    @Test
    fun returnsEmpty_whenMalformed() {
        val body = """jsonpgz({broken);"""
        val result = FundNavParser.parse(body)
        assertTrue(result.isEmpty())
    }
}

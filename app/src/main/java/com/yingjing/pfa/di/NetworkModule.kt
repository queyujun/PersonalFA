package com.yingjing.pfa.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.time.Duration
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(8))
            .readTimeout(Duration.ofSeconds(10))
            // 总兜底：单个数据源（含不可达的海外源）最多占用 20s，
            // 超时即放弃该源、继续更新其他可更新数据。
            .callTimeout(Duration.ofSeconds(20))
            .build()

    /**
     * AI 专用客户端：LLM 生成整篇报告可能需要 1-2 分钟以上，
     * 沿用默认客户端的 20s callTimeout 必然截断，故单独放长。
     */
    @Provides
    @Singleton
    @AiClient
    fun provideAiClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(15))
            .readTimeout(Duration.ofSeconds(120))
            .callTimeout(Duration.ofSeconds(180))
            .build()
}

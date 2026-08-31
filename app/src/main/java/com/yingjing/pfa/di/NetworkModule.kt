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
}

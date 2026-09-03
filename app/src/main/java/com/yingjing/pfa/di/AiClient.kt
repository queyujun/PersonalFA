package com.yingjing.pfa.di

import javax.inject.Qualifier

/** 标记 AI 专用的 OkHttpClient（长超时：LLM 生成可能超过 2 分钟）。 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AiClient

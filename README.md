# 盈睿伴 · RichWin

个人金融资产管理 Android App —— 中文界面、多用户、本地加密数据库、每日省流量更新。
盈睿伴不是一款记账软件，而是面向成功人士的资产集中综合管理应用，能帮你节约大量时间，并直观感受你资产的变动和因果。

适合人群：
家境殷实的中年人或者事业有成的青年人，有资产跨境（¥/HK$/US$ 三币种、港美股、可能人在大陆/香港或大湾区工作）一站式管理跟踪的需求，投资分散体量适中（A 股、场外基金、实物金、加密货币、可能还有公司股权-创业者或早期员工）。
追求掌控感与确定性，长期主义。「盈」是目标、「睿」是方法，拒绝把全部身家交给云端记账 App，接受自管加密备份、自录数据。

## 功能范围（v1）

资产类别：房产 · 存款 · A股/港股/美股 · 账户现金 · 黄金ETF · 国债ETF · 公司股权 · 区块链 · 负债（全类别可增/删/改）。
能力：多币种（¥/HK$/US$）切换 · 总览净值/走势线图/分布饼图 · 提醒（持仓/新股/市场/国际） · 加密备份/恢复。
AI: 人工智能资产分析和报告建议。可自定义模型。

## 技术栈

Kotlin 2.0 · Jetpack Compose (Material 3) · Hilt · Room + **SQLCipher（整库加密）** · WorkManager（后续）· Vico 图表（后续）。
`minSdk 29 / targetSdk 35 / compileSdk 35`。

## 本机开发（内置 portable 工具链）

本仓库在无系统级 JDK/SDK 的机器上，用 `.toolchain/`（**不入库**）中的 portable JDK 17 / Android SDK / Gradle 构建：

```bash
source .toolchain/env.sh          # 载入 JAVA_HOME / ANDROID_HOME / gradle
./gradlew :app:assembleDebug      # 构建 Debug APK
./gradlew testDebugUnitTest       # 运行单元测试
```

## 用 Android Studio 打开

直接 `Open` 本目录即可。若你有自己的 Android SDK，改 `local.properties` 的 `sdk.dir` 指向它。

## 目录

- `app/` —— 应用模块（`com.yingjing.pfa`）
- `design/` —— UI 设计稿（`ui-mockup.html`，浏览器打开）与数据源方案
- `docs/` —— 实现方案（分阶段路线图 P0–P7）

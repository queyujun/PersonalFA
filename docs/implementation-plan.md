# 盈景私助 · 实现方案（Implementation Plan）

> 配套文档：`design/ui-mockup.html`（UI 设计稿 v6，16 屏，已定稿）、`design/data-sources.md`（数据源与省流量方案）
> 状态：待用户确认 → 确认后进入 Phase 0 编码（TDD）

---

## 1. 目标与范围

- **平台**：Android 原生，中文界面，Material 3。
- **核心**：多用户注册管理、本地数据库（**整库加密**、可备份/恢复）、每日**省流量**自动更新行情。
- **资产类别（全类别支持增/删/改）**：房产、存款、A股、港股、美股、账户现金、黄金ETF、国债ETF、公司股权、区块链、负债。
- **能力**：多币种（¥ / HK$ / US$）切换；总览净值 + 走势线图 + 分布饼图；提醒（持仓 / 新股 / 市场 / 国际）。
- **部署**：**v1 完全本地、无服务器组件**（房产用手动估值 + 官方 70 城指数；自动抓价为后续可选增强）。

## 2. 技术栈与选型（Research & Reuse）

> 具体版本号在 Phase 0 由后台调研结论最终敲定；架构不受版本影响。

| 关注点 | 选择 | 理由 |
|---|---|---|
| 语言 / UI | Kotlin + Jetpack Compose (Material 3) | Android 原生首选，声明式 UI |
| 架构 | MVVM + Clean Architecture（ui / domain / data）+ 单向数据流 | 可测试、低耦合、契合「小文件高内聚」 |
| 本地库 | Room + **SQLCipher**（整库加密） | 满足「本地数据库 + 加密」 |
| 偏好存储 | DataStore(Preferences) | 默认币种、更新时间、阈值等 |
| 依赖注入 | Hilt | 官方方案，测试易替换 |
| 异步 | Coroutines + Flow | 响应式数据流 |
| 网络 | Retrofit + OkHttp + kotlinx.serialization（+ Scalars 处理新浪 GBK 文本） | 覆盖 JSON 与文本行情 |
| 定时 | WorkManager（每日一次，约束：联网） | 省流量、系统级可靠调度 |
| 图表 | **Vico**（Compose 原生）— 备选 MPAndroidChart | 走势线图 + 分布饼图 |
| 通知 | Notification API + WorkManager | 提醒推送 |
| 认证 | 本地密码哈希（bcrypt/argon2）+ 可选 BiometricPrompt | 多用户、离线安全 |
| 备份 | AES-GCM 加密导出（口令派生密钥）+ SAF 文件 + 每周自动 | 可备份/恢复、可迁移 |
| 测试 | JUnit + MockK + Turbine + Room in-memory + MockWebServer + Compose UI Test | TDD、80%+ 覆盖 |
| 房产估值 | 手动填写 + 国家统计局 70 城指数（v1 **无服务器**） | 完全本地零成本；自动抓价列为后续可选增强 |

### 2.1 选型定版与接口注意事项（后台调研 · 2026-08）

- **新浪行情**：必须 HTTPS + 请求头 `Referer: https://finance.sina.com.cn`（否则 403），响应为 **GBK**，用 OkHttp 拦截器转码；批量前缀：A股 `sh/sz`、港股 `rt_hk`、美股 `gb_`+小写代码、场内 ETF 走 `sh/sz`（如 `sh518880` 已确认）；外汇 `fx_susdcny` 等。返回是 `var xxx="a,b,c"` 文本 → 自写解析（非 JSON）。腾讯 `qt.gtimg.cn` 作 fallback（间隔 ≥100ms）。
- **CoinGecko**：免费访问现基本**需注册免费 Demo Key**（100 次/分、1 万次/月），Header `x-cg-demo-api-key`；`/simple/price?ids=…&vs_currencies=cny,hkd,usd` 一次出三法币。Key 不放明文/query。
- **新股日历**：东方财富 `datacenter` 接口已限速；参考开源 **AKShare** 的 URL 构造直接照搬，数据仅供参考、以交易所为准。
- **加密库**：用新包 **`net.zetetic:sqlcipher-android:4.17.0`**（旧 `android-database-sqlcipher` 已废弃、缺 16KB 页支持）+ `androidx.sqlite`；密钥经 Android Keystore 派生。
- **图表**：**Vico 3.2.2**（Compose-first）画走势线图；**饼图 Vico 较弱 → 用 Canvas 自绘**（也更贴合分类直标）。避开停更的 MPAndroidChart。
- **网络**：**Retrofit 3.0.0** + 一方 `converter-kotlinx-serialization`（+ OkHttp 4.12）；注意 suspend 接口默认抛异常，统一 try/catch。
- **WorkManager**：`PeriodicWork(24h)` + `setInitialDelay` 对齐时刻 + `setFlexTimeInterval` + 约束(联网/电量) + 指数退避；最小间隔 15 min、Doze 会顺延（正常）；要精确 09:00 推送可用 `AlarmManager.setExactAndAllowWhileIdle`。
- **备份(SAF)**：`ActivityResultContracts.CreateDocument`，无需存储权限；导出加密库前先 **WAL checkpoint** 并含 `-wal/-shm`，保证一致。
- **可复用参考**：架构范本 `nkuppan/expensemanager`（活跃, 多模块 Compose+Hilt+Room）、`Ivy-Apps/ivy-wallet`（Compose ADR, 已归档只借鉴）；**净值/多币种领域模型**参考 `wealthfolio`（Rust/React, 建模最贴题）与 `venil7/assets`。

## 3. 架构总览

**分层**
- `ui`：Compose 屏幕 + ViewModel（状态用不可变 data class，单向数据流）
- `domain`：`sealed interface Asset` 及子类型、UseCase（净值计算/换算/分类/计息/阈值判定）、Repository 接口
- `data`：Room（本地）+ Remote（行情/汇率/新股/房产代理）+ Repository 实现 + SyncManager（WorkManager）

**包/模块（先单工程分包，后可拆多模块）**
`core/{common,designsystem,database,network}` · `feature/{auth,portfolio,assetEdit,overview,alerts,settings}` · `domain` · `data`

**数据流**
`WorkManager → SyncManager →（RemoteDataSource 批量取价 + FxDataSource + AlertEngine）→ Room → Flow → ViewModel → Compose`

## 4. 数据模型（Room）

**Domain**：`sealed interface Asset { id; userId; name; currency; createdAt; updatedAt }`
子类型：`RealEstate / Deposit / Stock / AccountCash / GoldEtf / BondEtf / Equity / Crypto / Liability`

**Entities（关键字段）**
- `users(id, username↑unique, pwdHash, salt, defaultCurrency, createdAt)`
- `holdings(id, userId→, type[enum], name, currency, quantity?, costPrice?, manualValue?, market?, symbol?, tokenId?, sharePercent?, city?, communityId?, areaSqm?, depositType?, annualRate?, startDate?, maturityDate?, liabilityType?, monthlyPayment?, autoUpdate, sortOrder, createdAt, updatedAt)`
  —— 宽表 + `type` 区分；`ACCOUNT_CASH` 归入「股票」合计；`GOLD_ETF/BOND_ETF` 单独归「黄金/国债」；`LIABILITY` 计负值。
- `quotes(symbol, market, price, currency, asOf)` —— 最新价缓存（**跨用户共享**，省流量）
- `price_history(refKey, date, price)` —— 单标的历史（详情走势）
- `networth_snapshots(userId, date, currency, totalAssets, totalLiabilities, net)` —— 每日快照（总览走势）
- `fx_rates(date, base, quote, rate)`
- `alerts(id, userId, category[HOLDING/IPO/MARKET/GLOBAL], severity, title, body, refHoldingId?, ts, read)`
- `symbols(market, code, name)` —— 搜索联想 + 内置主流币列表

**计算**：净值 = Σ(资产 × 现价 → 换算到展示币种) − Σ(负债)。利息按日计提。

## 5. 关键子系统

**5.1 多用户与安全**：注册/登录、按 `userId` 数据隔离、密码哈希、SQLCipher 整库加密、可选生物识别、切换用户。

**5.2 行情抓取 + 省流量**：`SinaQuoteApi`（批量 `list=`）+ 腾讯备用 + `CoinGeckoApi`（多法币）+ `SinaFxApi` + `EastmoneyIpoApi`。`SyncManager` 每日触发：**只取持仓 symbol、合并批量、写 quotes/history、失败重试并回退缓存**。目标 **<20KB/天**。

**5.3 房产估值（手动 + 指数，v1 无服务器）**：用户手动填「当前估值」，可随时更新；可选开启「按**国家统计局 70 城房价指数**（官方公开、城市级、月度）自动估算涨跌」对基准价做环比微调。**不抓取中介数据、无后端**，App 保持 100% 本地。数据模型已预留 `manualValue / autoUpdate / 指数回退`，**后续如需**自动抓小区均价，补一个 Cloudflare Workers 轻量代理即可，不影响现有设计。

**5.4 汇率与多币种**：每日取 USD/CNY、HKD/CNY；展示层按选定币种换算；加密货币由 CoinGecko 直出三法币。

**5.5 净值快照与图表**：每日同步后写 `networth_snapshot`；总览走势线图（单序列，1月/3月/1年）+ 分布饼图（按类别，直接标注）。遵循已加载的 dataviz 规范与**红涨绿跌**。

**5.6 提醒规则引擎**
- 持仓：存款到期倒计时；持股公司公告/业绩/分红（交易所披露 + 快讯关键词命中持仓）；持币波动阈值 + 公告；ETF/房价指数。
- 新股：东方财富申购日历，每日 09:00。
- 市场：指数/汇率阈值。宏观/国际：财经日历 + 快讯关键词打分。
- 阈值可在设置页配置；产出 `alerts` + 本地通知。

**5.7 备份/恢复**：AES-GCM 加密导出（口令派生密钥）→ SAF 文件；每周自动；导入校验后写库；**删除条目前自动快照备份**（可恢复）。

## 6. CRUD 与导航

- 全类别：**添加**（类型选择 → 表单）、**编辑**（共用表单预填）、**删除**（列表左滑 / 详情，二次确认 + 删前备份）。
- 导航：Navigation-Compose；底部 4 tab（总览/资产/提醒/我的）+ FAB。

## 7. 测试策略（TDD，80%+）

- **单元**：UseCase（净值/换算/分类/计息/阈值）、Repository、行情解析器（新浪文本 / CoinGecko JSON）。
- **集成**：Room DAO（in-memory）、SyncManager（MockWebServer）、备份加密往返。
- **UI**：关键 Compose 屏；E2E（可选）关键流。
- 流程：先写测试(RED) → 实现(GREEN) → 重构，每阶段配 code-review / security-review。

## 8. 分阶段路线图

| 阶段 | 内容 | 产出 |
|---|---|---|
| **P0 脚手架** | 工程/模块、Hilt、Room+SQLCipher、主题、CI、选型定版 | 可运行空壳 + 基础设施 |
| **P1 多用户与安全** | 注册/登录、加密库、用户切换、（可选）生物识别 | 认证闭环 |
| **P2 资产 CRUD** | 全类别增删改、列表/详情/表单、本地计算（计息/分类/负债、房产手动估值） | 离线可用的资产管理 |
| **P3 行情 + 多币种** | Remote API、SyncManager、WorkManager 每日更新、汇率换算、70 城房价指数 | 每日自动更新价格与指数 |
| **P4 总览** | 净值、走势线图、分布饼图、每日快照 | 资产全景可视化 |
| **P5 提醒引擎** | 持仓/新股/市场/国际规则 + 通知 + 阈值设置 | 提醒中心上线 |
| **P6 备份/恢复** | 加密导出/导入、每周自动 | 数据可迁移 |
| **P7 打磨** | 深色模式、无障碍、性能、E2E、覆盖率达标 | 发布候选 |
| _（后续可选）_ | 房产小区均价自动抓取（Cloudflare Workers 代理） | 房产半自动估值 |

## 9. 待确认的技术决策

| 决策 | 默认（推荐） | 备选 | 状态 |
|---|---|---|---|
| 房产估值 | 手动 + 70 城指数（v1 无服务器） | 后续加代理自动抓价 | ✅ 已定（方案 A） |
| 最低 Android 版本 | **minSdk 29（Android 10）** | — | ✅ 已定 |
| 生物识别登录 | **需要**（指纹/人脸，密码后备） | — | ✅ 已定 |
| DI / 图表 / 加密 | Hilt / Vico / SQLCipher | 可覆盖 | 默认 |
| 代码托管 | **初始化 git，feature 分支开发** | — | ✅ 已定 |

## 10. 风险与缓解

- **免费行情接口变动** → 多源（新浪+腾讯）+ 缓存 + 断网回退上次值。
- **房产精度** → 手动估值 + 70 城指数兜底；（后续可选代理抓小区均价）。
- **合规** → v1 不抓中介数据；行情接口仅个人使用、不转售、不二次分发。
- **数据安全** → 整库加密 + 备份口令派生密钥 + 数据不出本机（v1 无服务器）。

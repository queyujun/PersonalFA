# 盈睿伴 · 进度与续接说明（STATUS）

> 给「下一次会话」看的交接文档。所有代码已提交进 git `main`；此文件随代码版本化。

## 一句话现状
个人金融资产管理 Android App，P0–P6 + 新股(IPO) + 净值走势详情(含自定义组合) + 资产页分组增强(含折叠状态保留) + 负债自动还款 + 设置中心 + 房产指数自动估算 + 多语言国际化(中/英/繁) + 实物金/其他/场外基金三类资产 + 场外基金净值在线抓取 + 加密主备容灾(CoinGecko→OKX 备路) + 应用锁(切回重新认证) + **AI 助手（资产报告 + 持仓分析：Responses 双协议 / 历史记录 / Markdown 渲染增强 / 备份纳入 / SSE 流式实时输出）** + **订阅管理（底部第 5 Tab）** 全部完成，**488 个单元测试全绿**，最新 debug APK 已产出（`apk/RICHWIN-AI流式输出-20260907.apk`）。**AI 流式输出改动已本地提交、未推送**（用户自行 push）。

## 本机构建（关键：无系统级 JDK/SDK，用仓库外 portable 工具链）
```bash
source /c/AIProjects/Claude/PersonalFA/.toolchain/env.sh   # 载入 JDK17/AndroidSDK/Gradle
cd /c/AIProjects/Claude/PersonalFA-1
./gradlew :app:assembleDebug testDebugUnitTest --console=plain
```
- **联网操作（下载依赖/首次构建/curl 探接口）必须关闭沙箱**（Bash `dangerouslyDisableSandbox: true`），否则网络被拦（退出码 137）。
- `.toolchain/`、`build/`、`local.properties`、`*.apk`、`.claude/settings.local.json` 均已在 `.gitignore`。
- APK 产物：`app/build/outputs/apk/debug/app-debug.apk`；手动复制到 `apk/RICHWIN-<功能>-<yyyymmdd>.apk` 交用户真机验证。
- **长构建（assembleDebug+test 正常约 2–5 分钟，冷启动 Daemon 约 10 分钟）建议走后台**（Bash `run_in_background: true`，轮询读输出文件）——前台撞上 10 分钟超时被 kill 会残留 gradle test 进程、锁住 `build/test-results/.../output.bin`，导致下次构建卡十几分钟并因文件锁 `IOException` 失败。遇此：`./gradlew --stop` + `rm -rf app/build/test-results` 后重构即恢复。
- 测试结果统计：解析 `app/build/test-results/testDebugUnitTest/TEST-*.xml` 的 tests/failures/errors 属性（当前 488 个全绿）。

## 技术栈 / 结构
Kotlin2.0 · Compose(Material3) · Hilt · Room+SQLCipher(整库加密) · WorkManager · OkHttp+kotlinx.serialization · 图表 Canvas 自绘。
分层：`ui`(Compose+ViewModel) / `domain`(model+usecase+repository接口) / `data`(local Room + remote + repository实现 + sync + backup)。minSdk 29 / target 35。

## 已完成（对照原始需求 a–e）
- a. 全类别资产 CRUD：房产/存款/A股/港股/美股/账户现金/黄金ETF/国债ETF/公司股权/区块链/负债 ✅
- b. 多币种 ¥/HK$/US$ 切换 ✅
- c. 每日自动取价 + 净值 + 走势线图 + 分布饼图（+ 走势详情子页：日/月/年、多选类别、缩放、横屏、万元）✅
- d. 提醒：新股(东财申购日历)/持仓到期/持仓波动/市场指数 ✅；**国际财经 ⏸️（无稳定免费源，未做）**
- e. 免费接口 + 省流量：新浪/腾讯行情、CoinGecko、新浪汇率、东财新股；每日一次批量 ✅
- 多用户 · 本地加密库 · 生物识别 · 加密备份/恢复 + 每周自动备份 ✅
- 资产页展示：类目/子类目**两级折叠** · 股票分 6 市场子类目（美股/美股现金/A股/A股现金/港股/港股现金；账户现金按币种自动归类，无 DB 改动）· 组内按默认币种**换算后市值降序** · 每类**背景色**区分（与走势图同色）✅
- 负债自动还款：可设**每月还款本金 + 每月还款日**（留空=每月最后一天）；每日同步时按上次扣款年月**补扣所有错过月份**、从欠款本金扣减、**扣到 0 结清**，自动重算净值（逻辑在纯函数 `LiabilityRepayment`）✅
- 设置中心：①顶部 ⚙️ 入口 · 个人资料（昵称/性别/年龄）· 默认货币切换 · 改用户名（查重）/改密码（旧密码校验）✅　②首字母头像 · 指纹登录开关（DataStore，控制登录页指纹按钮）✅　③行情/备份「周期(天) + 整点时间」可配、启动 KEEP 变更 REPLACE、提醒跟随行情（`ScheduleTime` 纯函数算首次延迟；WorkManager 尽力而为，非精确闹钟）✅
- UI 优化：顶栏 `CenterAlignedTopAppBar`「设置(左上) · 标题居中 · 立即刷新(右上，含 Snackbar)」（`RootViewModel`）；资产页股票 6 子分类标题各显示合计金额（`PortfolioSubGroup.totalText`）✅
- 资产页折叠状态保留：一级分类 + 股票二级子类目的展开/折叠状态**跨页面切换保留**。进程级 `@Singleton PortfolioCollapseStore`（`StateFlow<Set<String>>`，集合中存在=展开）；初始空集→**首次进入资产页全折叠**，用户展开过的加入集合，再次进入恢复上次状态。不落盘，冷启动回全折叠。`PortfolioViewModel` 注入并暴露 toggle，`PortfolioScreen` 改为消费 `expandedCategories`/`expandedSubGroups`（正向 expanded 语义）。✅
- 房产按 70 城二手住宅指数自动估算：用户可为房产开启「按 70 城二手住宅价格指数自动估算」，App 每月抓取指数、以录入值为基准累乘环比因子得估算现值，反映到净值/快照/资产页。全程本地、零服务器。数据源东方财富 `datacenter-web`（`RPT_ECONOMY_HOUSE_PRICE`，`SECOND_HOUSE_SEQUENTIAL` 二手环比，filter 一次多城，带 6 天新鲜度跳过）。**不 mutate manualValue**（基准始终是录入值）；估算值存新字段 `estimatedValue`（sync 派生写回，与 `currentPrice` 同构）；`valueBaseDateEpochMs` 记录基准月；纯函数 `RealEstateEstimator.estimate()` 累乘 `secondSequential/100`。表单「所在城市」改 70 城可搜索下拉；详情页展示估算值/基准/累计调整。新增 `HousePriceRemote/Parser/Repository/Dao/Entity/Cities/Estimator`。✅
- 净值走势「自定义组合」走势线：除总净值与各类资产独立走势外，新增「自定义组合」——用户多选若干资产类别，系统按日合计其总值显示为一条线。复用现有每日分类快照 `category_snapshots`，**无需改 DB、立即有完整历史**。`TrendSeriesBuilder` 纯函数扩展：新增 `TREND_CUSTOM_ID="CUSTOM"` + 可选参数 `customCategories`，对该 id 把多类别同 epochDay 的 amount 求和（缺失类别按 0 不贡献）；`TrendDetailScreen` 加「自定义组合」chip，勾选后 `AnimatedVisibility` 展开二级多选器（已选 chip 可点取消、「+ 类别」下拉追加、「全选/清空」快捷），**排除负债**（负值混入合计会误导）。新增主题色 `CustomCombo`（紫红 #8E24AA）。✅
- **多语言国际化（i18n）**：新增 `StringResolver`/`AppLanguage`/`LanguageStore`，per-app locale 经 `AppCompatActivity` + `LocaleListCompat` 应用（跟随系统或手动切换：简中/繁中/英文）。资产类型/币种/市场指数/导航项/时间粒度显示文案全部键化（`displayName`→`displayRes`）；校验文案与渲染解耦（`ValidationFailure` 取代裸 `String`）。默认 `values/strings.xml` 改英文回退，新增 `values-en`/`values-zh-rCN`/`values-zh-rHK`。主题 parent 改 `Theme.AppCompat.DayNight.NoActionBar`，新增 `appcompat` 依赖。✅
- **设置页拆分**：`SettingsScreen` 重构为分组卡片 + 语言选择对话框；新增 `ProfileEdit`/`SyncDetail`/`BackupDetail` 子页（二级页共享 `SettingsViewModel`）；支持切换语言、清理历史快照。✅
- **金额统一格式化**：新增 `MoneyText`（等宽字体对齐）+ `MoneyFormat` 多重载（带币种符号），概览/走势详情/资产明细/饼图统一接入。✅
- **资产类型扩展**：新增 `PHYSICAL_GOLD`（实物金，新浪 `hf_XAU` 现货价按盎司/克换算）、`MISC`（其他，带 `note` 备注）、`OTC_FUND`（场外基金，带子分类）。`Holding`/`HoldingEntity`/`AppDatabase`(v9→11) 同步加字段，备份导入导出贯通。✅
- **场外基金净值在线抓取**：新增 `FundQuoteRemote` + 东方财富 `fundgz` JSONP 实现（`FundNavParser`：`gsz`/`dwjz` 字符串接收、`toDoubleOrNull` 容错，优先估算净值回退官方净值）。`QuoteRepositoryImpl` 仅对 `autoFetchNav=true` 的「中国大陆」持仓在线抓取净值写回；「其他」子分类保持手录。表单提供「中国大陆/其他」筛选标签（切换时清空手录净值）；统计页两类合并显示，仅详情页区分。✅
- **历史快照清理 + 备份汇率**：`SnapshotRepository.deleteBefore` + DAO `deleteOlderThan` 支持清理旧快照；`FxRepository.save` + 备份导出/导入汇率；`UserRepository.observeUser` 用于恢复后币种符号刷新。✅
- **加密主备容灾**：CoinGecko `/simple/price` 为加密货币主数据源，新增 OKX 备路（`OkxRemote` + `OkxParser` + `OkxCoinMap`），CoinGecko 失败/空时自动回退到 OKX `/api/v5/market/index-tickers`，`FallbackCryptoRemote` 串联两源取首个有效结果。✅
- **应用锁（切回重新认证）**：应用从后台切回前台（`ProcessLifecycleOwner` 的 ON_STOP）或冷启动时，在当前页之上盖一层锁定遮罩，需密码或指纹解锁后回到之前正在看的页面，不丢失浏览位置与导航状态（类似银行/支付宝）。新增 `AppLockManager`（`@Singleton`，进程级 `isLocked` 真相源）、`AppLockViewModel`（密码解锁复用 `UserRepository.login` PBKDF2 校验，指纹解锁调 `unlockByBiometric`）、`LockScreen` Composable。设置页「账号与安全」分组加「应用锁」开关（`SyncStateStore.appLockEnabled`，默认启用）；关闭后切回不锁。新增 `lifecycle-process` 依赖 + `@ApplicationScope` CoroutineScope（`AppCoroutineModule`）。冷启动无闪烁：`isLocked` 初始 true，`AppRoot` 的 `sessionState` 初始 Loading 显示空 `Box`。✅
- **AI 助手（资产报告 + 持仓分析）**：OpenAI 兼容**双协议**（Chat Completions / Responses），服务商预设 DeepSeek/OpenAI/Kimi/Qwen/腾讯混元/豆包/自定义（选中回填默认 baseUrl/model，可手改；豆包 model 需填方舟模型 ID 或接入点 ep-xxx）。实现要点：
  - 基建：`AiSecretStore`（Keystore AES/GCM，alias `pfa_ai_key`、文件 `ai_key.bin`，仿 DatabaseKeyProvider）+ `AiSettingsStore`（DataStore "ai_settings"，key 只进 SecretStore；protocol id 存字符串，`AiApiProtocol.fromId` 反查未知回退 CHAT_COMPLETIONS）+ `AiChatParser`（Chat Completions 解 choices[0].message.content；Responses 解 output_text/output 数组，errorBody **优先中文 message_zh**）+ `OpenAiCompatRemote`（双协议路径 `/chat/completions` vs `/responses`；`@AiClient` OkHttp，connect 15s/read 120s/callTimeout 180s；逐状态码显式映射 401/429/5xx，CancellationException rethrow）+ `PortfolioPayloadBuilder`（脱敏白名单：只发 category/type/name/currency/quantity/cost/price/value/plPct 等；**绝不外发 userId/username/note/时间戳**；20k 字符预算超限降级为分类汇总）+ `AiPromptBuilder`（报告模板 7 节 / 分析模板 6 节，双语，禁表格禁代码围栏，规则引导关键数字加粗）+ `AiAssistant`（串 session→holdings→fx→snapshot 趋势→payload→prompt→complete，按协议出请求）。
  - 设置页：`settings_ai` 二级页（`AiSettingsViewModel` 独立，key 明文不进 state 只有尾号掩码；https 校验；**协议选择**（FlowRow 双 chip）；测试连接；**错误详情直显**服务商原始 message）。
  - 报告页：`ai_report`（Idle/Loading/Done/Error 四态 + 隐私同意对话框 + SAF 导出 Markdown `CreateDocument("text/markdown")`）+ `AiMarkdownText` 轻量渲染（H1-H3/Bullet/Numbered/Quote/**表格**/段落合并，`####`+ 降级剥净 `#`，行内 `**加粗**`）+ Overview 入口卡。
  - 分析页：`ai_insight`（同骨架 + 可选追问输入，无导出）+ Overview 双卡入口。
  - **历史记录**：生成成功自动保存 `ai_report_records`（命名「报告/分析 yyyy-MM-dd」），`ai_report`/`ai_insight` 页内底部「历史」区按日期从新到旧列出，可点回看、左滑/按钮删除（带确认对话框）。DB v12→13（破坏性迁移）。VM `flatMapLatest` 订阅历史流；删除经 pendingDelete 确认 + deletedHint Snackbar。
  - **SSE 流式实时输出（2026-09-08）**：报告/分析生成改为边到边渲染，不再等全文。`AiRemote.stream()`（SSE 逐行 `readUtf8Line`，`invokeOnCompletion{call.cancel()}` 支持取消中止阻塞读，`flowOn(IO)`）→ `AiStreamEvent`（Delta/Model/Completed/Failed）→ `AiAssistant.reportStream()/insightStream()` → VM `Generating` 渐进更新 markdown → `AiMarkdownText` 实时渲染 + **自动跟随滚动**（距底 <200dp 才跟随，手动上滑回看不抢滚动）。`AiChatParser.parseSseData`：Chat 协议取 `delta.content`（**过滤 `reasoning_content` 思考过程**），Responses 协议按 `type` 分派（`*.done` 收尾事件带全文**不重复追加**），流内 error 事件转 BAD_REQUEST 中文提示；单条 data 行 TCP 截断→JSON 容错跳过；`[DONE]` 哨兵忽略；空正文流→EMPTY_RESPONSE。错误映射与一次性补全一致（401/429/5xx/超时/网络断）。AI 设置页「测试连接」仍走 complete() 不变。
  - **备份纳入**：`BackupData` 增 `aiRecords`（全量导出导入）+ `aiSettings`（provider/baseUrl/model/protocol/includeDetails/consented）；**API Key 明确不进备份**（Keystore 换机恢复失效，新机重录）——测试断言备份明文不含 key。老备份无新字段→空列表/null 不覆盖本机（向后兼容）。恢复后 consented 随行免重复隐私弹窗。
  - 隐私硬约束：API Key 只进 Authorization 头不进 prompt；报告内容不落盘不进日志（无 logging interceptor）；导出走 SAF 由用户自选位置。
- **订阅管理（底部第 5 Tab）**：三批实现（数据层 DB v12 → UI 三件套 → 测试+构建）。
  - 数据层：`SubscriptionEntity`/`SubscriptionDao`（DB v11→12 破坏性迁移）+ `Subscription`/`SubscriptionCategory`(VIDEO/MUSIC/AI/SOFTWARE/CLOUD/NEWS/OTHER)/`BillingCycle`(WEEKLY/MONTHLY/QUARTERLY/YEARLY，`monthlyFactor` 折算) + `SubscriptionRepository`/Impl；续费顺延纯函数 `SubscriptionRenewal`（月末钳制锚定顺延结果不漂移，如 1/31→2/28 后续停在 28；跨年多周期一次顺延到第一个未来日期，不重复计费）；成本汇总纯函数 `SubscriptionCost`（inactive 剔除、按展示币种换算、byCategory 降序 0 排除）。
  - UI 三件套：`SubscriptionScreen`（汇总卡月均+年化 + 筛选 ALL/ACTIVE/INACTIVE + 即将续费 Top3 + 全部列表）→ `SubscriptionFormScreen`（新建/编辑，周期/币种/提醒提前天数，保存前校验金额>0/日期合法；停用开关）→ 详情入口复用编辑页。
  - 行内币种语义：**行内金额按订阅自身计费币种展示（不换汇）**；仅汇总卡换算到用户默认展示币种（修复「USD 订阅显示 ¥」错配；`SubscriptionViewModel` 经 `flatMapLatest` 对 defaultCurrency 响应式刷新）。
  - 备份兼容：`BackupData`/`BackupManager` 增订阅表导出导入；到期提醒复用 `AlertRules`。
  - 品牌图：`drawable/logo_title.png`（+zh-rCN/zh-rHK 语言限定变体），`PersonalFaRoot` 顶栏展示。

## 数据源关键事实（详见 design/data-sources.md）
- 新浪行情 `hq.sinajs.cn`：需 `Referer: https://finance.sina.com.cn` + **GBK 解码**；现价字段位：A股/ETF 第3、港股第6、美股第1；汇率第8。
- 指数：沪深300 `sh000300`、恒生 `rt_hkHSI`、标普 `gb_$inx`。
- CoinGecko `/simple/price` 目前免费直连可用（未来可能需免费 Demo Key）。
- 新股：东财 `datacenter-web.eastmoney.com/api/data/v1/get?reportName=RPTA_APP_IPOAPPLY`（注意报表名会变）。
- 房产：手动估值 + **已实现按 70 城二手住宅价格指数自动估算**（东方财富 `datacenter-web`，`RPT_ECONOMY_HOUSE_PRICE`，`SECOND_HOUSE_SEQUENTIAL` 二手环比，filter 多城批量请求，pageSize=500；带 6 天新鲜度缓存到 `HousePriceIndexEntity`）。表单「所在城市」70 城可搜索下拉，开启估算后 sync 派生 `estimatedValue`。

## 注意事项
- **DB 当前 version = 13**（v12 订阅表，v13 AI 历史记录表）；开发期用 `fallbackToDestructiveMigration`，**每次升 schema 覆盖安装会重置本地数据**（需重新注册）。发布前需写正式迁移。
- 订阅续费日展示走 `SubscriptionRenewal.displayRenewal`（只读顺延，不动库），真实推进在每日 sync 的 `advance`。
- 分类走势历史从 v6 起每日累积；当天仅 1 点显示「数据积累中」。
- 资产页「账户现金」按币种映射到股票子类目：¥→A股现金、HK$→港股现金、US$→美股现金（纯展示归类，录入仍为「账户现金」+ 选币种；组织逻辑在 `PortfolioSectionsBuilder`）。
- 净值走势「自定义组合」：选中 CUSTOM 但未勾任何类别时，该序列全 null（时间轴靠 totals 提供），图上不显示线——属正常空态。
- **多语言**：默认 `values/strings.xml` 为英文回退，未设 locale 的设备显示英文；中文落在 `values-zh-rCN`/`values-zh-rHK`。旧 OTC 持仓无 `autoFetchNav` 字段→视为手录，需手动补标为「中国大陆」才走在线抓取。
- **场外基金净值端点**：东方财富 `https://fundgz.1234567.com.cn/js/{code}.js` 的 `gsz`/`dwjz` 字段语义、https 可达性须在设备上联网验证；项目未配 `network_security_config.xml`，已统一用 https 适配默认禁明文。
- **应用锁**：每次从后台切回都锁（不区分停留时长，最高安全性）。锁定遮罩仅出现在 `LoggedIn` 态；未登录/冷启动无 `current_user_id` 时 `AppRoot` 走 `LoggedOut`→`AuthScreen`，遮罩 collector 把 `isLocked` 重置为 false。指纹解锁不重新校验密码——设备指纹=已登录身份凭证。关闭「应用锁」开关后，切回不锁、冷启动也不锁。

## 待办（可选，用户未定优先级）
1. ~~房产按国家统计局 70 城房价指数自动估算涨跌~~ ✅ 已完成。
2. 国际财经提醒（需自建快讯 RSS + 关键词打分，尽力而为）。
3. P7 打磨：深色模式细化、无障碍、性能、E2E、发布签名与正式 DB 迁移（当前 v13 均用破坏性迁移，发布前必修正式 6→13 迁移）。
4. ~~根目录 `settings.json` 含 `ANTHROPIC_AUTH_TOKEN` 等密钥，未入 `.gitignore`~~ ✅ 已补进 `.gitignore`（`settings-ds/glm/xzprj.json` + 预览 html），均未入库。
5. **AI 流式输出已上线**（2026-09-08）；若后续再探 SSE 接口，探针脚本（`probe_*.py`，已被 gitignore）与输出 txt（**未被 gitignore**，用完即删，勿入库）放在仓库根目录，用完即删。

## git
- 分支 `main` 含全部；特性分支按 `feature/*` 开发后 `--no-ff` 合并。
- 提交信息用中文 `<type>: <desc>`，**不加署名尾注**（用户全局禁用 attribution）。
- **上一批四项改动已本地提交**（截至 2026-09-01，主分支 `main`）：
  - `5c0d87d` feat: 应用锁 — 切回应用需重新认证（密码/指纹）
  - `0c98e05` refactor: 资产分类配色调整为浅色调九色色相错开
  - `403995d` feat: 房价指数同步反馈明示 — 同步弹窗展示「已更新至 X 月 / 已是最新」
  - `645eddd` feat: 加密货币主备容灾 — CoinGecko 主路失败自动回退 OKX 备路
  - 更早：`4336816`（4 套高端配色+改名 RICHWIN）/ `ffb84bb`（三主题配色）
  - `.gitignore` 已收紧为 `settings-*.json` / `*-preview.html` 通配，覆盖所有根目录密钥文件与预览产物（均未入库）。
- **AI 助手 + 订阅管理已一并本地提交（2026-09-03，主分支 `main`）**，未推送。含订阅管理三批、AI 助手四批、行内币种修复、品牌 logo 资源。
- **2026-09-04 五项改动分 5 个 commit 本地提交（主分支 `main`，未推送）**：
  - `b945ad1` feat: OpenAI Responses 双协议支持（TokenHub hy3）+ 设置页协议切换与错误详情直显
  - `0999ccc` feat: AI 历史记录自动保存/列表/删除 + 备份纳入 AI 记录与设置（API Key 除外）
  - `c81bbdd` feat: AI 报告 Markdown 渲染增强（表格/粗体/引用）+ 提示词引导关键数字加粗
  - `4d9dc96` fix: 趋势明细页类别按最新一天金额从大到小排序
  - `6be7fa2` chore: gitignore 增加本机探针脚本（testhunyuan.py / probe_*.py）
- **2026-09-07 两项改动本地提交（主分支 `main`，未推送）**：
  - `c7b52b1` feat: AI 历史记录条目可点击打开浏览 — 回放该次生成内容（报告页回放后可导出）
  - `be76745` docs: 项目根新增 CLAUDE.md — 固化协作节奏/构建方式/隐私与密钥约束
  - 471 个单元测试全绿；APK：`apk/RICHWIN-历史记录点击查看-20260904.apk`
- **2026-09-08 AI 流式实时输出本地提交（主分支 `main`，未推送）**：
  - `fdf356c` feat: AI 报告/分析改为 SSE 流式实时输出 — 生成中逐段渲染+自动跟随滚动
  - 488 个单元测试全绿；APK：`apk/RICHWIN-AI流式输出-20260907.apk`（用户已真机验证通过）
- **用户未推送**：提交链均未 `git push`，用户自行 push，不替用户推送。

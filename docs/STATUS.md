# 盈景私助 · 进度与续接说明（STATUS）

> 给「下一次会话」看的交接文档。所有代码已提交进 git `main`；此文件随代码版本化。

## 一句话现状
个人金融资产管理 Android App，P0–P6 + 新股(IPO) + 净值走势详情(含自定义组合) + 资产页分组增强(含折叠状态保留) + 负债自动还款 + 设置中心 + 房产指数自动估算 全部完成并合并入 `main`，**207 个单元测试全绿**，最新 debug APK 已产出。**三批最新改动已本地提交、未推送**（见 git 节）。

## 本机构建（关键：无系统级 JDK/SDK，用仓库内 portable 工具链）
```bash
source /c/AIProjects/Claude/PersonalFA/.toolchain/env.sh   # 载入 JDK17/AndroidSDK/Gradle
cd /c/AIProjects/Claude/PersonalFA
./gradlew :app:assembleDebug testDebugUnitTest --console=plain
```
- **联网操作（下载依赖/首次构建/curl 探接口）必须关闭沙箱**（Bash `dangerouslyDisableSandbox: true`），否则网络被拦（退出码 137）。
- `.toolchain/`、`build/`、`local.properties`、`*.apk`、`.claude/settings.local.json` 均已在 `.gitignore`。
- APK 产物：`app/build/outputs/apk/debug/app-debug.apk`；手动复制到项目根目录 `PersonalFA-<功能>-<日期时间>.apk`（根目录只保留最新一个，旧的删掉避免装错）。
- **长构建（assembleDebug+test 正常约 2–5 分钟）建议走后台**（Bash `run_in_background: true`，输出重定向到 log 后 tail）——前台撞上 10 分钟超时被 kill 会残留 gradle test 进程、锁住 `build/test-results/.../output.bin`，导致下次构建卡十几分钟并因文件锁 `IOException` 失败。遇此：`./gradlew --stop` + `rm -rf app/build/test-results` 后重构即恢复。

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

## 数据源关键事实（详见 design/data-sources.md）
- 新浪行情 `hq.sinajs.cn`：需 `Referer: https://finance.sina.com.cn` + **GBK 解码**；现价字段位：A股/ETF 第3、港股第6、美股第1；汇率第8。
- 指数：沪深300 `sh000300`、恒生 `rt_hkHSI`、标普 `gb_$inx`。
- CoinGecko `/simple/price` 目前免费直连可用（未来可能需免费 Demo Key）。
- 新股：东财 `datacenter-web.eastmoney.com/api/data/v1/get?reportName=RPTA_APP_IPOAPPLY`（注意报表名会变）。
- 房产：手动估值 + **已实现按 70 城二手住宅价格指数自动估算**（东方财富 `datacenter-web`，`RPT_ECONOMY_HOUSE_PRICE`，`SECOND_HOUSE_SEQUENTIAL` 二手环比，filter 多城批量请求，pageSize=500；带 6 天新鲜度缓存到 `HousePriceIndexEntity`）。表单「所在城市」70 城可搜索下拉，开启估算后 sync 派生 `estimatedValue`。

## 注意事项
- **DB 当前 version = 9**；开发期用 `fallbackToDestructiveMigration`，**每次升 schema 覆盖安装会重置本地数据**（需重新注册）。发布前需写正式迁移。
- 分类走势历史从 v6 起每日累积；当天仅 1 点显示「数据积累中」。
- 资产页「账户现金」按币种映射到股票子类目：¥→A股现金、HK$→港股现金、US$→美股现金（纯展示归类，录入仍为「账户现金」+ 选币种；组织逻辑在 `PortfolioSectionsBuilder`）。
- 净值走势「自定义组合」：选中 CUSTOM 但未勾任何类别时，该序列全 null（时间轴靠 totals 提供），图上不显示线——属正常空态。

## 待办（可选，用户未定优先级）
1. ~~房产按国家统计局 70 城房价指数自动估算涨跌~~ ✅ 已完成。
2. 国际财经提醒（需自建快讯 RSS + 关键词打分，尽力而为）。
3. P7 打磨：深色模式细化、无障碍、性能、E2E、发布签名与正式 DB 迁移。
4. 根目录 `settings.json` 含 `ANTHROPIC_AUTH_TOKEN` 等密钥，未入 `.gitignore`（当前 untracked 未提交）——**需加进 `.gitignore` 或移到 `.claude/` 下，避免误推泄露 token**。

## git
- 分支 `main` 含全部；特性分支按 `feature/*` 开发后 `--no-ff` 合并。
- 提交信息用中文 `<type>: <desc>`，**不加署名尾注**（用户全局禁用 attribution）。
- **最近三批改动已本地提交、未推送**（截至 2026-08-27）：
  - `47e8873` feat: 净值走势页增加自定义组合走势线
  - `09a4ef7` feat: 资产页分类折叠状态跨页面切换保留
  - `adce85f` feat: 房产按 70 城二手住宅指数自动估算涨跌
  - 用户未明确要求推送，下一次会话需确认是否 `git push`。

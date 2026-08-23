# 盈景私助 · 进度与续接说明（STATUS）

> 给「下一次会话」看的交接文档。所有代码已提交进 git `main`；此文件随代码版本化。

## 一句话现状
个人金融资产管理 Android App，P0–P6 + 新股(IPO) + 净值走势详情 全部完成并合并入 `main`，**145 个单元测试全绿**，最新 debug APK 已产出。

## 本机构建（关键：无系统级 JDK/SDK，用仓库内 portable 工具链）
```bash
source /c/AIProjects/Claude/PersonalFA/.toolchain/env.sh   # 载入 JDK17/AndroidSDK/Gradle
cd /c/AIProjects/Claude/PersonalFA
./gradlew :app:assembleDebug testDebugUnitTest --console=plain
```
- **联网操作（下载依赖/首次构建/curl 探接口）必须关闭沙箱**（Bash `dangerouslyDisableSandbox: true`），否则网络被拦（退出码 137）。
- `.toolchain/`、`build/`、`local.properties`、`*.apk`、`.claude/settings.local.json` 均已在 `.gitignore`。
- APK 产物：`app/build/outputs/apk/debug/app-debug.apk`；会复制一份到项目根目录 `PersonalFA-v0.1.0-*.apk`。

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

## 数据源关键事实（详见 design/data-sources.md）
- 新浪行情 `hq.sinajs.cn`：需 `Referer: https://finance.sina.com.cn` + **GBK 解码**；现价字段位：A股/ETF 第3、港股第6、美股第1；汇率第8。
- 指数：沪深300 `sh000300`、恒生 `rt_hkHSI`、标普 `gb_$inx`。
- CoinGecko `/simple/price` 目前免费直连可用（未来可能需免费 Demo Key）。
- 新股：东财 `datacenter-web.eastmoney.com/api/data/v1/get?reportName=RPTA_APP_IPOAPPLY`（注意报表名会变）。
- 房产：**手动估值**；「按 70 城指数自动估算」表单开关已在，但**指数抓取/应用尚未实现**。

## 注意事项
- **DB 当前 version = 6**；开发期用 `fallbackToDestructiveMigration`，**每次升 schema 覆盖安装会重置本地数据**（需重新注册）。发布前需写正式迁移。
- 分类走势历史从 v6 起每日累积；当天仅 1 点显示「数据积累中」。

## 待办（可选，用户未定优先级）
1. 房产按国家统计局 70 城房价指数自动估算涨跌（抓取 + 应用到 manualValue）。
2. 国际财经提醒（需自建快讯 RSS + 关键词打分，尽力而为）。
3. P7 打磨：深色模式细化、无障碍、性能、E2E、发布签名与正式 DB 迁移。

## git
- 分支 `main` 含全部；特性分支按 `feature/*` 开发后 `--no-ff` 合并。
- 提交信息用中文 `<type>: <desc>`，**不加署名尾注**（用户全局禁用 attribution）。

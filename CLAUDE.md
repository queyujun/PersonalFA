# RICHWIN（盈睿伴）· AI 协作约束

> 详细进度与续接说明见 `docs/STATUS.md`——每次会话开始先读它。

## 项目

个人金融资产管理 Android App。包名 `com.yingjing.pfa`。
Kotlin 2.0 · Compose(M3) · Hilt · Room+SQLCipher · WorkManager · OkHttp+kotlinx.serialization。

## 协作节奏（必须遵守）

1. **分批实现** → **出 APK 交用户真机验证** → **用户认可后才提交**。
2. 提交信息：中文 `<type>: <desc>`（feat/fix/refactor/docs/test/chore）。
3. **只 commit 不 push**——用户自行推送。
4. **不加署名尾注**（无 Co-Authored-By）。

## 构建（本机无系统级 JDK/SDK，用仓库外 portable 工具链）

```bash
source /c/AIProjects/Claude/PersonalFA/.toolchain/env.sh
cd /c/AIProjects/Claude/PersonalFA-1
./gradlew :app:assembleDebug testDebugUnitTest --console=plain
```

- **联网操作（下载依赖/探接口）必须关沙箱**（Bash `dangerouslyDisableSandbox: true`）。
- 长构建走后台（`run_in_background: true`），前台 10 分钟超时被杀会锁 test-results。
- APK 产物 `app/build/outputs/apk/debug/app-debug.apk` → 复制为 `apk/RICHWIN-<功能>-<yyyymmdd>.apk` 交用户。

## 隐私硬约束（AI 助手相关，永不违反）

- 对外发送（LLM prompt）只允许白名单字段：category/type/name/currency/quantity/costPrice/currentPrice/value/plPct。
- **绝不外发**：userId/username/nickname/gender/age/note（整字段剔除）/时间戳。
- **API Key 永不进 prompt**（只进 Authorization 头）；**永不进备份**、**永不进日志**（无 OkHttp logging interceptor）。
- baseUrl 必须 https://。

## 密钥文件（已被 .gitignore 覆盖，严禁入库）

`testhunyuan.py`（真实 key）、`settings-*.json`（含 token）、`probe_*.py`。

## 数据库

- Room 当前 version 见 `docs/STATUS.md`；开发期 `fallbackToDestructiveMigration`（升 schema 覆盖安装会清数据）。发布前需写正式迁移。

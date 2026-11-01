<div align="center">

<img src="art/icon.svg" width="128" height="128" alt="MqttABot icon">

# MqttABot

[![CI](https://img.shields.io/github/actions/workflow/status/264768502/mqttabot/ci.yml?branch=main&style=for-the-badge&logo=githubactions&logoColor=white&label=CI)](https://github.com/264768502/mqttabot/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/264768502/mqttabot?include_prereleases&sort=semver&style=for-the-badge&color=1E88E5&logo=github&logoColor=white)](https://github.com/264768502/mqttabot/releases)
[![Coverage](https://img.shields.io/badge/coverage-100%25-43A047?style=for-the-badge&logo=codecov&logoColor=white)](#构建与测试)
[![License: MIT](https://img.shields.io/badge/License-MIT-FFA000?style=for-the-badge&logo=opensourceinitiative&logoColor=white)](LICENSE)
[![Min API](https://img.shields.io/badge/API-24%2B-43A047?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/about/versions/nougat)
[![Target SDK](https://img.shields.io/badge/targetSdk-36%20%28Android%2016%29-00897B?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/about/versions/16)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.21-7E57C2?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Tasker plugin](https://img.shields.io/badge/Tasker-plugin-EF6C00?style=for-the-badge&logo=android&logoColor=white)](https://tasker.joaoapps.com/)
[![Last commit](https://img.shields.io/github/last-commit/264768502/mqttabot?style=for-the-badge&color=6A1B9A&logo=git&logoColor=white)](https://github.com/264768502/mqttabot/commits/main)

</div>

[English](README.md) | **简体中文**

Android MQTT 客户端 + 常驻订阅服务 + Tasker 插件。纯 Kotlin 实现，无 C/C++/JNI 代码。

- 最低支持 Android 7.0（API 24），目标版本 Android 16（API 36），编译 SDK 37
- MQTT 库：Eclipse Paho `mqttv3`（纯 Java）
- 构建：Gradle 9.8 + AGP 9.5（内置 Kotlin）

## 截图

| 主界面 | 消息 | 服务器设置 | Tasker 事件 |
| :---: | :---: | :---: | :---: |
| <img src="fastlane/metadata/android/zh-CN/images/phoneScreenshots/1_main.png" width="200" alt="主界面"> | <img src="fastlane/metadata/android/zh-CN/images/phoneScreenshots/2_messages.png" width="200" alt="消息"> | <img src="fastlane/metadata/android/zh-CN/images/phoneScreenshots/3_broker_settings.png" width="200" alt="服务器设置"> | <img src="fastlane/metadata/android/zh-CN/images/phoneScreenshots/4_tasker_event.png" width="200" alt="Tasker 事件配置"> |

## 功能

| 功能 | 说明 |
| --- | --- |
| 发送消息 | 主界面填写 topic / payload / QoS / retain 后发送。订阅服务运行时复用其长连接，否则建立一次性连接。 |
| 订阅服务 | `MqttService` 前台服务（`specialUse` 类型），常驻通知显示连接状态并提供“停止”按钮；断线自动重连并重新订阅；可设置开机自启。 |
| 内置服务器 | test.mosquitto.org（TCP / TLS / 带认证 1884）、broker.hivemq.com、broker.emqx.io、mqtt.eclipseprojects.io（TCP 与 TLS），也可自定义；支持用户名、密码、Client ID、Keep-alive、Clean session。 |
| Tasker 动作 | “MqttABot: Publish”——在 Tasker 中发送 MQTT 消息，topic 与 payload 支持 Tasker `%变量`。 |
| Tasker 事件 | “MqttABot: Message received”——订阅到的消息作为 Tasker 触发器，支持 topic 通配符（`+`、`#`）和可选的 payload 正则。保存时自动把过滤器加入订阅列表。 |
| 界面 | Material 3 Expressive 设计：Android 12+ 跟随壁纸动态取色（其他版本使用品牌紫色），支持浅色/深色主题、全面屏（edge-to-edge）布局、可折叠顶部栏、QoS 连接按钮组；平板和横屏时内容居中显示。 |
| 多语言 | 英文、简体中文、繁体中文、西班牙文、德文、韩文和日文。自动跟随系统语言，不支持的语言显示英文；Android 13+ 还可以在系统设置中单独为本应用选择语言。 |

### Tasker 使用

1. **发送**：任务 → 添加动作 → 插件 → MqttABot: Publish，填写 topic（如 `home/%room/light`）与 payload（如 `%state`）。
2. **触发**：配置文件 → 事件 → 插件 → MqttABot: Message received，填写过滤器（如 `sensors/+/temperature`）。
   确保 App 中已启动订阅服务。事件触发时可用变量：

   | 变量 | 含义 |
   | --- | --- |
   | `%mqtt_topic` | 消息主题 |
   | `%mqtt_payload` | 消息内容（UTF-8） |
   | `%mqtt_qos` | QoS（0–2） |
   | `%mqtt_retained` | 是否保留消息（true/false） |
   | `%mqtt_received_at` | 接收时间（毫秒时间戳） |

插件实现的是标准 Locale/Tasker 插件协议（`EDIT_SETTING`/`FIRE_SETTING`、`ACTION_EDIT_EVENT`/`REQUEST_QUERY`/`QUERY_CONDITION`），见 `app/src/main/kotlin/com/mqttabot/tasker/`。

## 构建与测试

需要 JDK 17+ 与 Android SDK（`platforms;android-37.0`、`build-tools;37.0.0`），在 `local.properties` 中设置 `sdk.dir`。

```bash
./gradlew assembleDebug                         # 构建 APK
./gradlew check                                 # lint（警告即失败）+ 全部测试 + 100% 覆盖率门禁
./gradlew testDebugUnitTest                     # 全部 JVM 测试（单元 + 端到端）
./gradlew jacocoUnitTestCoverageVerification -PskipE2e   # 只用单元测试验证 100% 覆盖率
./gradlew testDebugUnitTest --tests 'com.mqttabot.e2e.*'  # 只运行端到端测试
./gradlew testDebugUnitTest -Pscreenshots               # 重新生成 fastlane/ 中的截图和图标
```

覆盖率报告：`app/build/reports/jacoco/jacocoUnitTestReport/html/index.html`。门禁要求指令、分支、行、方法、类覆盖率均为 100%（仅排除生成的 `R`/`BuildConfig`）。

### 测试说明

- 单元测试使用 JUnit4 + Robolectric（默认 API 36，`MinSdkCompatTest` 在 API 24 上运行主要流程）+ MockK。
- 端到端测试（`app/src/test/kotlin/com/mqttabot/e2e/`）在进程内启动真实 MQTT broker（Moquette，已排除其原生依赖，纯 Java/NIO），App 内使用真实的 Paho 客户端与网络连接，`FakeTasker` 按 Tasker 的协议驱动插件：
  - `PublishEndToEndTest`：通过 Tasker 编辑界面配置动作 → 变量替换 → `FIRE_SETTING` → broker → 独立订阅者收到消息；以及主界面经订阅服务长连接发送。
  - `SubscribeEndToEndTest`：通过 Tasker 编辑界面配置事件 → 启动前台服务 → 其他客户端发布消息 → `REQUEST_QUERY` → `QUERY_CONDITION` 返回满足条件及变量；验证 broker 断线后自动重连、重新订阅。
- 暂无 `androidTest` 仪器测试；端到端测试在 JVM 上通过 Robolectric 运行，无需模拟器。

## 开发

- **格式化**：Spotless + ktlint（`ktlint_official`，规则见 `.editorconfig`）。`./gradlew spotlessApply` 自动修复，`./gradlew spotlessCheck` 检查。
- **pre-commit**：`pip install pre-commit && pre-commit install`。钩子会运行格式化、Android lint、单元测试与 100% 覆盖率门禁、依赖审计以及基础文件检查；`pre-commit run --all-files` 运行全部钩子。
- **依赖锁定**：所有解析出的版本固定在 `gradle.lockfile` 中。修改依赖后运行 `./gradlew buildEnvironment :app:dependencies --write-locks`。
- **依赖审计**：[OSV-Scanner](https://google.github.io/osv-scanner/) 根据 osv.dev 检查锁文件：`osv-scanner scan source --recursive --config=osv-scanner.toml .`。存在漏洞的构建/测试传递依赖的修复版本固定在 `settings.gradle.kts` 中；经审核的例外记录在 `osv-scanner.toml`。
- **CI**：GitHub Actions（`.github/workflows/ci.yml`）在每次推送到 `main` 及每个 Pull Request 时运行格式检查、lint、测试与覆盖率、依赖审计。

## 版本发布

`.github/workflows/release.yml` 会构建并发布 GitHub Release，附带 `mqttabot-<版本>-release.apk`、`mqttabot-<版本>-debug.apk` 和 `SHA256SUMS.txt`。构建前会运行格式检查、lint、全部测试和覆盖率门禁。两种触发方式：

- **推荐：** 手动运行 **Release** workflow（Actions → Release → Run workflow），填写版本号如 `1.2.0`。workflow 会更新 `app/build.gradle.kts` 中的 `versionName`/`versionCode`，提交到 `main`，创建 tag 并发布。
- 或者自己修改 `app/build.gradle.kts` 中的 `versionName`/`versionCode` 并提交，再推送 tag：`git tag v1.2.0 && git push origin v1.2.0`。tag 与源码中的版本不一致时 workflow 会失败。

版本号保存在源码中（F-Droid 从源码读取）：`versionName` 为去掉 `v` 的 tag，`versionCode` = `主版本 × 1,000,000 + 次版本 × 1,000 + 修订号`。带后缀的版本（如 `1.3.0-beta.1`）会发布为预发布版本。可选：在 `fastlane/metadata/android/<语言>/changelogs/<versionCode>.txt` 中为应用商店添加更新说明。

**签名。** 在仓库 Secrets（Settings → Secrets and variables → Actions）中添加以下内容，即可用自己的密钥签名 release APK：

| Secret | 值 |
| --- | --- |
| `SIGNING_KEYSTORE_BASE64` | base64 编码的 keystore 文件（`base64 -w0 release.jks`） |
| `SIGNING_KEYSTORE_PASSWORD` | keystore 密码 |
| `SIGNING_KEY_ALIAS` | 密钥别名 |
| `SIGNING_KEY_PASSWORD` | 密钥密码（PKCS12 keystore 与 keystore 密码相同） |

未配置时，正式版本会被拒绝发布。预发布版本使用 CI 机器临时生成的 debug 密钥签名，release 说明中会注明；这样的 APK 可以安装，但每次签名都不同，用户在它们之间升级前需要先卸载。

## F-Droid

MqttABot 已提交到官方 [F-Droid](https://f-droid.org) 仓库：[fdroiddata!55280](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/55280)。F-Droid 会根据 tag 对应的源码自行构建。构建是**可复现的**：F-Droid 会检查它的构建结果与已签名的 GitHub release 逐字节一致，然后发布带有我们签名的 APK，因此用户可以在 F-Droid 和 GitHub release 之间切换而无需卸载。

- **商店信息：** `fastlane/metadata/android/<语言>/`（en-US、zh-CN、zh-TW、es-ES、de-DE、ko-KR、ja-JP）—— 标题、简短描述、完整描述和更新说明；en-US 和 zh-CN 还包含图标和截图。F-Droid 会直接从仓库读取。
- **构建元数据：** [`fdroid/com.mqttabot.yml`](fdroid/com.mqttabot.yml)，与提交的文件完全一致。它在发布对应的 commit 上构建 `app`，与 `Binaries`（GitHub release 的 APK）比对并要求其签名属于 `AllowedAPKSigningKeys`，同时使用 `AutoUpdateMode: Version` 和 `UpdateCheckMode: Tags`，收录后每个新的 `vX.Y.Z` tag 都会被自动识别。
- **已用 fdroidserver 验证：** `fdroid lint` 通过，从 GitHub tag 执行的 `fdroid build` 成功，并报告 "compared built binary to supplied reference binary successfully"。

merge request 被接受后，新版本只需按正常流程发布（见[版本发布](#版本发布)）；F-Droid 会识别新的 tag 并在几天内发布。可复现性依赖于相同的构建工具链，因此升级 Gradle、AGP 或 JDK 时，要保持发布 workflow（JDK 21）与 F-Droid 构建一致。

源码中不能引入非自由依赖，也不能使用 Google、Maven Central 和 Gradle 插件门户以外的 Maven 仓库，否则 F-Droid 的扫描器会拒绝构建。

## 说明

- 密码保存在应用私有 SharedPreferences 中，并已排除在备份/设备迁移之外。
- Android 13+ 首次启动服务时会请求通知权限；拒绝后服务照常运行，只是通知不可见。

## 许可证

[MIT](LICENSE)

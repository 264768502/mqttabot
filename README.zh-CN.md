# MqttABot

[English](README.md) | **简体中文**

Android MQTT 客户端 + 常驻订阅服务 + Tasker 插件。纯 Kotlin 实现，无 C/C++/JNI 代码。

- 最低支持 Android 7.0（API 24），目标版本 Android 16（API 36），编译 SDK 37
- MQTT 库：Eclipse Paho `mqttv3`（纯 Java）
- 构建：Gradle 9.8 + AGP 9.5（内置 Kotlin）

## 功能

| 功能 | 说明 |
| --- | --- |
| 发送消息 | 主界面填写 topic / payload / QoS / retain 后发送。订阅服务运行时复用其长连接，否则建立一次性连接。 |
| 订阅服务 | `MqttService` 前台服务（`specialUse` 类型），常驻通知显示连接状态并提供“停止”按钮；断线自动重连并重新订阅；可设置开机自启。 |
| 内置服务器 | test.mosquitto.org（TCP / TLS / 带认证 1884）、broker.hivemq.com、broker.emqx.io、mqtt.eclipseprojects.io（TCP 与 TLS），也可自定义；支持用户名、密码、Client ID、Keep-alive、Clean session。 |
| Tasker 动作 | “MqttABot: Publish”——在 Tasker 中发送 MQTT 消息，topic 与 payload 支持 Tasker `%变量`。 |
| Tasker 事件 | “MqttABot: Message received”——订阅到的消息作为 Tasker 触发器，支持 topic 通配符（`+`、`#`）和可选的 payload 正则。保存时自动把过滤器加入订阅列表。 |

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

## 说明

- 密码保存在应用私有 SharedPreferences 中，并已排除在备份/设备迁移之外。
- Android 13+ 首次启动服务时会请求通知权限；拒绝后服务照常运行，只是通知不可见。

## 许可证

[MIT](LICENSE)

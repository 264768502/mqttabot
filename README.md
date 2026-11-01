# MqttABot

**English** | [简体中文](README.zh-CN.md)

Android MQTT client + persistent subscription service + Tasker plugin. Written entirely in Kotlin, with no C/C++/JNI code.

- Minimum Android 7.0 (API 24), target Android 16 (API 36), compiled against SDK 37
- MQTT library: Eclipse Paho `mqttv3` (pure Java)
- Build: Gradle 9.8 + AGP 9.5 (built-in Kotlin)

## Features

| Feature | Description |
| --- | --- |
| Publish | Enter topic / payload / QoS / retain on the main screen and send. Reuses the subscription service's long-lived connection when it is running, otherwise opens a one-shot connection. |
| Subscription service | `MqttService` foreground service (`specialUse` type) with an ongoing notification showing the connection state and a "Stop" action; reconnects and resubscribes automatically; optional start on boot. |
| Built-in brokers | test.mosquitto.org (TCP / TLS / authenticated 1884), broker.hivemq.com, broker.emqx.io, mqtt.eclipseprojects.io (TCP and TLS), or a custom broker; supports username, password, client ID, keep-alive and clean session. |
| Tasker action | "MqttABot: Publish" — publish MQTT messages from Tasker; topic and payload accept Tasker `%variables`. |
| Tasker event | "MqttABot: Message received" — received messages trigger Tasker profiles, with topic wildcards (`+`, `#`) and an optional payload regex. Saving the event adds its filter to the subscription list. |

### Using with Tasker

1. **Publish**: Task → Add Action → Plugin → MqttABot: Publish, then enter a topic (e.g. `home/%room/light`) and payload (e.g. `%state`).
2. **Trigger**: Profile → Event → Plugin → MqttABot: Message received, then enter a topic filter (e.g. `sensors/+/temperature`).
   Make sure the subscription service is running in the app. Variables available when the event fires:

   | Variable | Meaning |
   | --- | --- |
   | `%mqtt_topic` | Message topic |
   | `%mqtt_payload` | Payload (UTF-8) |
   | `%mqtt_qos` | QoS (0–2) |
   | `%mqtt_retained` | Whether it is a retained message (true/false) |
   | `%mqtt_received_at` | Receive time (epoch milliseconds) |

The plugin implements the standard Locale/Tasker plugin protocol (`EDIT_SETTING`/`FIRE_SETTING`, `ACTION_EDIT_EVENT`/`REQUEST_QUERY`/`QUERY_CONDITION`); see `app/src/main/kotlin/com/mqttabot/tasker/`.

## Build and test

Requires JDK 17+ and the Android SDK (`platforms;android-37.0`, `build-tools;37.0.0`); set `sdk.dir` in `local.properties`.

```bash
./gradlew assembleDebug                         # build the APK
./gradlew check                                 # lint (warnings fail) + all tests + 100% coverage gate
./gradlew testDebugUnitTest                     # all JVM tests (unit + end-to-end)
./gradlew jacocoUnitTestCoverageVerification -PskipE2e   # verify 100% coverage with unit tests only
./gradlew testDebugUnitTest --tests 'com.mqttabot.e2e.*'  # end-to-end tests only
```

Coverage report: `app/build/reports/jacoco/jacocoUnitTestReport/html/index.html`. The gate requires 100% instruction, branch, line, method and class coverage (only generated `R`/`BuildConfig` are excluded).

### About the tests

- Unit tests use JUnit4 + Robolectric (API 36 by default; `MinSdkCompatTest` runs the main flows on API 24) + MockK.
- End-to-end tests (`app/src/test/kotlin/com/mqttabot/e2e/`) start a real MQTT broker in-process (Moquette, with its native dependencies excluded so it runs on pure Java/NIO). The app uses the real Paho client over real sockets, and `FakeTasker` drives the plugin using Tasker's protocol:
  - `PublishEndToEndTest`: configure the action in the Tasker edit screen → variable substitution → `FIRE_SETTING` → broker → an independent subscriber receives the message; also publishing from the main screen over the service's long-lived connection.
  - `SubscribeEndToEndTest`: configure the event in the Tasker edit screen → start the foreground service → another client publishes → `REQUEST_QUERY` → `QUERY_CONDITION` returns "satisfied" with variables; also verifies automatic reconnect and resubscribe after the broker drops the connection.
- There are no `androidTest` instrumentation tests yet; the end-to-end tests run on the JVM under Robolectric, without an emulator.

## Development

- **Formatting**: Spotless + ktlint (`ktlint_official`, rules in `.editorconfig`). `./gradlew spotlessApply` fixes, `./gradlew spotlessCheck` verifies.
- **pre-commit**: `pip install pre-commit && pre-commit install`. Hooks run formatting, Android lint, unit tests with the 100% coverage gate, the dependency audit and basic file checks; `pre-commit run --all-files` runs them all.
- **Dependency locking**: all resolved versions are pinned in `gradle.lockfile`s. After changing dependencies run `./gradlew buildEnvironment :app:dependencies --write-locks`.
- **Dependency audit**: [OSV-Scanner](https://google.github.io/osv-scanner/) checks the lockfiles against osv.dev: `osv-scanner scan source --recursive --config=osv-scanner.toml .`. Patched versions of vulnerable transitive build/test dependencies are pinned in `settings.gradle.kts`; audited exceptions live in `osv-scanner.toml`.
- **CI**: GitHub Actions (`.github/workflows/ci.yml`) runs format, lint, tests + coverage, and the dependency audit on every push to `main` and on pull requests.

## Notes

- Passwords are stored in the app's private SharedPreferences and are excluded from backup and device transfer.
- On Android 13+, starting the service the first time asks for notification permission; if denied, the service still runs, but its notification is hidden.

## License

[MIT](LICENSE)

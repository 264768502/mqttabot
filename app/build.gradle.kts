plugins {
    alias(libs.plugins.android.application)
    jacoco
}

// Release signing key, supplied by CI via environment variables; falls back to the debug key.
val releaseKeystore = providers.environmentVariable("SIGNING_KEYSTORE_PATH").orNull

android {
    namespace = "com.mqttabot"
    // Newest AndroidX needs compileSdk 37; runtime behaviour still targets Android 16 (targetSdk 36).
    compileSdk = 37

    defaultConfig {
        applicationId = "com.mqttabot"
        minSdk = 24
        targetSdk = 36
        // Literal values so F-Droid can read them; the release workflow bumps them and checks
        // them against the tag. versionCode = major * 1_000_000 + minor * 1_000 + patch.
        versionCode = 1000000
        versionName = "1.0.0"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = providers.environmentVariable("SIGNING_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("SIGNING_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("SIGNING_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        debug {
            enableUnitTestCoverage = true
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    // Lists the translated locales for Android 13+ per-app language settings.
    androidResources {
        generateLocaleConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = false
        }
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
        // targetSdk 36 (Android 16) is deliberate. Version-freshness checks would break CI on every
        // upstream release; dependency upgrades are done explicitly instead.
        disable += setOf("OldTargetApi", "GradleDependency", "AndroidGradlePluginVersion", "NewerVersionAvailable")
    }

    packaging {
        resources.excludes += setOf("META-INF/INDEX.LIST", "META-INF/io.netty.versions.properties")
    }
}

// Pin every resolved dependency in gradle.lockfile (reproducible builds; scanned by osv-scanner).
// After changing dependencies run: ./gradlew :app:dependencies --write-locks
dependencyLocking {
    lockAllConfigurations()
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.paho.mqttv3)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.moquette) {
        // Keep the test broker pure Java: Netty falls back to NIO without these native artifacts.
        exclude(group = "io.netty", module = "netty-transport-native-epoll")
        exclude(group = "io.netty", module = "netty-tcnative-boringssl-static")
        exclude(group = "com.bugsnag")
        exclude(group = "com.librato.metrics")
        exclude(group = "io.dropwizard.metrics")
    }
}

val mavenCentralMirror = providers.gradleProperty("mavenCentralMirror").orNull
val screenshots = providers.gradleProperty("screenshots").isPresent
val screenshotDir = rootDir.resolve("fastlane/metadata/android").absolutePath

tasks.withType<Test>().configureEach {
    // Robolectric fetches android-all jars itself; point it at the optional mirror too.
    mavenCentralMirror?.let { systemProperty("robolectric.dependency.repo.url", it) }
    maxHeapSize = "3g"
    // Robolectric's SDK 36 runtime touches JDK internals.
    jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED")
    // -PskipE2e runs only the unit tests (the coverage gate also holds without e2e).
    if (providers.gradleProperty("skipE2e").isPresent) {
        filter.excludeTestsMatching("com.mqttabot.e2e.*")
    }
    // -Pscreenshots renders README screenshots only; they are skipped otherwise.
    if (screenshots) {
        filter.includeTestsMatching("com.mqttabot.screenshots.*")
        systemProperty("screenshot.dir", screenshotDir)
        outputs.upToDateWhen { false }
    } else {
        filter.excludeTestsMatching("com.mqttabot.screenshots.*")
    }
    extensions.configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}

// Unit-test coverage gate: every instruction, branch, line, method and class must be covered.
val coverageClassDirs =
    fileTree(layout.buildDirectory.dir("intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes")) {
        exclude("**/R.class", "**/R$*.class", "**/BuildConfig.*", "**/Manifest*.*")
    }
val coverageData = files(layout.buildDirectory.file("outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec"))

val jacocoUnitTestReport by tasks.registering(JacocoReport::class) {
    group = "verification"
    description = "Generates the unit-test coverage report."
    dependsOn("testDebugUnitTest")
    classDirectories.setFrom(coverageClassDirs)
    sourceDirectories.setFrom("src/main/kotlin")
    executionData.setFrom(coverageData)
    reports {
        xml.required = true
        html.required = true
        csv.required = true
    }
}

val jacocoUnitTestCoverageVerification by tasks.registering(JacocoCoverageVerification::class) {
    group = "verification"
    description = "Fails unless unit tests cover 100% of the code."
    dependsOn(jacocoUnitTestReport)
    classDirectories.setFrom(coverageClassDirs)
    sourceDirectories.setFrom("src/main/kotlin")
    executionData.setFrom(coverageData)
    violationRules {
        rule {
            for (counter in listOf("INSTRUCTION", "BRANCH", "LINE", "METHOD", "CLASS")) {
                limit {
                    this.counter = counter
                    minimum = "1.0".toBigDecimal()
                }
            }
        }
    }
}

tasks.named("check") { dependsOn(jacocoUnitTestCoverageVerification) }

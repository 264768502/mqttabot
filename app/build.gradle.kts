plugins {
    alias(libs.plugins.android.application)
    jacoco
}

android {
    namespace = "com.mqttbot"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mqttbot"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        debug {
            enableUnitTestCoverage = true
        }
        release {
            isMinifyEnabled = false
        }
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
        // Android 16 (API 36) is the deliberate target; newer SDKs / libraries are not wanted yet.
        disable += setOf("OldTargetApi", "GradleDependency", "AndroidGradlePluginVersion", "NewerVersionAvailable")
    }

    packaging {
        resources.excludes += setOf("META-INF/INDEX.LIST", "META-INF/io.netty.versions.properties")
    }
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

tasks.withType<Test>().configureEach {
    // Robolectric fetches android-all jars itself; use the same Maven Central mirror.
    systemProperty(
        "robolectric.dependency.repo.url",
        "https://maven-central.storage-download.googleapis.com/maven2",
    )
    maxHeapSize = "3g"
    // Robolectric's SDK 36 runtime touches JDK internals.
    jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED")
    // -PskipE2e runs only the unit tests (the coverage gate also holds without e2e).
    if (providers.gradleProperty("skipE2e").isPresent) {
        filter.excludeTestsMatching("com.mqttbot.e2e.*")
    }
    extensions.configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}

// Unit-test coverage gate: every instruction, branch, line, method and class must be covered.
val coverageClassDirs = fileTree(layout.buildDirectory.dir("intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes")) {
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

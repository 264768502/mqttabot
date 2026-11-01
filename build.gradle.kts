buildscript {
    // Lock the plugin classpath too (buildscript-gradle.lockfile); refresh with: ./gradlew buildEnvironment --write-locks
    configurations.classpath {
        resolutionStrategy.activateDependencyLocking()
    }
    dependencies {
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.spotless)
}

// Formatting: `./gradlew spotlessApply` fixes, `./gradlew spotlessCheck` verifies (rules in .editorconfig).
spotless {
    val ktlintVersion = libs.versions.ktlint.get()
    kotlin {
        target("**/src/**/*.kt")
        ktlint(ktlintVersion)
    }
    kotlinGradle {
        target("*.gradle.kts", "**/*.gradle.kts")
        targetExclude("**/build/**")
        ktlint(ktlintVersion)
    }
    format("xml") {
        target("**/src/**/*.xml")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

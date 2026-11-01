pluginManagement {
    repositories {
        google()
        // Google's Maven Central mirror; repo1.maven.org rate-limits frequently.
        maven("https://maven-central.storage-download.googleapis.com/maven2")
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven("https://maven-central.storage-download.googleapis.com/maven2")
        mavenCentral()
    }
}

// Patched versions of vulnerable transitive dependencies reported by osv-scanner
// (applies to plugin and project classpaths; none of these ship in the APK).
val securityPins =
    mapOf(
        "com.google.guava:guava" to "33.7.2-jre",
        "org.apache.commons:commons-lang3" to "3.21.0",
        "org.apache.httpcomponents:httpclient" to "4.5.14",
        "org.bitbucket.b_c:jose4j" to "0.9.7",
        "org.jdom:jdom2" to "2.0.6.1",
    )

gradle.allprojects {
    val pin =
        Action<Configuration> {
            resolutionStrategy.eachDependency {
                val key = "${requested.group}:${requested.name}"
                val version = requested.version.orEmpty()
                when {
                    key in securityPins -> useVersion(securityPins.getValue(key))
                    requested.group == "org.bouncycastle" && requested.name.endsWith("-jdk18on") -> useVersion("1.86")
                    requested.group == "io.netty" && version.startsWith("4.1.") -> useVersion("4.2.19.Final")
                }
            }
        }
    buildscript.configurations.configureEach(pin)
    configurations.configureEach(pin)
}

rootProject.name = "mqttabot"
include(":app")

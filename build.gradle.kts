plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kover)
    alias(libs.plugins.detekt)
}

allprojects {
    group = "io.kubemq.sdk"
    version = "1.0.0"

    repositories {
        mavenCentral()
    }
}

kover {
    reports {
        verify {
            rule {
                minBound(95)
            }
        }
    }
}

detekt {
    config.setFrom("$rootDir/detekt.yml")
    buildUponDefaultConfig = true
    source.setFrom(
        "kubemq-sdk-kotlin/src/main/kotlin",
        "kubemq-spring-boot-starter/src/main/kotlin",
        "examples/src/main/kotlin"
    )
}

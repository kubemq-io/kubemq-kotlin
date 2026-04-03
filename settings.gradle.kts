plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "kubemq-kotlin"

include(
    "kubemq-sdk-kotlin",
    "kubemq-sdk-kotlin-bom",
    "examples",
    "burnin"
)

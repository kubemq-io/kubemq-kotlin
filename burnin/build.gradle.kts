plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

kotlin {
    jvmToolchain(11)
}

application {
    mainClass.set("io.kubemq.sdk.burnin.MainKt")
}

dependencies {
    implementation(project(":kubemq-sdk-kotlin"))
    implementation(libs.coroutines.core)
    implementation(libs.kotlin.logging)
    implementation(libs.slf4j.simple)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content)
    implementation(libs.ktor.serialization.json)
    implementation(libs.micrometer.prometheus)
    implementation(libs.kaml)
}

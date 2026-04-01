plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    jvmToolchain(11)
}

application {
    mainClass.set(project.findProperty("mainClass") as? String ?: "io.kubemq.sdk.examples.connection.PingExampleKt")
}

dependencies {
    implementation(project(":kubemq-sdk-kotlin"))
    implementation(libs.coroutines.core)
    implementation(libs.slf4j.simple)
}

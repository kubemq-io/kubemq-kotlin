plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.deps)
    alias(libs.plugins.vanniktech.publish)
}

kotlin {
    jvmToolchain(17)
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}")
    }
}

dependencies {
    api(project(":kubemq-sdk-kotlin"))
    implementation(libs.coroutines.core)
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    compileOnly("org.springframework.boot:spring-boot-actuator")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(libs.kotest.runner)
    testImplementation(libs.kotest.assertions)
    testImplementation(libs.mockk)
}

mavenPublishing {
    coordinates("io.kubemq.sdk", "kubemq-spring-boot-starter", project.version.toString())
}

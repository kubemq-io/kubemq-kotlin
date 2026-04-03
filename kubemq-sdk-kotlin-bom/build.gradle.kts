plugins {
    `java-platform`
    alias(libs.plugins.vanniktech.publish)
}

javaPlatform {
    allowDependencies()
}

dependencies {
    constraints {
        api(project(":kubemq-sdk-kotlin"))
    }
}

mavenPublishing {
    coordinates("io.kubemq.sdk", "kubemq-sdk-kotlin-bom", project.version.toString())
}

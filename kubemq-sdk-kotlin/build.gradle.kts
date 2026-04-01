plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.protobuf)
    alias(libs.plugins.kover)
    alias(libs.plugins.binary.compat)
    alias(libs.plugins.vanniktech.publish)
    alias(libs.plugins.dokka)
}

kotlin {
    jvmToolchain(11)
    explicitApi()
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.29.3"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.71.0"
        }
        create("grpckt") {
            artifact = "io.grpc:protoc-gen-grpc-kotlin:1.4.3:jdk8@jar"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                create("grpc")
                create("grpckt")
            }
            task.builtins {
                create("kotlin")
            }
        }
    }
}

dependencies {
    implementation(libs.coroutines.core)
    implementation(libs.grpc.kotlin.stub)
    implementation(libs.grpc.netty.shaded)
    implementation(libs.grpc.protobuf)
    implementation(libs.grpc.stub)
    implementation(libs.protobuf.kotlin)
    implementation(libs.protobuf.java)
    implementation(libs.kotlin.logging)

    compileOnly(libs.opentelemetry.api)
    compileOnly(libs.opentelemetry.grpc)
    compileOnly(libs.slf4j.api)

    testImplementation(libs.kotest.runner)
    testImplementation(libs.kotest.assertions)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.grpc.testing)
    testImplementation(libs.grpc.inprocess)
    testImplementation(libs.slf4j.simple)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

apiValidation {
    ignoredPackages.add("kubemq")
}

tasks.withType<org.jetbrains.dokka.gradle.DokkaTask>().configureEach {
    moduleName.set("KubeMQ Kotlin SDK")
    dokkaSourceSets.configureEach {
        includes.from("../docs/packages.md")
    }
}

mavenPublishing {
    coordinates("io.kubemq.sdk", "kubemq-sdk-kotlin", project.version.toString())
    pom {
        name.set("KubeMQ Kotlin SDK")
        description.set("Kotlin-native SDK for KubeMQ message broker")
        url.set("https://github.com/kubemq-io/kubemq-kotlin")
        licenses {
            license {
                name.set("Apache-2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0")
            }
        }
        scm {
            connection.set("scm:git:git://github.com/kubemq-io/kubemq-kotlin.git")
            developerConnection.set("scm:git:ssh://github.com:kubemq-io/kubemq-kotlin.git")
            url.set("https://github.com/kubemq-io/kubemq-kotlin")
        }
        developers {
            developer {
                name.set("KubeMQ")
                email.set("support@kubemq.io")
                organization.set("KubeMQ LTD")
                organizationUrl.set("https://kubemq.io")
            }
        }
        issueManagement {
            system.set("GitHub Issues")
            url.set("https://github.com/kubemq-io/kubemq-kotlin/issues")
        }
        ciManagement {
            system.set("GitHub Actions")
            url.set("https://github.com/kubemq-io/kubemq-kotlin/actions")
        }
        properties.set(mapOf(
            "keywords" to "kubemq,messaging,message-broker,pubsub,queues,kotlin,grpc,events,commands,queries"
        ))
    }
}

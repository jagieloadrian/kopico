plugins {
    `java-gradle-plugin`
    `maven-publish`
    kotlin("jvm") version "2.4.0"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.2"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
}

group = "com.anjo"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.3")

    testImplementation(gradleTestKit())
    testImplementation("io.kotest:kotest-runner-junit5:6.2.1")
    testImplementation("io.kotest:kotest-assertions-core:6.2.1")
}

kotlin {
    jvmToolchain(21)
}

gradlePlugin {
    plugins {
        create("kopico") {
            id = "com.anjo.kopico"
            implementationClass = "com.anjo.kopico.KopicoPlugin"
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.detekt {
    enabled = false // until 2.0 publish on mvn centrl
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom("detekt.yml")
}

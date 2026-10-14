import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.4.21"
    application
}

group = "qaagent"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.modelcontextprotocol:kotlin-sdk-server:0.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-io-core:0.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    // stdout is the MCP channel — logging goes to stderr (slf4j-simple default), warnings only.
    runtimeOnly("org.slf4j:slf4j-simple:2.0.17")

    testImplementation(kotlin("test"))
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

application {
    mainClass.set("qaagent.mobile.MainKt")
    applicationDefaultJvmArgs = listOf("-Dkotlin-logging.logStartupMessage=false", "-Dorg.slf4j.simpleLogger.defaultLogLevel=warn", "-XX:+UseSerialGC", "-Xss1m")
}

tasks.test {
    useJUnitPlatform()
}

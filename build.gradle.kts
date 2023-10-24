val ktor_version: String by project
val kotlin_version: String by project
val logback_version: String by project

val kotlin_css_version: String by project
val exposed_version: String by project
val h2_version: String by project
val sqlite_version: String by project

plugins {
    kotlin("jvm") version "1.9.10"
    id("io.ktor.plugin") version "2.3.4"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.10"
}

group = "dev.craftstudio"
version = "0.0.1"

application {
    mainClass.set("dev.craftstudio.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.bundles.ktor.server)

    implementation(libs.bundles.jetbrains.exposed)
    implementation(libs.db.sqlite.jdbc)
    implementation(libs.db.h2.jdbc)

    implementation(libs.stripe.java)
    implementation(libs.gson)

    implementation(libs.bundles.ktor.client)

    implementation(libs.dotenv.kotlin)
    implementation(libs.logback.classic)

    implementation(libs.ktor.server.tests)
    testImplementation(libs.ktor.server.tests)
    testImplementation(libs.kotlin.test.junit)

}

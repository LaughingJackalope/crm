plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    // OpenAPI Generator Gradle plugin — plugin marker for plugin resolution
    implementation("org.openapi.generator:org.openapi.generator.gradle.plugin:7.9.0")
    // OpenAPI Generator plugin classes — needed for typed task registration in convention plugins
    implementation("org.openapitools:openapi-generator-gradle-plugin:7.9.0")

    // Kotlin Gradle plugin — used by crm.kotlin-convention
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.20")
}

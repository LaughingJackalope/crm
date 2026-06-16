/*
 * Shared convention for pure-Kotlin modules (e.g., :libs:common).
 *
 * Sets up:
 *   - Kotlin JVM toolchain 21
 *   - Strict JSR-305 annotations
 *   - JUnit Platform for tests
 *   - Dynamic version resolution from `-Pversion` project property
 *
 * Version resolution:
 *   Accepts `-Pversion=...` from CI. Falls back to "0.0.1-SNAPSHOT" for local dev.
 */

plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xjsr305=strict",
            "-Xjvm-default=all",
        )
    }
}

// ── Version ────────────────────────────────────────────────────────────────────
// CI injects `-Pversion=1.0.0` or `-Pversion=0.1.0-SNAPSHOT-42` via Gradle CLI.
// `findProperty` returns null when the property is unset, falling through to the
// local-development default.
version = findProperty("version")?.toString() ?: "0.0.1-SNAPSHOT"

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
    }
}

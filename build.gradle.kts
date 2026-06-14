/*
 * Root build script — applies convention plugins and shared configuration.
 * Service and library modules apply convention plugins defined in
 * `buildSrc` or `gradle/plugins/` (see convention plugin pattern below).
 */

plugins {
    // Convention plugins (defined in buildSrc)
    id("crm.kotlin-convention") apply false
    id("crm.quarkus-convention") apply false
    id("crm.openapi-convention") apply false
}

// ── Aggregated tasks ─────────────────────────────────────────────────────────
tasks.register("cleanAll") {
    group = "build"
    description = "Cleans all subprojects"
    subprojects.forEach { dependsOn("${it.path}:clean") }
}

tasks.register("testAll") {
    group = "verification"
    description = "Runs all subproject tests"
    subprojects.forEach { dependsOn("${it.path}:test") }
}

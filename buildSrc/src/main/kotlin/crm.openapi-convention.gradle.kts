/*
 * Convention plugin for contract modules that generate Kotlin DTOs from OpenAPI specs.
 *
 * Applies the OpenAPI Generator plugin and wires generated sources into compilation.
 * The consuming module registers its own GenerateTask instances or configures
 * the default openApiGenerate extension.
 */

plugins {
    id("crm.kotlin-convention")
    id("org.openapi.generator")
}

// Disable the default openApiGenerate task — consuming modules register their own.
// This avoids failures when no default spec is configured.
tasks.named("openApiGenerate") {
    enabled = false
}

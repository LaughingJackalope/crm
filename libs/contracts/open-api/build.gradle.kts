/*
 * :libs:contracts:open-api
 *
 * Generates Kotlin DTOs from all CRM OpenAPI specs.
 * Each spec produces a separate generation task with a unique package.
 *
 * Generated sources are available to all service modules as a regular dependency.
 */

plugins {
    id("crm.openapi-convention")
}

dependencies {
    // Generated Kotlin DTOs use Jackson annotations
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.1")
}

// ── One generate task per Bounded Context spec ───────────────────────────────

val specs = mapOf(
    "ciam" to file("../../../DDD/api/ciam-openapi.yaml"),
    "sales" to file("../../../DDD/api/sales-openapi.yaml"),
    "support" to file("../../../DDD/api/support-openapi.yaml"),
    "billing" to file("../../../DDD/api/billing-openapi.yaml"),
    "marketing" to file("../../../DDD/api/marketing-openapi.yaml"),
    "communication" to file("../../../DDD/api/communication-openapi.yaml"),
)

specs.forEach { (context, specFile) ->
    val taskName = "generate${context.replaceFirstChar { it.uppercase() }}Dto"

    tasks.register(taskName, org.openapitools.generator.gradle.plugin.tasks.GenerateTask::class.java) {
        generatorName.set("kotlin")
        inputSpec.set(specFile.absolutePath)
        outputDir.set(layout.buildDirectory.dir("generated-sources/openapi-$context").get().asFile.path)
        validateSpec.set(false)

        apiPackage.set("com.crm.openapi.${context}.api")
        modelPackage.set("com.crm.openapi.${context}.model")
        invokerPackage.set("com.crm.openapi.${context}.invoker")

        configOptions.set(
            mapOf(
                "dateLibrary" to "java8",
                "serializationLibrary" to "jackson",
                "enumPropertyNaming" to "UPPERCASE",
                "useJakartaEe" to "true",
                "useCoroutines" to "true",
                "collectionType" to "list",
            )
        )

        globalProperties.set(
            mapOf(
                "models" to "",
                "modelDocs" to "false",
                "modelTests" to "false",
            )
        )
    }

    // Wire generated sources into compilation
    kotlin {
        sourceSets {
            main {
                kotlin.srcDir(layout.buildDirectory.dir("generated-sources/openapi-$context/src/main/kotlin"))
            }
        }
    }
}

// Single aggregate task to generate all
tasks.register("generateAllDto") {
    group = "generation"
    description = "Generates Kotlin DTOs from all OpenAPI specs"
    dependsOn(specs.keys.map { "generate${it.replaceFirstChar { c -> c.uppercase() }}Dto" })
}

// Ensure all generations run before compile
tasks.named("compileKotlin") {
    dependsOn(tasks.named("generateAllDto"))
}

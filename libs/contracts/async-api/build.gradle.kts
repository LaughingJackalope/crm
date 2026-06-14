/*
 * :libs:contracts:async-api
 *
 * Generates Kotlin DTOs from the AsyncAPI spec for domain events.
 * Extracts JSON schemas from the asyncapi.yaml components/schemas section,
 * wraps them in a minimal OpenAPI 3.0 document, and feeds that to the
 * OpenAPI Generator Kotlin template.
 */

plugins {
    id("crm.kotlin-convention")
    id("org.openapi.generator")
}

dependencies {
    // Generated Kotlin DTOs use Jackson annotations
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.1")
    // SnakeYAML for parsing AsyncAPI spec
    implementation("org.yaml:snakeyaml:2.3")
}

val asyncApiSpec = file("../../../DDD/api/asyncapi.yaml")

// ── Step 1: Extract schemas from AsyncAPI spec ───────────────────────────────

tasks.register("extractAsyncApiSchemas") {
    group = "generation"
    description = "Extracts JSON schemas from AsyncAPI spec"

    inputs.file(asyncApiSpec)
    outputs.dir(layout.buildDirectory.dir("extracted-schemas"))

    doLast {
        val specText = asyncApiSpec.readText()
        val yaml = org.yaml.snakeyaml.Yaml()
        val parsed = yaml.load<Any>(specText)

        val components = (parsed as? Map<*, *>)?.get("components") as? Map<*, *> ?: return@doLast
        val schemas = components["schemas"] as? Map<*, *> ?: return@doLast

        val outputDir = layout.buildDirectory.dir("extracted-schemas").get().asFile
        outputDir.mkdirs()

        val schemaYaml = org.yaml.snakeyaml.Yaml().dump(schemas)

        val wrapper = """
            |openapi: 3.0.3
            |info:
            |  title: CRM Domain Events
            |  version: 1.1.0
            |paths: {}
            |components:
            |  schemas:
            |${schemaYaml.prependIndent("    ")}
        """.trimMargin()

        File(outputDir, "asyncapi-schemas.yaml").writeText(wrapper)
    }
}

// ── Step 2: Generate Kotlin event DTOs via OpenAPI Generator ─────────────────

openApiGenerate {
    validateSpec.set(false)
    generatorName.set("kotlin")
    inputSpec.set(
        layout.buildDirectory
            .file("extracted-schemas/asyncapi-schemas.yaml")
            .get().asFile.absolutePath
    )
    outputDir.set(
        layout.buildDirectory.dir("generated-sources/asyncapi").get().asFile.absolutePath
    )
    modelPackage.set("com.crm.asyncapi.model")

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

// Ensure extraction runs before generation
tasks.named("openApiGenerate") {
    dependsOn("extractAsyncApiSchemas")
}

// ── Step 3: Wire generated sources into compilation ───────────────────────────

kotlin {
    sourceSets {
        main {
            kotlin.srcDir(
                layout.buildDirectory.dir("generated-sources/asyncapi/src/main/kotlin")
            )
        }
    }
}

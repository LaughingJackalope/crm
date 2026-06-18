package com.crm.test

/**
 * JUnit 5 `@Tag` constants for classifying tests.
 *
 * Usage:
 * ```
 * @Test
 * @Tag(TestTags.INTEGRATION)
 * fun `should process Kafka event`() { ... }
 * ```
 *
 * The Gradle build excludes `integration`-tagged tests by default.
 * Opt in with `-Pinclude.integration`:
 * ```
 * ./gradlew test -Pinclude.integration
 * ```
 */
object TestTags {
    /** Marks a test that requires Testcontainers (Postgres, Kafka, etc.). */
    const val INTEGRATION = "integration"
}

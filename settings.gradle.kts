/*
 * Root settings for CRM monorepo — DDD Bounded Contexts with Quarkus + Kotlin.
 *
 * Module naming convention:
 *   :libs:contracts:<type>   — generated API DTOs (OpenAPI / AsyncAPI)
 *   :libs:common             — shared cross-cutting concerns
 *   :services:<context>      — one module per Bounded Context
 */

// ── Build cache ──────────────────────────────────────────────────────────────
buildCache {
    local {
        isEnabled = true
        directory = File(rootDir, ".gradle/build-cache")
    }
    // Uncomment for remote cache (e.g., Gradle Enterprise):
    // remote<HttpBuildCache> {
    //     url = uri("https://gradle-cache.example.com/cache/")
    //     isEnabled = true
    //     isPush = System.getenv("CI") != null
    //     credentials {
    //         username = System.getenv("GRADLE_CACHE_USER")
    //         password = System.getenv("GRADLE_CACHE_PASSWORD")
    //     }
    // }
}

// ── Plugin management ────────────────────────────────────────────────────────
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

// ── Dependency resolution ────────────────────────────────────────────────────
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "crm"

// ── Contract / Schema modules (generated DTOs) ───────────────────────────────
include(
    ":libs:contracts:open-api",
    ":libs:contracts:async-api",
)

// ── Shared library modules ───────────────────────────────────────────────────
include(
    ":libs:common",
)

// ── Bounded Context service modules ──────────────────────────────────────────
include(
    ":services:ciam-service",
    ":services:sales-service",
    ":services:support-service",
    ":services:billing-service",
    ":services:marketing-service",
    ":services:communication-service",
)

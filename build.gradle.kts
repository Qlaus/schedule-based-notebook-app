plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

/**
 * Fails the build if any Google Play Services / Firebase artifact sneaks into the
 * app's runtime classpath. Huawei devices have no GMS, so these must never be used.
 */
tasks.register("checkNoGms") {
    group = "verification"
    description = "Fails if Play Services or Firebase are on the app runtime classpath."
    doLast {
        val app = project(":app")
        val forbidden = listOf("com.google.android.gms", "com.google.firebase")
        listOf("debugRuntimeClasspath", "releaseRuntimeClasspath").forEach { name ->
            val hits = app.configurations.getByName(name).resolvedConfiguration
                .resolvedArtifacts.map { it.moduleVersion.id.group }
                .filter { group -> forbidden.any { group.startsWith(it) } }
                .distinct()
            if (hits.isNotEmpty()) throw GradleException("Forbidden GMS/Firebase deps in $name: $hits")
        }
        println("checkNoGms: OK, no Play Services or Firebase dependencies.")
    }
}

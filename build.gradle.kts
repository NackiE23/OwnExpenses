plugins {
    // Declared here once so that every module shares the same plugin classloader.
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.androidx.room) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.spotless)
}

spotless {
    val ktlintVersion = libs.versions.ktlint.get()
    val composeRules =
        libs.compose.rules.ktlint
            .get()
            .toString()

    kotlin {
        target("**/src/**/*.kt")
        targetExclude("**/build/**")
        ktlint(ktlintVersion).customRuleSets(listOf(composeRules))
    }
    kotlinGradle {
        target("*.gradle.kts", "*/*.gradle.kts")
        ktlint(ktlintVersion)
    }
}

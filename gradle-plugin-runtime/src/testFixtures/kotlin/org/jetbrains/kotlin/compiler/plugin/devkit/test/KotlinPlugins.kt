package org.jetbrains.kotlin.compiler.plugin.devkit.test

import com.autonomousapps.kit.AbstractGradleProject.Companion.PLUGIN_UNDER_TEST_VERSION
import com.autonomousapps.kit.gradle.Plugin

/** The version of the Gradle plugin under test, injected by the testkit infrastructure. */
public val pluginUnderTestVersion: String = PLUGIN_UNDER_TEST_VERSION

/** Kotlin Gradle plugin declarations, defaulting to the Kotlin version currently under test. */
public object KotlinPlugins {
    public fun jvm(version: String? = null): Plugin =
        Plugin("org.jetbrains.kotlin.jvm", version ?: getTestCompilerVersion())

    public fun multiplatform(version: String? = null): Plugin =
        Plugin("org.jetbrains.kotlin.multiplatform", version ?: getTestCompilerVersion())
}

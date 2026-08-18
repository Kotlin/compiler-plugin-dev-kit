package org.jetbrains.kotlin.compiler.plugin.devkit.test

import com.autonomousapps.kit.AbstractGradleProject.Companion.PLUGIN_UNDER_TEST_VERSION
import com.autonomousapps.kit.gradle.Plugin
import org.jetbrains.kotlin.compiler.plugin.devkit.KotlinToolingVersion
import org.jetbrains.kotlin.compiler.plugin.devkit.isDev

/** The version of the Gradle plugin under test, injected by the testkit infrastructure. */
public val pluginUnderTestVersion: String = PLUGIN_UNDER_TEST_VERSION

private val KotlinToolingVersion.lowestDev: KotlinToolingVersion
    get() {
        require(isDev)
        val classifier = requireNotNull(classifier)
        val lastDashIndex = classifier.indexOfLast { it == '-' }
        val newClassifier =
            if (lastDashIndex == -1) classifier else classifier.substring(0, lastDashIndex)
        return KotlinToolingVersion(major, minor, patch, newClassifier)
    }

/** Kotlin Gradle plugin declarations, defaulting to the Kotlin version currently under test. */
public object KotlinPlugins {
    private val defaultKotlinVersion by lazy {
        val version = getTestCompilerToolingVersion()
        if (version.isDev) "[${version.lowestDev}, $version]" else "$version"
    }

    public fun jvm(version: String? = null): Plugin =
        Plugin("org.jetbrains.kotlin.jvm", version ?: defaultKotlinVersion)

    public fun multiplatform(version: String? = null): Plugin =
        Plugin("org.jetbrains.kotlin.multiplatform", version ?: defaultKotlinVersion)
}

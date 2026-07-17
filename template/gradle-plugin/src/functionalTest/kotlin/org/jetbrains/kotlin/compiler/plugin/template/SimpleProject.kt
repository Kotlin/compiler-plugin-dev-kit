package org.jetbrains.kotlin.compiler.plugin.template

import com.autonomousapps.kit.gradle.Plugin
import org.jetbrains.kotlin.compiler.plugin.devkit.test.AbstractDevKitGradleProject
import org.jetbrains.kotlin.compiler.plugin.devkit.test.pluginUnderTestVersion

open class SimpleProject(multiplatform: Boolean = false) :
    AbstractDevKitGradleProject(multiplatform = multiplatform) {
    // TODO provide star import in build config
    override val defaultImports: List<String>
        get() =
            listOf(
                "org.jetbrains.kotlin.compiler.plugin.template.SomeAnnotation",
                "foo.bar.MyClass",
            )

    // TODO provide in build config
    override val pluginUnderTest: Plugin
        get() = Plugin("org.jetbrains.kotlin.compiler.plugin.template", pluginUnderTestVersion)
}

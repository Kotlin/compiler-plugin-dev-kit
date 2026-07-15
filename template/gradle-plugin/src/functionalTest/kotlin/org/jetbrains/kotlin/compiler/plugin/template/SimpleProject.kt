package org.jetbrains.kotlin.compiler.plugin.template

import com.autonomousapps.kit.gradle.Plugin
import com.autonomousapps.kit.gradle.Repositories
import com.autonomousapps.kit.gradle.Repository
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

    // TODO provide by default
    override fun repositories(defaults: List<Repository>): Repositories =
        Repositories(
            mutableListOf<Repository>().apply {
                addAll(defaults)
                add(Repository.ofMaven("https://packages.jetbrains.team/maven/p/kt/bootstrap"))
                add(Repository.ofMaven("https://packages.jetbrains.team/maven/p/kt/dev/"))
                add(
                    Repository.ofMaven(
                        "https://packages.jetbrains.team/maven/p/ij/intellij-dependencies/"
                    )
                )
            }
        )
}

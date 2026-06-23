package org.jetbrains.kotlin.compiler.plugin.template

import org.gradle.api.Project

@Suppress("unused") // Used via reflection.
class SimpleGradlePlugin : DevKitCompilerGradlePlugin(BuildConfig.PLUGIN_INFO) {
    override fun apply(target: Project) {
        target.extensions.create("simplePlugin", SimpleGradleExtension::class.java)
    }
}

package org.jetbrains.kotlin.compiler.plugin.template

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.compiler.plugin.devkit.DevKitCompilerGradlePlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

@Suppress("unused") // Used via reflection.
class SimpleGradlePlugin : DevKitCompilerGradlePlugin(BuildConfig.PLUGIN_INFO) {
    override fun apply(target: Project) {
        target.extensions.create("simplePlugin", SimpleGradleExtension::class.java)
    }

    override fun Project.applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        kotlinCompilation.compileTaskProvider.configure {
            // Run this compiler plugin before Compose plugin.
            it.compilerOptions.freeCompilerArgs.add("-Xcompiler-plugin-order=${BuildConfig.KOTLIN_PLUGIN_ID}>androidx.compose.compiler.plugins.kotlin")
        }
        return super.applyToCompilation(kotlinCompilation)
    }
}

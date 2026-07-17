package org.jetbrains.kotlin.compiler.plugin.devkit.services

import java.io.File
import java.util.Collections.emptyList
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.js.config.JSConfigurationKeys
import org.jetbrains.kotlin.platform.isJs
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.EnvironmentConfigurator
import org.jetbrains.kotlin.test.services.RuntimeClasspathProvider
import org.jetbrains.kotlin.test.services.TestServices
import org.jetbrains.kotlin.test.services.targetPlatform

public fun TestConfigurationBuilder.configureDefaultRuntimeLibraries() {
    configureRuntimeLibraries(defaultLibrariesJvm, defaultLibrariesJs) { true }
}

public fun TestConfigurationBuilder.configureRuntimeLibraries(
    jvmClasspath: List<File> = emptyList(),
    jsClasspath: List<File> = emptyList(),
    condition: TestServices.(TestModule) -> Boolean,
) {
    useConfigurators({
        object : EnvironmentConfigurator(it) {
            override fun configureCompilerConfiguration(
                configuration: CompilerConfiguration,
                module: TestModule,
            ) {
                if (!testServices.condition(module)) return
                val platform = module.targetPlatform(testServices)
                when {
                    platform.isJvm() -> {
                        configuration.addJvmClasspathRoots(jvmClasspath)
                    }

                    platform.isJs() -> {
                        val libraries = configuration.getList(JSConfigurationKeys.LIBRARIES)
                        configuration.put(
                            JSConfigurationKeys.LIBRARIES,
                            libraries + jsClasspath.map { it.absolutePath },
                        )
                    }
                }
            }
        }
    })
    useCustomRuntimeClasspathProviders({
        object : RuntimeClasspathProvider(it) {
            override fun runtimeClassPaths(module: TestModule): List<File> {
                if (!testServices.condition(module)) return emptyList()
                val targetPlatform = module.targetPlatform(testServices)
                return when {
                    targetPlatform.isJvm() -> jvmClasspath
                    targetPlatform.isJs() -> jsClasspath
                    else -> emptyList()
                }
            }
        }
    })
}

private val defaultLibrariesJvm = classpathFiles("defaultRuntimeLibraries.jvm.classpath")
private val defaultLibrariesJs = classpathFiles("defaultRuntimeLibraries.js.classpath")

public fun classpathFiles(property: String): List<File> {
    val property =
        System.getProperty(property)
            ?: error("Unable to get a valid classpath from '$property' property")
    return property.split(File.pathSeparator).map(::File)
}

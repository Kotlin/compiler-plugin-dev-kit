package org.jetbrains.kotlin.compiler.plugin.devkit

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.mapProperty
import org.gradle.kotlin.dsl.withType
import org.gradle.plugins.ide.api.GeneratorTask
import org.gradle.plugins.ide.internal.generator.generator.Generator

public abstract class JsReplGeneratorTask : GeneratorTask<String>() {
    @get:Input @get:Optional public abstract val defaultRepl: Property<String>

    init {
        generator =
            object : Generator<String> {
                override fun read(inputFile: File?): String? =
                    defaultRepl.orNull ?: inputFile?.readText()

                override fun defaultInstance(): String? = defaultRepl.orNull

                override fun configure(`object`: String?) {}

                override fun write(`object`: String?, outputFile: File?) {
                    if (`object` != null && outputFile != null) outputFile.writeText(`object`)
                }
            }
    }
}

public abstract class GenerateTestsTask : JavaExec() {
    @get:OutputDirectory public abstract val generatedTestsDir: DirectoryProperty

    override fun exec() {
        generatedTestsDir.asFile.get().deleteRecursively()
        super.exec()
    }
}

internal abstract class GenerateServices : DefaultTask() {
    @get:OutputDirectory abstract val generatedServicesDir: DirectoryProperty
    @get:Input val services: MapProperty<String, String> = project.objects.mapProperty()

    @TaskAction
    fun generateServices() {
        val generatedServicesDir = generatedServicesDir.asFile.get()
        generatedServicesDir.deleteRecursively()
        val servicesDir = generatedServicesDir.resolve("META-INF/services")
        servicesDir.mkdirs()
        services.get().forEach { (service, implementation) ->
            val serviceFile = servicesDir.resolve(service)
            serviceFile.writeText(implementation)
        }
    }
}

internal open class DefaultTestTask : DefaultTask() {
    // copied from KotlinTestsRegistry
    init {
        val isIdeaActive = project.providers.systemProperty("idea.active").isPresent

        if (isIdeaActive) {
            extensions.extraProperties.set("idea.internal.test", true)
        }
    }

    @Option(
        option = "tests",
        description = "Sets test class or method name to be included, '*' is supported.",
    )
    open fun setTestNameIncludePatterns(testNamePattern: List<String>): DefaultTestTask {
        project.tasks.withType<Test> { setTestNameIncludePatterns(testNamePattern) }
        return this
    }
}

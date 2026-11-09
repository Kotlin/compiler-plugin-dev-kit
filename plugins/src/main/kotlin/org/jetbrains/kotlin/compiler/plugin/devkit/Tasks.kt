package org.jetbrains.kotlin.compiler.plugin.devkit

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectories
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.mapProperty
import org.gradle.kotlin.dsl.withType
import org.gradle.work.DisableCachingByDefault

@CacheableTask
public abstract class JsReplGeneratorTask : DefaultTask() {
    @get:Input @get:Optional public abstract val defaultRepl: Property<String>

    @get:OutputFile public abstract val outputFile: RegularFileProperty

    @TaskAction
    public fun generate() {
        val file = outputFile.get().asFile
        val content = defaultRepl.orNull ?: return
        file.parentFile.mkdirs()
        file.writeText(content)
    }
}

@DisableCachingByDefault(because = "it mostly does IO, so no benefit in caching it")
internal abstract class GenerateTestsTask : JavaExec() {
    @get:OutputDirectories abstract val generatedTestsDirs: ListProperty<Directory>

    override fun exec() {
        generatedTestsDirs.get().forEach { it.asFile.deleteRecursively() }
        super.exec()
    }
}

/**
 * A task that scans provided testData directories and writes a list of relative .kt files into
 * build/testDataInfo/testDataFilesList.txt
 */
@DisableCachingByDefault(because = "it only does IO, so no benefit in caching it")
internal abstract class CollectTestDataTask : DefaultTask() {
    @get:Input abstract val rootDirPath: Property<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val testDataFiles: ConfigurableFileCollection

    @get:OutputFile abstract val targetFile: RegularFileProperty

    @get:Input abstract val filePatterns: ListProperty<String>

    @get:Input abstract val excludeFilePatterns: ListProperty<String>

    @TaskAction
    fun run() {
        val files = testDataFiles.asFileTree
        val rootDir = File(rootDirPath.get())
        val outFile = targetFile.get().asFile
        outFile.parentFile.mkdirs()

        val text =
            files
                .matching {
                    include(filePatterns.get())
                    exclude(excludeFilePatterns.get())
                }
                .sorted()
                .joinToString("\n") {
                    it.relativeTo(rootDir).path.replace('\\', '/')
                }

        outFile.writeText(text)
    }
}

@DisableCachingByDefault(because = "it writes simple strings to a file")
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

@DisableCachingByDefault(because = "This is used as a lifecycle task, so it's usually disabled")
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

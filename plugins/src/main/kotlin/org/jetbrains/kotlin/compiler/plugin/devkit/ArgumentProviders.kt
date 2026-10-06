package org.jetbrains.kotlin.compiler.plugin.devkit

import java.io.File
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.testing.Test
import org.gradle.process.CommandLineArgumentProvider
import org.gradle.process.JavaForkOptions
import org.jetbrains.kotlin.gradle.plugin.HasProject

internal interface CommandLineArgumentProviderFlatMap : CommandLineArgumentProvider, ProviderFlatMap

internal class FilesCommandLineArgumentProvider(
    private val rootDir: String,
    private val inputFiles: FileCollection,
    @OutputDirectory val outputDir: Provider<out Directory>,
) : CommandLineArgumentProviderFlatMap {
    @get:Input
    val inputFilePaths: List<String>
        get() = inputFiles.map { it.toRelativeString(File(rootDir)) }

    override fun asArguments() =
        listOf(
            outputDir().asFile.toRelativeString(File(rootDir)),
            inputFilePaths.joinToString(","),
        )
}

context(_: HasProject)
internal fun filesCommandLineArgumentProvider(
    inputFiles: FileCollection,
    outputDir: Provider<out Directory>,
) = FilesCommandLineArgumentProvider(rootDir.path, inputFiles, outputDir)

internal class LibraryPathArgumentProvider(
    @Input val propNames: List<String>,
    @Input val jarName: String,
    @InputFiles
    @PathSensitive(PathSensitivity.ABSOLUTE)
    val configuration: Provider<out FileCollection>,
) : CommandLineArgumentProviderFlatMap {
    override fun asArguments(): Iterable<String> {
        val regex = """$jarName-\d.*""".toRegex()
        val path = configuration().files.find { regex.matches(it.name) }?.absolutePath
        return if (path != null) propNames.map { "-D$it=$path" } else emptyList()
    }
}

internal class ClasspathSystemPropertyArgumentProvider(
    @Input val propName: String,
    @Classpath val configuration: Provider<out FileCollection>,
) : CommandLineArgumentProviderFlatMap {
    override fun asArguments(): Iterable<String> = listOf("-D$propName=${configuration().asPath}")
}

internal class FileSystemPropertyArgumentProvider(
    @Input val propName: String,
    @Internal val file: Provider<out File>,
) : CommandLineArgumentProviderFlatMap {
    @get:Input
    val filePath: String
        get() = file().toString()

    override fun asArguments(): Iterable<String> = listOf("-D$propName=$filePath")
}

internal class StringSystemPropertyArgumentProvider(
    @Input val propName: String,
    @Input val value: Provider<out String>,
) : CommandLineArgumentProviderFlatMap {
    override fun asArguments(): Iterable<String> = listOf("-D$propName=${value()}")
}

internal class OptionalStringSystemPropertyArgumentProvider(
    @Input val propName: String,
    @Optional @Input val value: Provider<String>,
) : CommandLineArgumentProvider {
    override fun asArguments(): Iterable<String> =
        value.orNull?.let { listOf("-D$propName=$it") } ?: emptyList()
}

internal class NestedArgumentProvider(
    @Nested val providers: Iterable<CommandLineArgumentProvider>
) : CommandLineArgumentProvider {
    override fun asArguments() = providers.flatMap { it.asArguments() }
}

internal class PropertiesSystemPropertyArgumentProvider(
    @Input val propName: String,
    @Input val properties: Provider<List<String>>,
) : CommandLineArgumentProviderFlatMap {
    override fun asArguments() = listOf("-D$propName=${properties.get().joinToString(",")}")
}

internal class NestedSystemPropertyArgumentProvider(
    @Input val propName: String,
    @Nested val properties: Provider<out Iterable<CommandLineArgumentProvider>>,
    @Input val delimiter: String = ",",
) : CommandLineArgumentProviderFlatMap {
    override fun asArguments() =
        listOf(
            "-D$propName=${properties.get().flatMap { it.asArguments() }.joinToString(delimiter)}"
        )
}

context(exec: JavaForkOptions)
internal operator fun CommandLineArgumentProvider.unaryPlus() = exec.jvmArgumentProviders.add(this)

public fun Test.setClasspathProperty(
    propName: String,
    configuration: Provider<out FileCollection>,
) {
    +ClasspathSystemPropertyArgumentProvider(propName, configuration)
}

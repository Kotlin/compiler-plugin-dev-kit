package org.jetbrains.kotlin.compiler.plugin.devkit

import java.io.File
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
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

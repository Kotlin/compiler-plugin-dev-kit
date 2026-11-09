package org.jetbrains.kotlin.compiler.plugin.devkit.test

import com.autonomousapps.kit.GradleBuilder.build
import com.autonomousapps.kit.GradleProject
import com.autonomousapps.kit.truth.BuildResultSubject
import com.autonomousapps.kit.truth.TestKitTruth.Companion.assertThat
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files.readAttributes
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import kotlin.io.path.absolute
import kotlin.io.path.exists
import kotlin.io.path.getLastModifiedTime
import kotlin.test.assertContains
import kotlin.test.fail
import org.gradle.testkit.runner.BuildResult
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion

private val FILE_PATH_REGEX = Regex("file://.*?/(?=[^/]+\\.kt)")

public fun String.cleanOutputLine(): String =
    FILE_PATH_REGEX.replace(trimEnd(), "").lineSequence().joinToString("\n") { it.trimEnd() }

/**
 * Loads the main classes of a [GradleProject].
 *
 * @param target KMP target name (defaults to `"jvm"`). Classes are read from
 *   `build/classes/kotlin/<target>/main` to match the multiplatform output layout. Pass `null` for
 *   the plain JVM layout `build/classes/kotlin/main`.
 */
public fun GradleProject.classLoader(target: String? = "jvm"): ClassLoader {
    val pathSuffix = if (target != null) "$target/main" else "main"
    val rootClassesDir = rootDir.toPath().resolve("build/classes/kotlin/$pathSuffix").absolute()

    check(rootClassesDir.exists()) {
        "Root classes dir not found: ${rootClassesDir.toAbsolutePath()}"
    }

    val subprojectClassesDirs = subprojects.map { subproject ->
        val dir =
            rootDir
                .toPath()
                .resolve("${subproject.name.replace(':', '/')}/build/classes/kotlin/$pathSuffix")
                .absolute()
        check(rootClassesDir.exists()) {
            "Subproject ${subproject.name} classes dir not found: ${dir.toAbsolutePath()}"
        }
        dir.toUri().toURL()
    }

    return URLClassLoader(
        // Include the original classpaths and the output directory to be able to load classes from
        // dependencies.
        (subprojectClassesDirs + rootClassesDir.toUri().toURL()).toTypedArray(),
        this::class.java.classLoader,
    )
}

public fun buildAndAssertThat(projectDir: File, args: String, body: BuildResultSubject.() -> Unit) {
    val result = build(projectDir, *args.split(' ').toTypedArray())
    assertThat(result).body()
}

public fun BuildResult.assertOutputContains(text: String) {
    val output = output.cleanOutputLine()
    assertContains(output, text)
}

/** The Kotlin version the current `functionalTest<Version>` task is running against. */
public val TEST_COMPILER_VERSION: KotlinToolingVersion by lazy {
    KotlinToolingVersion(System.getProperty("kotlin.compiler.plugin.devkit.test.kotlin-version"))
}

/** The Kotlin version that companion libraries use. */
public val BUILD_COMPILER_VERSION: KotlinToolingVersion by lazy {
    KotlinToolingVersion(System.getProperty("kotlin.compiler.plugin.devkit.build.kotlin-version"))
}

/**
 * The Kotlin/Native host target for the current machine, e.g. `macosArm64` on Apple Silicon,
 * `linuxX64` on x86-64 Linux. Kotlin/Native can only compile the host target without pulling in a
 * cross-compilation toolchain, which is useful for keeping multiplatform functional test matrices
 * fast and portable.
 */
public fun detectHostNativeTarget(): String {
    val os = System.getProperty("os.name").orEmpty().lowercase()
    val arch = System.getProperty("os.arch").orEmpty().lowercase()
    val isArm64 = arch == "aarch64" || arch == "arm64"
    return when {
        os.contains("mac") || os.contains("darwin") -> if (isArm64) "macosArm64" else "macosX64"
        os.contains("windows") -> "mingwX64"
        os.contains("linux") -> if (isArm64) "linuxArm64" else "linuxX64"
        else -> "linuxX64"
    }
}

public fun File.resolveSafe(relative: String): File {
    val dir = this
    return resolve(relative).apply {
        if (!exists()) {
            fail(
                "Could not find $relative in $dir. Files are:\n${dir.walkTopDown().filter { it.isFile }.joinToString("\n")}"
            )
        }
    }
}

public val Path.snapshot: FileSnapshot
    get() {
        return FileSnapshot(
            fileKey = readAttributes(this, BasicFileAttributes::class.java).fileKey(),
            lastModified = getLastModifiedTime(),
        )
    }

public data class FileSnapshot(val fileKey: Any?, val lastModified: FileTime)

/**
 * Invokes the `main` function from the compiled test sources and returns the result.
 *
 * @param className the fully qualified class name containing the main function (defaults to
 *   "test.MainKt")
 * @param target optional KMP target name forwarded to [classLoader]
 * @return the result of invoking the main function, cast to type [T]
 */
public inline fun <reified T> GradleProject.invokeMain(
    className: String = "test.MainKt",
    target: String? = "jvm",
): T {
    return classLoader(target)
        .loadClass(className)
        .declaredMethods
        .first { it.name == "main" }
        .invoke(null) as T
}

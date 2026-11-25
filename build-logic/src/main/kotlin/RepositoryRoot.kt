import java.io.File
import org.gradle.api.Project

// `plugins` and `version-resolution` are builds in subdirectories, so `rootDir` isn't always the
// root of the repository.
val Project.repositoryRoot: File
    get() =
        generateSequence(rootDir) { it.parentFile }.first { it.resolve("build-logic").isDirectory }

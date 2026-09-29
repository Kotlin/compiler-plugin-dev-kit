package org.jetbrains.kotlin.compiler.plugin.devkit.test

import com.autonomousapps.kit.AbstractGradleProject
import com.autonomousapps.kit.GradleProject
import com.autonomousapps.kit.GradleProject.DslKind
import com.autonomousapps.kit.RootProject
import com.autonomousapps.kit.Source
import com.autonomousapps.kit.Source.Companion.DEFAULT_SOURCE_SET
import com.autonomousapps.kit.gradle.BuildScript
import com.autonomousapps.kit.gradle.Dependency
import com.autonomousapps.kit.gradle.DependencyResolutionManagement
import com.autonomousapps.kit.gradle.Plugin
import com.autonomousapps.kit.gradle.PluginManagement
import com.autonomousapps.kit.gradle.Repositories
import com.autonomousapps.kit.gradle.Repository
import org.intellij.lang.annotations.Language
import org.junit.Assume.assumeTrue

/**
 * Base for a TestKit fixture that generates single- or multi-module Gradle projects applying a
 * compiler plugin under test. Subclasses supply [pluginUnderTest] and, optionally, a
 * [pluginConfigBlock], extra [repositories], [defaultImports] and [extraGradleProperties].
 */
public abstract class AbstractDevKitGradleProject(
    protected val kotlinVersion: String? = null,
    protected val multiplatform: Boolean = false,
) : AbstractGradleProject() {
    init {
        // Technically, it could be a multiplatform JVM-only project
        if (multiplatform)
            assumeTrue(
                "KLIBs are not forward compatible. The build uses $BUILD_COMPILER_VERSION " +
                    "which is greater than $TEST_COMPILER_VERSION",
                TEST_COMPILER_VERSION >= BUILD_COMPILER_VERSION,
            )
    }

    /** Source set that [source] declarations target by default. */
    protected val defaultSourceSet: String = if (multiplatform) "commonMain" else DEFAULT_SOURCE_SET

    /** Imports prepended to [source] snippets when `includeDefaultImports` is true. */
    protected open val defaultImports: List<String> = emptyList()

    /** The plugin under test, applied alongside Kotlin in each module's default build script. */
    protected abstract val pluginUnderTest: Plugin

    public val gradleProject: GradleProject by lazy { buildGradleProject() }

    protected fun source(
        @Language("kotlin") source: String,
        fileNameWithoutExtension: String? = null,
        packageName: String = "test",
        includeDefaultImports: Boolean = true,
        vararg extraImports: String,
    ): Source =
        source(
            source = source,
            fileNameWithoutExtension = fileNameWithoutExtension,
            packageName = packageName,
            sourceSet = defaultSourceSet,
            imports =
                buildList {
                    if (includeDefaultImports) addAll(defaultImports)
                    addAll(extraImports)
                },
        )

    /**
     * Sources for the default single-module project. Unused when [buildGradleProject] is
     * overridden.
     */
    protected open fun sources(): List<Source> = emptyList()

    public open fun StringBuilder.onBuildScript() {}

    protected open fun buildGradleProject(): GradleProject =
        newGradleProjectBuilder(DslKind.KOTLIN)
            .withRootProject {
                sources = this@AbstractDevKitGradleProject.sources()
                withBuildScript { applyDefaultBuildScript() }
                withDevKitSettings()
            }
            .write()

    protected fun multiModuleProject(
        configure: MultiModuleProjectBuilder.() -> Unit
    ): GradleProject {
        val builder = MultiModuleProjectBuilder()
        builder.configure()
        return builder.build()
    }

    protected inner class MultiModuleProjectBuilder {
        private val projects = mutableListOf<ProjectModuleConfig>()

        public fun root(configure: ProjectModuleBuilder.() -> Unit) {
            val builder = ProjectModuleBuilder(":")
            builder.configure()
            projects.add(0, builder.build())
        }

        public fun subproject(path: String, configure: ProjectModuleBuilder.() -> Unit) {
            val builder = ProjectModuleBuilder(path)
            builder.configure()
            projects += builder.build()
        }

        public fun build(): GradleProject {
            val rootConfig = projects.firstOrNull { it.path == ":" }
            val subprojectConfigs = projects.filter { it.path != ":" }
            return newGradleProjectBuilder(DslKind.KOTLIN)
                .withRootProject {
                    rootConfig?.let { config ->
                        sources = config.sources
                        withBuildScript {
                            applyDefaultBuildScript()
                            if (config.dependencies.isNotEmpty()) {
                                dependencies(*config.dependencies.mappedForTarget().toTypedArray())
                            }
                            config.buildScriptExtra?.invoke(this)
                        }
                    } ?: withBuildScript { applyDefaultBuildScript() }
                    withDevKitSettings()
                }
                .apply {
                    for (config in subprojectConfigs) {
                        withSubproject(config.path) {
                            sources.addAll(config.sources)
                            withBuildScript {
                                if (config.plugins.isNotEmpty()) {
                                    plugins(*config.plugins.toTypedArray())
                                    withKotlin(pluginConfigBlock())
                                } else {
                                    applyDefaultBuildScript()
                                }
                                if (config.dependencies.isNotEmpty()) {
                                    dependencies(
                                        *config.dependencies.mappedForTarget().toTypedArray()
                                    )
                                }
                                config.buildScriptExtra?.invoke(this)
                            }
                        }
                    }
                }
                .write()
        }

        private fun List<Dependency>.mappedForTarget(): List<Dependency> {
            if (!multiplatform) return this
            return map { dep ->
                dep.copy(
                    configuration =
                        "commonMain" + dep.configuration.replaceFirstChar { it.titlecase() }
                )
            }
        }
    }

    protected class ProjectModuleBuilder(private val path: String) {
        private var sources: List<Source> = emptyList()
        private var dependencies: List<Dependency> = emptyList()
        private var plugins: List<Plugin> = emptyList()
        private var buildScriptExtra: (BuildScript.Builder.() -> Unit)? = null

        public fun sources(vararg sources: Source) {
            this.sources += sources.toList()
        }

        public fun sources(sources: List<Source>) {
            this.sources = sources
        }

        public fun dependencies(vararg deps: Dependency) {
            this.dependencies += deps.toList()
        }

        /** Override the default plugins. If set, you must specify every plugin including Kotlin. */
        public fun plugins(vararg plugins: Plugin) {
            this.plugins += plugins.toList()
        }

        public fun buildScript(configure: BuildScript.Builder.() -> Unit) {
            this.buildScriptExtra = configure
        }

        public fun build(): ProjectModuleConfig =
            ProjectModuleConfig(path, sources, dependencies, plugins, buildScriptExtra)
    }

    public data class ProjectModuleConfig(
        public val path: String,
        public val sources: List<Source>,
        public val dependencies: List<Dependency>,
        public val plugins: List<Plugin>,
        public val buildScriptExtra: (BuildScript.Builder.() -> Unit)?,
    )

    protected fun RootProject.Builder.withDevKitSettings() {
        withSettingsScript {
            pluginManagement =
                PluginManagement(
                    repositories(
                        buildList {
                            addAll(Repository.FUNC_TEST_INCLUDED_BUILDS)
                            addAll(Repository.DEFAULT_PLUGINS)
                            addAll(KOTLIN_REPOS)
                        }
                    )
                )
            dependencyResolutionManagement =
                DependencyResolutionManagement(
                    repositories(
                        buildList {
                            addAll(Repository.FUNC_TEST_INCLUDED_BUILDS)
                            addAll(Repository.DEFAULT)
                            addAll(KOTLIN_REPOS)
                        }
                    )
                )
        }
        gradleProperties = gradleProperties.plus(extraGradleProperties)
    }

    /** Repositories for both `pluginManagement` and `dependencyResolutionManagement`. */
    protected open fun repositories(defaults: List<Repository>): Repositories =
        Repositories(defaults.toMutableList())

    /** gradle.properties lines layered onto every generated project. */
    protected open val extraGradleProperties: List<String> = DEFAULT_TESTKIT_GRADLE_PROPERTIES

    protected open fun BuildScript.Builder.applyDefaultBuildScript() {
        val kotlinPlugin =
            if (multiplatform) KotlinPlugins.multiplatform(kotlinVersion)
            else KotlinPlugins.jvm(kotlinVersion)
        plugins(kotlinPlugin, pluginUnderTest)
        withKotlin(
            buildString {
                onBuildScript()
                if (multiplatform) append(multiplatformTargetsBlock())
                append(pluginConfigBlock())
            }
        )
    }

    /**
     * Plugin-specific DSL block (e.g. `metro { ... }`) appended to the generated `kotlin` script.
     */
    protected open fun pluginConfigBlock(): String = ""

    /** The `kotlin { ... }` targets block for multiplatform projects. Override to scope it down. */
    protected open fun multiplatformTargetsBlock(): String = buildString {
        appendLine("kotlin {")
        appendLine("  jvm()")
        appendLine("  js { nodejs() }")
        appendLine("  wasmJs { nodejs() }")
        appendLine("  ${KmpTarget.NATIVE_HOST.gradleTargetName}()")
        appendLine("}")
    }

    private companion object {
        private val DEFAULT_TESTKIT_GRADLE_PROPERTIES =
            listOf(
                "org.gradle.jvmargs=-Dfile.encoding=UTF-8 -XX:+HeapDumpOnOutOfMemoryError -XX:MaxMetaspaceSize=1024m",
                "org.gradle.parallel=true",
                "org.gradle.vfs.watch=false",
                "systemProp.org.gradle.configuration-cache.read-only=true",
                "kotlin.native.enableKlibsCrossCompilation=true",
                "kotlin.native.ignoreDisabledTargets=true",
            ) +
                System.getProperty("kotlin.compiler.plugin.devkit.test.gradle-properties")
                    .orEmpty()
                    .split(",")
        private val KOTLIN_REPOS =
            listOf(
                    "https://packages.jetbrains.team/maven/p/kt/bootstrap",
                    "https://packages.jetbrains.team/maven/p/kt/dev/",
                    "https://packages.jetbrains.team/maven/p/ij/intellij-dependencies/",
                    "https://packages.jetbrains.team/maven/p/compiler-plugin-dev-kit/eap",
                )
                .map(Repository::ofMaven)
    }
}

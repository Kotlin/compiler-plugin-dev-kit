package org.jetbrains.kotlin.compiler.plugin.devkit

import com.github.gmazzo.buildconfig.BuildConfigExtension
import org.gradle.api.Action
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectSet
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.invoke
import org.gradle.kotlin.dsl.kotlin
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.plugin.HasProject
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.kotlinToolingVersion
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion

/** A single Kotlin version that the consuming Gradle plugin's functional tests run against. */
public interface DevKitGradlePluginTestTarget : Named {
    override fun getName(): String = version.versionString

    public val version: KotlinToolingVersion
    public val testTask: TaskProvider<Test>
}

private class DevKitGradlePluginTestTargetImpl(
    override val version: KotlinToolingVersion,
    override val testTask: TaskProvider<Test>,
) : DevKitGradlePluginTestTarget {
    override fun equals(other: Any?): Boolean =
        other is DevKitGradlePluginTestTarget && other.version == version

    override fun hashCode(): Int = version.hashCode()
}

// GradleTestKitSupportExtension doesn't expose it publicly
private const val FUNCTIONAL_TEST_SOURCE_SET_NAME = "functionalTest"

public fun Project.DevKitGradlePluginTestTarget(
    version: KotlinToolingVersion
): DevKitGradlePluginTestTarget =
    DevKitGradlePluginTestTargetImpl(
        version,
        tasks.register<Test>("${version.versionName}FunctionalTest") {
            val functionalTestSourceSet =
                project.extensions
                    .getByType<SourceSetContainer>()
                    .named(FUNCTIONAL_TEST_SOURCE_SET_NAME)
            group = "verification"
            description = "Runs the functional tests against Kotlin ${version.versionString}."
            testClassesDirs = files(functionalTestSourceSet.map { it.output.classesDirs })
            classpath = files(functionalTestSourceSet.map { it.runtimeClasspath })
            dependsOn("deleteOldFuncTests", "installForFunctionalTest")
            mustRunAfter("test")
            maxParallelForks = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
            jvmArgs("-XX:+HeapDumpOnOutOfMemoryError", "-XX:MaxMetaspaceSize=1g")
            systemProperty("$DEV_KIT_GROUP.gradle.test.kotlin-version", version.versionString)
        },
    )

public abstract class GradlePluginConfigExtension(override val project: Project) : HasProject {
    public abstract val compilerPlugin: Property<Project>
    public abstract val pluginAnnotations: Property<Project>

    /**
     * Whether to generate the `PluginInfo` buildConfig class (id/artifact/annotations coordinates)
     * for a thin plugin that just wires [compilerPlugin] into consumer builds. Plugins with their
     * own, more involved wiring can turn this off.
     */
    public abstract val generatePluginInfo: Property<Boolean>

    /**
     * Whether to add an implementation dependency on `gradle-plugin-runtime`. Plugins that don't
     * use its APIs can turn this off to avoid an unused dependency leaking into their published
     * POM.
     */
    public abstract val addRuntimeDependency: Property<Boolean>

    /**
     * Which registered [testAgainst] version the generic `functionalTest` task delegates to.
     * Defaults to the Kotlin Gradle Plugin version currently applied to this build. If no
     * [testAgainst] entry matches, `functionalTest` is left alone.
     */
    public abstract val defaultTestVersion: Property<KotlinToolingVersion>

    /** The Kotlin versions to run the `functionalTest` suite against, one task per version. */
    public abstract val testAgainst: NamedDomainObjectSet<DevKitGradlePluginTestTarget>

    public abstract val functionalTestRepo: DirectoryProperty

    public fun testAgainst(
        version: KotlinToolingVersion,
        action: Action<DevKitGradlePluginTestTarget> = Action {},
    ): DevKitGradlePluginTestTarget =
        project.DevKitGradlePluginTestTarget(version).also(testAgainst::add).also(action::execute)
}

public class DevKitGradlePlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = project.run {
        // TODO should we apply SAM and assign plugins?
        pluginManager.apply {
            apply("org.jetbrains.kotlin.jvm")
            apply("com.github.gmazzo.buildconfig")
            apply("java-gradle-plugin")
            apply("com.autonomousapps.testkit")
        }
        val kotlinExtension = extensions.getByType<KotlinJvmProjectExtension>()
        val mainCompilation =
            kotlinExtension.target.compilations.getByName(KotlinCompilation.MAIN_COMPILATION_NAME)
        val functionalTestCompilation =
            kotlinExtension.target.compilations.getByName(FUNCTIONAL_TEST_SOURCE_SET_NAME)

        dependencies {
            mainCompilation.javaSourceSet.compileOnlyConfigurationName(kotlin("gradle-plugin-api"))
        }

        val pluginDevKit =
            project.extensions.create<GradlePluginConfigExtension>("pluginDevKit", project)
        val compilerPlugin = pluginDevKit.compilerPlugin
        val pluginAnnotations = pluginDevKit.pluginAnnotations
        pluginDevKit.generatePluginInfo.convention(true)
        pluginDevKit.addRuntimeDependency.convention(true)
        pluginDevKit.defaultTestVersion.convention(kotlinToolingVersion)
        pluginDevKit.functionalTestRepo.convention(layout.buildDirectory.dir("functionalTestRepo"))

        val kmpTargetProperty = "$DEV_KIT_GROUP.gradle.test.kmpTarget"
        val kmpTarget = providers.gradleProperty(kmpTargetProperty)
        pluginDevKit.testAgainst.configureEach {
            testTask {
                +FileSystemPropertyArgumentProvider(
                    "com.autonomousapps.plugin-under-test.repo",
                    pluginDevKit.functionalTestRepo,
                )
                +StringSystemPropertyArgumentProvider(
                    "com.autonomousapps.plugin-under-test.version",
                    project.provider { project.version.toString() },
                )
                +OptionalStringSystemPropertyArgumentProvider(kmpTargetProperty, kmpTarget)
            }
        }

        dependencies {
            addIf(
                pluginDevKit.addRuntimeDependency,
                mainCompilation.implementationConfigurationName,
                "$DEV_KIT_GROUP:gradle-plugin-runtime",
            )
            functionalTestCompilation.implementationConfigurationName(
                testFixtures("$DEV_KIT_GROUP:gradle-plugin-runtime")
            )
        }

        val allVersionedFunctionalTests =
            tasks.register<Test>("allVersionedFunctionalTests") {
                description = "Run all functional tests."
                group = "verification"
                enabled = false
                pluginDevKit.testAgainst.configureEach { this@register.dependsOn(testTask) }
            }

        tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME) { dependsOn(allVersionedFunctionalTests) }

        val functionalTestTask =
            tasks.named<Test>(FUNCTIONAL_TEST_SOURCE_SET_NAME) {
                enabled = false
                classpath = files()
                testClassesDirs = files()
            }

        afterEvaluate {
            val testVersion = pluginDevKit.defaultTestVersion.orNull
            if (pluginDevKit.testAgainst.isEmpty() && testVersion != null)
                pluginDevKit.testAgainst(testVersion)
            val target =
                pluginDevKit.testAgainst.find { it.version == testVersion }
                    ?: pluginDevKit.testAgainst.first()
            functionalTestTask { dependsOn(target.testTask) }
        }
        val pluginInfoClass =
            extensions
                .getByType<BuildConfigExtension>()
                .forClass(
                    "$group",
                    "PluginInfo",
                )

        // Deferred so consuming build scripts get a chance to call
        // `pluginDevKit.generatePluginInfo.set(false)` before we decide whether to generate.
        afterEvaluate {
            if (!pluginDevKit.generatePluginInfo.get()) return@afterEvaluate

            pluginInfoClass.apply {
                buildConfigField("String", "KOTLIN_PLUGIN_ID", "\"$group\"")

                if (compilerPlugin.isPresent)
                    buildConfigField("KOTLIN_PLUGIN_ARTIFACT") {
                        type("org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact")
                        expression(
                            compilerPlugin.map {
                                """
                            |org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact(
                            |  groupId = "$group",
                            |  artifactId = "${it.name}",
                            |  version = "$version",
                            |)
                            """
                                    .trimMargin()
                            }
                        )
                    }

                if (pluginAnnotations.isPresent)
                    buildConfigField("ANNOTATIONS_LIBRARY_COORDINATES") {
                        type("String")
                        expression(pluginAnnotations.map { "\"$group:${it.name}:$version\"" })
                    }

                // PLUGIN_INFO's type comes from gradle-plugin-runtime; only generate it if that's
                // actually on the classpath.
                if (
                    pluginDevKit.addRuntimeDependency.get() &&
                        compilerPlugin.isPresent &&
                        pluginAnnotations.isPresent
                ) {
                    buildConfigField("PLUGIN_INFO") {
                        type("$DEV_KIT_GROUP.DevKitPluginInfo")
                        expression(
                            """
                            |$DEV_KIT_GROUP.DevKitPluginInfo(
                            |  id = KOTLIN_PLUGIN_ID,
                            |  artifact = KOTLIN_PLUGIN_ARTIFACT,
                            |  libraryCoordinates = ANNOTATIONS_LIBRARY_COORDINATES,
                            |)
                            """
                                .trimMargin()
                        )
                    }
                }
            }
        }
    }
}

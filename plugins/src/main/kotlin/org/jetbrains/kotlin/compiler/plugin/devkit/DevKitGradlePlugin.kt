package org.jetbrains.kotlin.compiler.plugin.devkit

import com.github.gmazzo.buildconfig.BuildConfigExtension
import com.github.gmazzo.buildconfig.BuildConfigType
import org.gradle.api.Action
import org.gradle.api.ExtensiblePolymorphicDomainObjectContainer
import org.gradle.api.Named
import org.gradle.api.Plugin
import org.gradle.api.Project
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

private val FUNCTIONAL_TEST_ARGUMENT_PROVIDERS =
    setOf("RepoAndVersionArgumentProvider", "IncludeBuildReposArgumentProvider")

public fun Project.DevKitGradlePluginTestTarget(
    version: KotlinToolingVersion
): DevKitGradlePluginTestTarget =
    DevKitGradlePluginTestTargetImpl(
        version,
        tasks.register<Test>("${version.versionName}FunctionalTest"),
    )

public abstract class GradlePluginConfigExtension(override val project: Project) : HasProject {
    public abstract val compilerPlugin: Property<Project>
    public abstract val pluginAnnotations: Property<Project>

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
    public abstract val defaultTestTarget: Property<DevKitGradlePluginTestTarget>

    public fun defaultTestVersion(version: KotlinToolingVersion) {
        defaultTestTarget.set(testAgainst.find { it.version == version })
    }

    /** The Kotlin versions to run the `functionalTest` suite against, one task per version. */
    public abstract val testAgainst:
        ExtensiblePolymorphicDomainObjectContainer<DevKitGradlePluginTestTarget>

    init {
        testAgainst.registerFactory(DevKitGradlePluginTestTarget::class.java) {
            project.DevKitGradlePluginTestTarget(KotlinToolingVersion(it))
        }
    }

    public fun testAgainst(
        version: KotlinToolingVersion,
        action: Action<DevKitGradlePluginTestTarget> = Action {},
    ): DevKitGradlePluginTestTarget =
        testAgainst.maybeCreate(version.versionString).apply(action::execute)
}

public class DevKitGradlePlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = project.run {
        // TODO should we apply SAM and assign plugins?
        pluginManager.apply {
            apply("org.jetbrains.kotlin.jvm")
            apply("com.github.gmazzo.buildconfig")
            apply("java-gradle-plugin")
            apply(DevKitFunctionalTestPublishingPlugin::class.java)
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
        pluginDevKit.addRuntimeDependency.convention(true)
        pluginDevKit.defaultTestTarget.convention(pluginDevKit.testAgainst(kotlinToolingVersion))

        val kmpTargetProperty = "$DEV_KIT_GROUP.gradle.test.kmpTarget"
        val kmpTarget = providers.gradleProperty(kmpTargetProperty)
        pluginDevKit.testAgainst.configureEach {
            testTask {
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
                +NestedArgumentProvider(
                    (tasks.getByName(FUNCTIONAL_TEST_SOURCE_SET_NAME) as Test)
                        .jvmArgumentProviders
                        // Hacky: pick up repo and included-build repos from functionalTest
                        .filter { it.javaClass.simpleName in FUNCTIONAL_TEST_ARGUMENT_PROVIDERS }
                )
                +OptionalStringSystemPropertyArgumentProvider(kmpTargetProperty, kmpTarget)
            }
        }

        dependencies {
            addIf(
                pluginDevKit.addRuntimeDependency,
                mainCompilation.implementationConfigurationName,
                "$DEV_KIT_GROUP:gradle-plugin-runtime:$DEV_KIT_VERSION",
            )
            functionalTestCompilation.implementationConfigurationName(
                testFixtures("$DEV_KIT_GROUP:gradle-plugin-runtime:$DEV_KIT_VERSION")
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

        tasks.named<Test>(FUNCTIONAL_TEST_SOURCE_SET_NAME) {
            enabled = false
            classpath = files()
            testClassesDirs = files()
            dependsOn(pluginDevKit.defaultTestTarget.map { it.testTask })
        }

        extensions.getByType<BuildConfigExtension>().forClass(
            "$group",
            "PluginInfo",
        ) {
            buildConfigField("String", "KOTLIN_PLUGIN_ID", "\"$group\"")
            buildConfigField("KOTLIN_PLUGIN_ARTIFACT") {
                type("org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact")
                expression(
                    compilerPlugin.map {
                        """
                        |SubpluginArtifact(
                        |  groupId = "$group",
                        |  artifactId = "${it.name}",
                        |  version = "$version",
                        |)
                        """
                            .trimMargin()
                    }
                )
            }
            buildConfigField("ANNOTATIONS_LIBRARY_COORDINATES") {
                type("String")
                expression(pluginAnnotations.map { "\"$group:${it.name}:$version\"" })
            }
            buildConfigField("PLUGIN_INFO") {
                type.value(
                    project
                        .providerFlatMap {
                            if (pluginDevKit.addRuntimeDependency())
                                "$DEV_KIT_GROUP.DevKitPluginInfo"
                            else "kotlin.Nothing"
                        }
                        .map { BuildConfigType(it) }
                )
                expression(
                    project
                        .providerFlatMap {
                            if (
                                pluginDevKit.addRuntimeDependency.orNull == true &&
                                    compilerPlugin.isPresent &&
                                    pluginAnnotations.isPresent
                            )
                                """
                                |DevKitPluginInfo(
                                |  id = KOTLIN_PLUGIN_ID,
                                |  artifact = KOTLIN_PLUGIN_ARTIFACT,
                                |  libraryCoordinates = ANNOTATIONS_LIBRARY_COORDINATES,
                                |)
                                """
                                    .trimMargin()
                            else ""
                        }
                        .filter { it.isNotEmpty() }
                )
            }
        }
    }
}

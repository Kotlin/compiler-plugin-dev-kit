@file:OptIn(ExternalKotlinTargetApi::class)

package org.jetbrains.kotlin.compiler.plugin.devkit

import kotlin.properties.Delegates
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.NamedDomainObjectFactory
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.artifacts.type.ArtifactTypeDefinition.JAR_TYPE
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.internal.component.external.model.TestFixturesSupport
import org.gradle.kotlin.dsl.getByName
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.the
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.jetbrains.kotlin.gradle.ExternalKotlinTargetApi
import org.jetbrains.kotlin.gradle.dsl.HasConfigurableKotlinCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.AbstractKotlinTargetConfigurator
import org.jetbrains.kotlin.gradle.plugin.ClasspathTestRunSourceSupport
import org.jetbrains.kotlin.gradle.plugin.CompilationExecutionSource
import org.jetbrains.kotlin.gradle.plugin.HasProject
import org.jetbrains.kotlin.gradle.plugin.JvmClasspathTestRunSource
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation.Companion.MAIN_COMPILATION_NAME
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation.Companion.TEST_COMPILATION_NAME
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinTargetComponent
import org.jetbrains.kotlin.gradle.plugin.KotlinTargetTestRun
import org.jetbrains.kotlin.gradle.plugin.KotlinTargetWithTests
import org.jetbrains.kotlin.gradle.plugin.KotlinTargetWithTests.Companion.DEFAULT_TEST_RUN_NAME
import org.jetbrains.kotlin.gradle.plugin.ide.IdeMultiplatformImport
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinCompilationFactory
import org.jetbrains.kotlin.gradle.plugin.mpp.external.DecoratedExternalKotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.external.DecoratedExternalKotlinTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.external.ExternalKotlinCompilationDescriptor
import org.jetbrains.kotlin.gradle.plugin.mpp.external.ExternalKotlinTargetDescriptor
import org.jetbrains.kotlin.gradle.plugin.mpp.external.createCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.external.createExternalKotlinTarget
import org.jetbrains.kotlin.gradle.plugin.sources.AbstractKotlinSourceSet
import org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import org.jetbrains.kotlin.gradle.testing.KotlinTaskTestRun
import org.jetbrains.kotlin.gradle.testing.internal.KotlinTestsRegistry
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion

public const val TEST_FIXTURES_COMPILATION_NAME: String =
    TestFixturesSupport.TEST_FIXTURE_SOURCESET_NAME

public class DevKitTarget(delegate: Delegate, public val version: KotlinToolingVersion) :
    DecoratedExternalKotlinTarget(delegate),
    HasConfigurableKotlinCompilerOptions<KotlinJvmCompilerOptions>,
    Named,
    KotlinTargetWithTests<JvmClasspathTestRunSource, DevKitTestRun> {

    @Suppress("UNCHECKED_CAST")
    override val compilations: NamedDomainObjectContainer<DevKitCompilation> =
        objects.domainObjectContainer(DevKitCompilation::class.java, DevKitCompilationFactory(this))

    public val mainCompilation: DevKitCompilation = compilations.create(MAIN_COMPILATION_NAME)
    public val testFixturesCompilation: DevKitCompilation =
        compilations.create(TEST_FIXTURES_COMPILATION_NAME) {
            associateWith(this@DevKitTarget.mainCompilation)
        }
    public val testCompilation: DevKitCompilation =
        compilations.create(TEST_COMPILATION_NAME) {
            associateWith(this@DevKitTarget.testFixturesCompilation)
        }

    public val main: KotlinSourceSet
        get() = mainCompilation.defaultSourceSet

    public fun main(action: KotlinSourceSet.() -> Unit) {
        main.action()
    }

    public val testFixtures: KotlinSourceSet
        get() = testFixturesCompilation.defaultSourceSet

    public fun testFixtures(action: KotlinSourceSet.() -> Unit) {
        testFixtures.action()
    }

    public val test: KotlinSourceSet
        get() = testCompilation.defaultSourceSet

    public fun test(action: KotlinSourceSet.() -> Unit) {
        test.action()
    }

    override val testRuns: NamedDomainObjectContainer<DevKitTestRun> by lazy {
        project.objects.domainObjectContainer(
            DevKitTestRun::class.java,
            DevKitTestRunFactory(this),
        )
    }

    init {
        testRuns.create(DEFAULT_TEST_RUN_NAME)
        // ensure task is of type org.gradle.api.tasks.bundling.Jar
        project.locateOrRegisterTask<Jar>(lowerCamelCaseName(targetName, "jar")) {}
    }

    public val testTask: TaskProvider<KotlinJvmTest> =
        testRuns.getByName(DEFAULT_TEST_RUN_NAME).executionTask

    override val compilerOptions: KotlinJvmCompilerOptions
        get() = super.compilerOptions as KotlinJvmCompilerOptions

    override val publishable: Boolean
        get() = false

    override fun equals(other: Any?): Boolean = other is DevKitTarget && other.version == version

    override fun hashCode(): Int = version.hashCode()

    init {
        configureArtifactsTask()
    }

    override val kotlinComponents: Set<KotlinTargetComponent>
        get() = emptySet()
}

internal fun KotlinMultiplatformExtension.createDevKitTarget(version: KotlinToolingVersion) =
    createExternalKotlinTarget {
        targetName = version.versionName
        platformType = KotlinPlatformType.jvm
        targetFactory = ExternalKotlinTargetDescriptor.TargetFactory {
            DevKitTarget(it, version)
        }

        // Deprioritize them compared to the main jar
        for (configurable in
            listOf(
                apiElements,
                runtimeElements,
                sourcesElements,
                apiElementsPublished,
                runtimeElementsPublished,
                sourcesElementsPublished,
            )) {
            configurable.configure { _, configuration ->
                // TODO find way to disable these configurations instead
                configuration.attributes.attribute(
                    FakeKotlinCompilerVersion.attribute,
                    FakeKotlinCompilerVersion(version),
                )
            }
        }
        configureIdeImport {
            registerDependencyResolver(
                resolver = IdeMultiVersionCompilationBinaryDependencyResolver(),
                constraint = { sourceSet ->
                    (sourceSet as? AbstractKotlinSourceSet)?.compilations?.any {
                        it.target is DevKitTarget
                    } == true
                },
                phase = IdeMultiplatformImport.DependencyResolutionPhase.BinaryDependencyResolution,
                priority = IdeMultiplatformImport.Priority.high,
            )
        }
    }

public class DevKitCompilation(delegate: Delegate, public val javaSourceSet: SourceSet) :
    DecoratedExternalKotlinCompilation(delegate) {
    override val target: DevKitTarget = super.target as DevKitTarget

    @Suppress("UNCHECKED_CAST")
    override val compileTaskProvider:
        TaskProvider<out KotlinCompilationTask<KotlinJvmCompilerOptions>>
        get() =
            super.compileTaskProvider
                as TaskProvider<KotlinCompilationTask<KotlinJvmCompilerOptions>>

    public val compileJavaTaskProvider: TaskProvider<out JavaCompile>
        get() = target.project.tasks.named<JavaCompile>(javaSourceSet.compileJavaTaskName)

    override val processResourcesTaskName: String
        get() = super.processResourcesTaskName ?: error("Missing 'processResourcesTaskName'")

    override val runtimeDependencyConfigurationName: String
        get() =
            super.runtimeDependencyConfigurationName
                ?: error("$this: Missing 'runtimeDependencyConfigurationName'")

    override val runtimeDependencyFiles: FileCollection
        get() = super.runtimeDependencyFiles ?: error("$this: Missing 'runtimeDependencyFiles'")
}

internal class DevKitCompilationFactory(override val target: DevKitTarget) :
    KotlinCompilationFactory<DevKitCompilation>, HasProject {
    private val kotlinExtension = project.the<KotlinMultiplatformExtension>()
    private val javaSourceSets = project.the<JavaPluginExtension>().sourceSets
    override val itemClass: Class<DevKitCompilation>
        get() = DevKitCompilation::class.java

    override fun create(name: String): DevKitCompilation {
        val sourceSetName = defaultSourceSetName(name)

        val javaSourceSet = javaSourceSets.maybeCreate(sourceSetName)

        return target.createCompilation {
            compilationName = name
            defaultSourceSet = kotlinExtension.sourceSets.create(sourceSetName)
            compilationFactory = ExternalKotlinCompilationDescriptor.CompilationFactory {
                DevKitCompilation(it, javaSourceSet)
            }
            configure(::createResourcesTask)
            configure(::wireJavaSources)
        }
    }
}

internal class DevKitClasspathOnlyTestRunSource(
    override val classpath: FileCollection,
    override val testClassesDirs: FileCollection,
) : JvmClasspathTestRunSource

internal open class DevKitCompilationsTestRunSource(
    val classpathCompilations: Iterable<DevKitCompilation>,
    val testCompilations: Iterable<DevKitCompilation>,
) : JvmClasspathTestRunSource, HasProject {
    override val project
        get() = testCompilations.first().target.project

    override val testClassesDirs: FileCollection
        get() = files(testCompilations.map { it.output.classesDirs })

    override val classpath: FileCollection
        get() =
            files(
                (testCompilations + classpathCompilations).distinct().map {
                    it.output.allOutputs + it.runtimeDependencyFiles
                }
            )
}

internal class SingleDevKitCompilationTestRunSource(override val compilation: DevKitCompilation) :
    DevKitCompilationsTestRunSource(listOf(compilation), listOf(compilation)),
    CompilationExecutionSource<DevKitCompilation>

public class DevKitTestRun(testRunName: String, override val target: DevKitTarget) :
    KotlinTaskTestRun<JvmClasspathTestRunSource, KotlinJvmTest>(testRunName, target),
    ClasspathTestRunSourceSupport {
    override fun setExecutionSourceFrom(
        classpath: FileCollection,
        testClassesDirs: FileCollection,
    ) {
        executionSource = DevKitClasspathOnlyTestRunSource(classpath, testClassesDirs)
    }

    public fun setExecutionSourceFrom(
        classpathCompilations: Iterable<DevKitCompilation>,
        testClassesCompilations: Iterable<DevKitCompilation>,
    ) {
        classpathCompilations.forEach { requireCompilationOfTarget(it, target) }
        executionSource =
            DevKitCompilationsTestRunSource(classpathCompilations, testClassesCompilations)
    }

    public fun setExecutionSourceFrom(compilation: DevKitCompilation) {
        executionSource = SingleDevKitCompilationTestRunSource(compilation)
    }

    private var _executionSource: JvmClasspathTestRunSource by Delegates.notNull()

    override var executionSource: JvmClasspathTestRunSource
        get() = _executionSource
        private set(value) {
            setTestTaskClasspathAndClassesDirs(value.classpath, value.testClassesDirs)
            _executionSource = value
        }

    private fun setTestTaskClasspathAndClassesDirs(
        classpath: FileCollection,
        testClassesDirs: FileCollection,
    ) {
        executionTask.configure {
            this.classpath = classpath
            this.testClassesDirs = testClassesDirs
        }
    }
}

internal class DevKitTestRunFactory(private val target: DevKitTarget) :
    NamedDomainObjectFactory<DevKitTestRun> {
    override fun create(name: String): DevKitTestRun {
        return DevKitTestRun(name, target).apply {
            val testTaskOrProvider =
                target.project.registerTask<KotlinJvmTest>(testTaskName) { testTask ->
                    testTask.targetName = target.disambiguationClassifier
                    testTask.description = "Runs tests against Kotlin ${target.version}"
                    testTask.group = JavaBasePlugin.VERIFICATION_GROUP
                }

            // TODO hacky, maybe we should forego DevKitTestRun entirely
            @Suppress("INVISIBLE_SETTER")
            executionTask = testTaskOrProvider

            setExecutionSourceFrom(target.testCompilation)

            target.project.extensions
                .getByName<KotlinTestsRegistry>(KotlinTestsRegistry.PROJECT_EXTENSION_NAME)
                .registerTestTask(testTaskOrProvider)
        }
    }
}

private fun requireCompilationOfTarget(compilation: KotlinCompilation<*>, target: KotlinTarget) {
    require(compilation.target === target) {
        "Expected a compilation of target ${target.name}, " +
            "got the compilation ${compilation.name} of target ${compilation.target.name}"
    }
}

internal val KotlinTargetTestRun<*>.testTaskName: String
    get() =
        lowerCamelCaseName(
            target.disambiguationClassifier,
            name.takeIf { it != DEFAULT_TEST_RUN_NAME }?.removeSuffix("Test"),
            AbstractKotlinTargetConfigurator.testTaskNameSuffix,
        )

internal fun DevKitTarget.configureArtifactsTask() {
    val apiElements = configurations.getByName(apiElementsConfigurationName)
    val runtimeElements = configurations.findByName(runtimeElementsConfigurationName)
    createArtifact(apiElements, runtimeElements)
}

internal fun DevKitTarget.createArtifact(
    apiElements: Configuration,
    runtimeElements: Configuration?,
) {
    val mainCompilation = compilations.getByName(MAIN_COMPILATION_NAME)
    val jvmJarTask = createArtifactsTask { jar -> jar.from(mainCompilation.output.allOutputs) }

    createPublishArtifact(jvmJarTask, JAR_TYPE, apiElements, runtimeElements)
}

internal fun KotlinTarget.createArtifactsTask(configure: (Jar) -> Unit = {}): TaskProvider<Jar> {
    return tasks.named<Jar>(artifactsTaskName) {
        description = "Assembles an archive containing the main classes."
        group = BasePlugin.BUILD_GROUP
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true

        disambiguationClassifier?.let { classifier ->
            archiveAppendix.set(classifier.toLowerCaseAsciiOnly())
        }

        configure(this)
    }
}

private fun String.toLowerCaseAsciiOnly(): String {
    val builder = StringBuilder(length)
    for (c in this) {
        builder.append(if (c in 'A'..'Z') c.lowercaseChar() else c)
    }
    return builder.toString()
}

/**
 * Configures the project's 'assemble' task to depend on the provided [task].
 *
 * This ensures that when `./gradlew assemble` is run, the provided [task] will execute as part of
 * the build lifecycle.
 *
 * @param task The provider of the task to be added to the assemble lifecycle.
 */
internal fun Project.addToAssemble(task: TaskProvider<*>) {
    tasks.named(LifecycleBasePlugin.ASSEMBLE_TASK_NAME).configure { dependsOn(task) }
}

internal fun KotlinTarget.createPublishArtifact(
    artifactTask: TaskProvider<*>,
    artifactType: String,
    vararg elementsConfiguration: Configuration?,
): PublishArtifact {
    project.addToAssemble(artifactTask)

    val primaryConfig =
        elementsConfiguration.firstOrNull() ?: project.configurations.detachedConfiguration()

    val artifact =
        project.artifacts.add(primaryConfig.name, artifactTask) {
            builtBy(artifactTask)
            type = artifactType
        }

    elementsConfiguration.filterNotNull().forEach { configuration ->
        configuration.outgoing.artifacts.add(artifact)
        configuration.outgoing.attributes.attribute(
            ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE,
            artifactType,
        )
    }

    return artifact
}

package org.jetbrains.kotlin.compiler.plugin.devkit

import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectCollection
import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.domainObjectContainer
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.invoke
import org.gradle.kotlin.dsl.listProperty
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.registerIfAbsent
import org.gradle.kotlin.dsl.setProperty
import org.gradle.kotlin.dsl.the
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.jvm.JvmTargetValidationMode
import org.jetbrains.kotlin.gradle.plugin.HasProject
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/** How stable an IntelliJ IDEA build is; the Gradle-side mirror of `ide-test-runtime`'s enum. */
public enum class DevKitIdeBuildType {
    RELEASE,
    RC,
    EAP,
}

/** Which IntelliJ IDEA is downloaded. */
public enum class DevKitIdeaEdition(
    internal val productCode: String,
    internal val idPrefix: String,
) {
    ULTIMATE("IU", "ideaUltimate"),
    COMMUNITY("IC", "ideaCommunity"),
}

/**
 * Serializes the IDE test tasks against each other.
 *
 * Two IDEs cannot run side by side: IDE Starter's event bus server binds a fixed port and the
 * driver connects over it, so a second run either steals the port or fails to connect - never mind
 * two full IDEs and their Gradle imports competing for the machine.
 */
public abstract class DevKitIdeTestLock : BuildService<BuildServiceParameters.None>

/** One IDE that the IDE integration tests are run against, by a task of its own. */
public class DevKitIdeTarget
internal constructor(
    private val id: String,
    /** `<product code>:<version>[:<extra>]`, parsed back by `IdeUnderTest.parse`. */
    public val spec: String,
    public val testTask: TaskProvider<out Test>,
) : Named {
    override fun getName(): String = id

    override fun equals(other: Any?): Boolean = other is DevKitIdeTarget && other.name == id

    override fun hashCode(): Int = id.hashCode()
}

private const val IDE_TEST = "ideTest"

/**
 * The JVM the IDE tests compile and run against.
 *
 * Not negotiable, and not the module's own target: IDE Starter's jars are Java 21 and the Driver
 * SDK inlines into its callers, so anything lower fails to compile. Set on this compilation alone,
 * which is why a Gradle plugin module can host IDE tests while its own code still targets the
 * oldest supported Gradle's JVM.
 */
private val IDE_TEST_JVM_TARGET = JvmTarget.JVM_21

private val IDE_STARTER_MODULES =
    listOf(
        "com.jetbrains.intellij.tools:ide-starter-squashed",
        "com.jetbrains.intellij.tools:ide-starter-junit5",
        "com.jetbrains.intellij.tools:ide-starter-driver",
        "com.jetbrains.intellij.driver:driver-client",
        "com.jetbrains.intellij.driver:driver-sdk",
        "com.jetbrains.intellij.driver:driver-model",
    )

/**
 * Runs a compiler plugin's IDE integration tests: one real IDE launched per registered version,
 * opening a generated Gradle project that applies the plugin under test.
 *
 * Everything beyond the sources being analysed is wired here - IDE Starter, the Driver SDK, and the
 * plugin published into the functional test repository the generated project resolves from - so all
 * a module contributes is a `src/ideTest` subclass of `AbstractDevKitIdeTest`.
 *
 * The suite gets a source set of its own rather than sharing `functionalTest`, which is what lets
 * one module hold both: out of `functionalTest`, a Gradle plugin's own functional tests would be
 * run once per IDE and the IDE tests once per Kotlin version. It also keeps the 250 MB Starter jar
 * off the functional tests' classpath.
 *
 * @see GradlePluginConfigExtension.ideTest
 */
public open class IdeTestConfigExtension(final override val project: Project) : HasProject {
    private val sourceSets: SourceSetContainer = project.the()

    private val ideTest =
        sourceSets.create(IDE_TEST) {
            val main = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
            compileClasspath += main.output
            runtimeClasspath += output + main.output
        }

    /**
     * The Gradle plugin the generated test project applies, by plugin id. Defaults to the one this
     * module declares, and falls back to [GradlePluginConfigExtension.pluginPackage] - so a module
     * that declares no plugin of its own, or several, has to name it.
     */
    public val gradlePluginId: Property<String> = objects.property()

    /**
     * Version of IDE Starter and the Driver SDK.
     *
     * One version covers every IDE a module tests, since Starter drives the IDE from the outside.
     * Its jars do embed platform classes though, so a version far from the IDEs under test can
     * still break: keep it within a release or two of them.
     */
    public val starterVersion: Property<String> = objects.property(IDE_STARTER_VERSION)

    /**
     * Substrings that mark an IDE error as the plugin's fault. Defaults to [gradlePluginId], which
     * catches both the id itself and stack frames in the package it is named after.
     */
    public val errorPatterns: SetProperty<String> =
        objects
            .setProperty<String>()
            .convention(gradlePluginId.map { setOf(it) }.orElse(emptySet()))

    /**
     * Strings that must not appear in the IDE's log, empty by default. A plugin that logs when it
     * declines to install its extensions can name that message here, and have the test say so
     * rather than report every expected diagnostic as missing.
     */
    public val forbiddenLogPatterns: SetProperty<String> = objects.setProperty()

    /** Gradle distribution the IDE imports the generated project with. */
    public val gradleDistributionUrl: Property<String> =
        objects.property(
            "https://services.gradle.org/distributions/" +
                "gradle-${project.gradle.gradleVersion}-bin.zip"
        )

    /**
     * JUnit timeout per test. One test covers an IDE download, a Gradle import and a full analysis.
     */
    public val testTimeout: Property<String> = objects.property("15m")

    /** `gradle.properties` lines layered onto the generated test project. */
    public val testProjectProperties: ListProperty<String> = objects.listProperty<String>()

    /** The IDEs to run against, one `<name>IdeTest` task each. */
    public val ides: NamedDomainObjectCollection<DevKitIdeTarget> =
        objects.domainObjectContainer(DevKitIdeTarget::class)

    /** Which registered IDE `defaultIdeTest` runs. Defaults to the last IDE registered. */
    public val defaultIde: Property<DevKitIdeTarget> =
        objects.property<DevKitIdeTarget>().convention(provider { ides.lastOrNull() })

    /** @see defaultIde */
    public fun defaultIde(name: String) {
        defaultIde.set(ides.named(name))
    }

    /**
     * Registers an IntelliJ IDEA build. A [DevKitIdeBuildType.RELEASE] takes a marketing version
     * (`2026.1.1`); anything else takes a platform build number (`261.27258.27`), because a
     * prerelease is not in the release API that a marketing version is looked up in.
     *
     * This operation is idempotent.
     */
    public fun intellijIdea(
        version: String,
        buildType: DevKitIdeBuildType = DevKitIdeBuildType.RELEASE,
        edition: DevKitIdeaEdition = DevKitIdeaEdition.ULTIMATE,
    ): DevKitIdeTarget =
        register(
            id =
                lowerCamelCaseName(
                    edition.idPrefix,
                    version.replace('.', '_'),
                    buildType.name.lowercase().takeIf {
                        buildType != DevKitIdeBuildType.RELEASE
                    },
                ),
            spec = "${edition.productCode}:$version:${buildType.name.lowercase()}",
        )

    /**
     * Registers an Android Studio build, by its four-component version.
     *
     * [downloadFilePrefix] overrides the installer file name for the builds that don't follow the
     * default pattern - `android-studio-quail3-rc1` for `2026.1.3.5`, which Google's release list
     * is the place to look up. Left out, IDE Starter searches that list itself.
     *
     * This operation is idempotent.
     */
    public fun androidStudio(version: String, downloadFilePrefix: String? = null): DevKitIdeTarget =
        register(
            id = lowerCamelCaseName("androidStudio", version.replace('.', '_')),
            spec = "AS:$version" + downloadFilePrefix?.let { ":$it" }.orEmpty(),
        )

    /**
     * IntelliJ IDEA builds to run against, as a platform build number spelled only as precisely as
     * needed: `253` is the whole 2025.3 series, `253.29346.138` one build of it.
     *
     * Only the newest build of each platform baseline in each channel counts, unlike the same call
     * on [DevKitVersionsDsl]: a compiler target is cheap and every patch release may bundle a
     * different compiler, but an IDE is a gigabyte-and-a-half download and a multi-minute run, and
     * two patch releases of one baseline analyse the same code the same way.
     *
     * Android Studio is never registered here - its download coordinates don't follow from a
     * platform build, so it has to be named through [androidStudio].
     *
     * @param max upper bound on the baseline, infinite if null
     */
    public fun ideaVersions(
        min: IdeBuildNumber,
        max: Int? = null,
        includeRc: Boolean = false,
        includeEap: Boolean = false,
    ) {
        KnownVersions.ideTracksInRange(min, max, includeRc, includeEap)
            .entries
            .filter { it.key.product == "idea" }
            .groupBy { it.key.stability to it.value.baselineVersion }
            .values
            .map { line -> line.maxBy { it.value } }
            .forEach { (track, build) ->
                when (track.stability) {
                    // A release is looked up by marketing version, a prerelease by build number.
                    IdeStability.STABLE -> intellijIdea(track.version)
                    IdeStability.RC -> intellijIdea("$build", DevKitIdeBuildType.RC)
                    IdeStability.EAP -> intellijIdea("$build", DevKitIdeBuildType.EAP)
                }
            }
    }

    /** @see ideaVersions */
    public fun ideaVersions(
        min: String,
        max: Int? = null,
        includeRc: Boolean = false,
        includeEap: Boolean = false,
    ) {
        ideaVersions(IdeBuildNumber(min), max, includeRc, includeEap)
    }

    private fun register(id: String, spec: String): DevKitIdeTarget =
        ides.findByName(id)
            ?: DevKitIdeTarget(id, spec, tasks.register<Test>("${id}IdeTest")).also(ides::add)

    /**
     * Registers everything the `minIdeaVersion` family of properties asks for.
     *
     * The same properties that decide which compilers a plugin is built against decide which IDEs
     * it is tested in, so a build that widens its supported range gets the IDE test for free.
     * Anything declared in the `pluginDevKit { }` block is registered on top of these.
     */
    private fun registerSupportedIdes() {
        val min =
            providers.gradleProperty(MIN_IDEA_VERSION_PROPERTY).orNull?.let(::IdeBuildNumber)
                ?: return
        ideaVersions(
            min,
            providers.gradleProperty(MAX_IDEA_VERSION_PROPERTY).orNull?.toIntOrNull(),
            providers.gradleProperty(INCLUDE_IDEA_RC_PROPERTY).isTrue(),
            providers.gradleProperty(INCLUDE_IDEA_EAP_PROPERTY).isTrue(),
        )
    }

    private fun configureDependencies() = dependencies {
        ideTest.implementationConfigurationName("$DEV_KIT_GROUP:ide-test-runtime:$DEV_KIT_VERSION")
        for (module in IDE_STARTER_MODULES) {
            ideTest.implementationConfigurationName(starterVersion.map { "$module:$it" })
        }
        ideTest.runtimeOnlyConfigurationName(
            "org.junit.platform:junit-platform-launcher:$JUNIT_JUPITER_VERSION"
        )
    }

    /**
     * Pins this compilation to [IDE_TEST_JVM_TARGET], whatever the module targets otherwise.
     *
     * `--release` is what javac honours, so both halves do end up on the same target. KGP's
     * consistency check reads `targetCompatibility` instead, though, and a convention plugin
     * setting that for the whole module registers its action after this one and wins - so the check
     * is turned off rather than raced for. Nothing is lost: it would be comparing two values this
     * method has just made agree.
     */
    private fun configureJvmTarget() {
        tasks.named<KotlinCompile>(ideTest.getCompileTaskName("kotlin")) {
            compilerOptions.jvmTarget.set(IDE_TEST_JVM_TARGET)
            jvmTargetValidationMode.set(JvmTargetValidationMode.IGNORE)
        }
        tasks.named<JavaCompile>(ideTest.compileJavaTaskName) {
            options.release.set(IDE_TEST_JVM_TARGET.target.toInt())
        }
    }

    private fun configureTestTasks() {
        val lock =
            project.gradle.sharedServices.registerIfAbsent(
                "$DEV_KIT_GROUP.ideTestLock",
                DevKitIdeTestLock::class,
            ) {
                maxParallelUsages.set(1)
            }
        ides.configureEach {
            val spec = spec
            testTask {
                usesService(lock)
                val ideTestSourceSet =
                    project.extensions.getByType<SourceSetContainer>().named(IDE_TEST)
                group = "verification"
                description = "Runs the IDE integration tests against $spec."
                testClassesDirs = files(ideTestSourceSet.map { it.output.classesDirs })
                classpath = files(ideTestSourceSet.map { it.runtimeClasspath })
                dependsOn("deleteOldFuncTests", "installForFunctionalTest")
                useJUnitPlatform()
                maxHeapSize = "4g"
                // The IDE sets java.system.class.loader, which trips the JBR's CDS warning.
                jvmArgs("-Xlog:cds=off")
                // IDE Starter's ShutdownListener blocks every later test in the same JVM once one
                // has failed, retries included.
                forkEvery = 1
                systemProperty("junit.jupiter.execution.timeout.default", testTimeout.get())
                // Silences the Driver SDK's "Could not find installation home path" warning.
                systemProperty("idea.home.path", layout.projectDirectory.asFile.absolutePath)
                systemProperty("$IDE_PROPERTY_PREFIX.spec", spec)
                systemProperty("$DEV_KIT_PROPERTY_PREFIX.test.kotlin-version", kotlinToolingVersion)
                systemProperty(
                    "$DEV_KIT_PROPERTY_PREFIX.build.kotlin-version",
                    kotlinToolingVersion,
                )
                +OptionalStringSystemPropertyArgumentProvider(
                    "$IDE_PROPERTY_PREFIX.pluginId",
                    gradlePluginId,
                )
                +StringSystemPropertyArgumentProvider(
                    "$IDE_PROPERTY_PREFIX.errorPatterns",
                    errorPatterns.map { it.joinToString(",") },
                )
                +StringSystemPropertyArgumentProvider(
                    "$IDE_PROPERTY_PREFIX.forbiddenLogPatterns",
                    forbiddenLogPatterns.map { it.joinToString(",") },
                )
                +StringSystemPropertyArgumentProvider(
                    "$IDE_PROPERTY_PREFIX.gradleDistributionUrl",
                    gradleDistributionUrl,
                )
                +PropertiesSystemPropertyArgumentProvider(
                    "$DEV_KIT_PROPERTY_PREFIX.test.gradle-properties",
                    testProjectProperties,
                )
                +NestedArgumentProvider(
                    (tasks.getByName(FUNCTIONAL_TEST) as Test)
                        .jvmArgumentProviders
                        // Hacky: pick up repo and included-build repos from functionalTest
                        .filter { it.javaClass.simpleName in FUNCTIONAL_TEST_ARGUMENT_PROVIDERS }
                )
            }
        }

        tasks.register("listIdeTests") {
            description = "Prints the name of every registered IDE test task, one per line."
            group = "help"
            val taskNames = ides.map { it.testTask.name }
            doLast { taskNames.forEach(::println) }
        }

        tasks.register<DefaultTestTask>("allIdeTests") {
            description = "Runs the IDE integration tests against every registered IDE."
            group = "verification"
            enabled = false
            ides.configureEach { this@register.dependsOn(testTask) }
        }
        tasks.register<DefaultTestTask>("defaultIdeTest") {
            description = "Runs the IDE integration tests against one IDE."
            group = "verification"
            enabled = false
            dependsOn(defaultIde.map { it.testTask })
        }
    }

    internal fun configure() {
        registerSupportedIdes()
        configureDependencies()
        configureJvmTarget()
        configureTestTasks()
    }

    public companion object {
        internal const val IDE_PROPERTY_PREFIX: String = "$DEV_KIT_PROPERTY_PREFIX.ide"
    }
}

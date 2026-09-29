package org.jetbrains.kotlin.compiler.plugin.devkit

import javax.inject.Inject
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.io.path.name
import org.gradle.api.Plugin
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.initialization.Settings
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ProviderFactory
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.maven
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion

public class DevKitSettingsPlugin : Plugin<Settings> {
    override fun apply(target: Settings): Unit = target.run {
        pluginManagement.repositories.kotlinDevRepositories()
        gradle.settingsEvaluated {
            dependencyResolutionManagement.repositories.kotlinDevRepositories()
        }
        gradle.lifecycle.beforeProject {
            pluginManager.apply("$DEV_KIT_GROUP.functional-test-publishing")
        }
        extensions.create("pluginDevKit", DevKitSettingsExtension::class.java, this)
    }
}

public abstract class DevKitSettingsExtension(private val settings: Settings) :
    DevKitVersionsProperties {
    @get:Inject protected abstract val objects: ObjectFactory
    @get:Inject protected abstract val providers: ProviderFactory

    /**
     * Uses the [pathToVersionCatalog] relative to [pathToRoot] to create a [name] version catalog
     */
    public fun createVersionCatalogFrom(
        pathToRoot: String,
        pathToVersionCatalog: String = "gradle/libs.versions.toml",
        name: String = "libs",
    ): Unit = settings.run {
        dependencyResolutionManagement {
            val catalogPath = settings.rootDir.toPath() / pathToRoot / pathToVersionCatalog
            versionCatalogs {
                create(name) { from(objects.fileCollection().from(catalogPath)) }
            }
        }
    }

    /**
     * Includes the build at [path], and wires [Settings.rootProject]'s `:check` task to it. It also
     * adds it as a [companionLibrary]
     */
    public fun includeCompanionBuild(path: String) {
        includeBuildWithChecks(path)
        companionLibrary(if (path.isIncludedBuildPath) path else "./$path")
    }

    /** Includes the build at [path], and wires [Settings.rootProject]'s `:check` task to it. */
    public fun includeBuildWithChecks(path: String): Unit = settings.run {
        includeBuild(path)
        gradle.beforeProject {
            if (this == rootProject) {
                pluginManager.apply("base")
                tasks.named("check") {
                    dependsOn(gradle.includedBuild(Path(path).name).task(":check"))
                }
            }
        }
    }

    init {
        settings
            .listFromExtOrGradleProperty(COMPANION_LIBRARIES_PROPERTY)
            .filter { it.isIncludedBuildPath }
            .forEach { includeBuildWithChecks(it) }
    }

    public fun testAgainst(version: KotlinToolingVersion) {
        settings.gradle.beforeProject {
            extra.appendList(TEST_AGAINST_PROPERTY, version.toString())
        }
    }

    public fun testAgainst(version: String) {
        testAgainst(KotlinToolingVersion(version))
    }

    override fun cliVersions(
        min: KotlinVersion,
        max: KotlinVersion?,
        betaAndRc: BetaAndRc,
    ) {
        settings.gradle.beforeProject {
            extra[MIN_CLI_VERSION_PROPERTY] = min.toString()
            max?.let { extra[MAX_CLI_VERSION_PROPERTY] = max.toString() }
            extra[INCLUDE_BETA_AND_RC_PROPERTY] = betaAndRc.name
        }
    }

    override fun ideaVersions(
        min: String,
        max: Int?,
        includeRc: Boolean,
        includeEap: Boolean,
    ) {
        settings.gradle.beforeProject {
            extra[MIN_IDEA_VERSION_PROPERTY] = min
            max?.let { extra[MAX_IDEA_VERSION_PROPERTY] = max.toString() }
            extra[INCLUDE_IDEA_RC_PROPERTY] = includeRc.toString()
            extra[INCLUDE_IDEA_EAP_PROPERTY] = includeEap.toString()
        }
    }

    override fun useLatestDev() {
        settings.gradle.beforeProject {
            extra[USE_LATEST_DEV_PROPERTY] = "true"
        }
    }

    public fun defaultTestVersion(version: KotlinToolingVersion) {
        settings.gradle.beforeProject {
            extra[DEFAULT_TEST_VERSION_PROPERTY] = version.toString()
        }
    }

    public fun defaultTestVersion(version: String) {
        defaultTestVersion(KotlinToolingVersion(version))
    }

    public fun pluginPackage(pkg: String) {
        settings.gradle.beforeProject {
            extra[PLUGIN_PACKAGE_PROPERTY] = pkg
        }
    }

    public fun companionLibrary(library: String) {
        settings.gradle.beforeProject {
            extra.appendList(COMPANION_LIBRARIES_PROPERTY, library)
        }
    }
}

private fun RepositoryHandler.kotlinDevRepositories() {
    maven("https://redirector.kotlinlang.org/maven/bootstrap")
    maven("https://redirector.kotlinlang.org/maven/dev/")
    // Publications used by IJ
    // https://kotlinlang.slack.com/archives/C7L3JB43G/p1757001642402909
    maven("https://redirector.kotlinlang.org/maven/intellij-dependencies/")
}

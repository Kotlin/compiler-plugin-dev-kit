// Copyright (C) 2025 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.metro.gradle

import com.autonomousapps.kit.RootProject
import com.autonomousapps.kit.gradle.Repositories
import com.autonomousapps.kit.gradle.Repository
import org.jetbrains.kotlin.compiler.plugin.devkit.test.AbstractDevKitGradleProject

abstract class MetroProject(
  private val debug: Boolean = false,
  private val metroOptions: MetroOptionOverrides = MetroOptionOverrides(),
  private val reportsEnabled: Boolean = true,
  kotlinVersion: String? = null,
  multiplatform: Boolean = true,
) : AbstractDevKitGradleProject(kotlinVersion, multiplatform) {

  override val defaultImports = listOf("dev.zacsweers.metro.*")

  override val pluginUnderTest = GradlePlugins.metro

  override val extraGradleProperties = super.extraGradleProperties + METRO_TESTKIT_GRADLE_PROPERTIES

  override fun pluginConfigBlock(): String = buildMetroBlock()

  override fun repositories(defaults: List<Repository>): Repositories =
    Repositories(
      mutableListOf<Repository>().apply {
        addAll(defaults)
        add(Repository.ofMaven("https://packages.jetbrains.team/maven/p/kt/bootstrap"))
        add(Repository.ofMaven("https://packages.jetbrains.team/maven/p/kt/dev/"))
        add(Repository.ofMaven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies/"))
      }
    )

  /** Applies the Metro settings; used by fixtures that build custom project structures. */
  protected fun RootProject.Builder.withMetroSettings() = withDevKitSettings()

  /** Generates just the `metro { ... }` block content for use in custom build scripts. */
  fun buildMetroBlock(): String = buildString {
    appendLine(
      "@OptIn(dev.zacsweers.metro.gradle.DelicateMetroGradleApi::class, dev.zacsweers.metro.gradle.ExperimentalMetroGradleApi::class)"
    )
    appendLine("metro {")
    appendLine("  debug.set($debug)")
    if (reportsEnabled) {
      appendLine("  reportsDestination.set(layout.buildDirectory.dir(\"metro\"))")
    }
    val options = buildList {
      metroOptions.enableFullBindingGraphValidation?.let {
        add("compilerOptions.enable(\"enable-full-binding-graph-validation\")")
      }
      metroOptions.generateContributionProviders?.let {
        add("generateContributionProviders.set($it)")
      }
    }
    if (options.isNotEmpty()) {
      options.joinTo(this, separator = "\n", prefix = "  ")
    }
    appendLine("\n}")
  }

  private companion object {
    private val METRO_TESTKIT_GRADLE_PROPERTIES =
      listOf("org.gradle.jvmargs=-Xmx4g -Xms1g -Dfile.encoding=UTF-8 -XX:+HeapDumpOnOutOfMemoryError -XX:MaxMetaspaceSize=1024m")
  }
}

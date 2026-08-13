package org.jetbrains.kotlin.compiler.plugin.devkit

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.KotlinHierarchyDsl
import org.jetbrains.kotlin.gradle.plugin.KotlinHierarchyBuilder
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import org.jetbrains.kotlin.tooling.core.toKotlinVersion

public interface DevKitHierarchyDsl {
    @ExperimentalKotlinGradlePluginApi
    public fun KotlinHierarchyDsl.applyPluginDevKitHierarchyTemplate(
        template: KotlinHierarchyBuilder.() -> Unit
    ) {
        applyHierarchyTemplate {
            withSourceSetTree(
                KotlinSourceSetTree.main,
                KotlinSourceSetTree(TEST_FIXTURES_COMPILATION_NAME),
                KotlinSourceSetTree.test,
            )

            common {
                withCompilations { true }
                template()
            }
        }
    }

    @ExperimentalKotlinGradlePluginApi
    public fun KotlinHierarchyBuilder.withVersions(condition: (KotlinToolingVersion) -> Boolean) {
        withCompilations {
            val target = it.target
            target is DevKitTarget && condition(target.version)
        }
    }

    @ExperimentalKotlinGradlePluginApi
    public fun KotlinHierarchyBuilder.withVersions(range: ClosedRange<KotlinToolingVersion>) {
        withVersions { it in range }
    }

    @ExperimentalKotlinGradlePluginApi
    public fun KotlinHierarchyBuilder.groupVersions(
        name: String,
        condition: (KotlinToolingVersion) -> Boolean,
        build: KotlinHierarchyBuilder.() -> Unit = {},
    ) {
        group(name) {
            withVersions(condition)
            build()
        }
    }

    @ExperimentalKotlinGradlePluginApi
    public fun KotlinHierarchyBuilder.groupVersions(
        name: String,
        range: ClosedRange<KotlinToolingVersion>,
        build: KotlinHierarchyBuilder.() -> Unit = {},
    ) {
        group(name) {
            withVersions(range)
            build()
        }
    }

    @ExperimentalKotlinGradlePluginApi
    public fun KotlinHierarchyBuilder.pre(
        version: KotlinToolingVersion,
        name: String,
        build: KotlinHierarchyBuilder.() -> Unit = {},
    ) {
        group(name) {
            withVersions { it < version }
            build()
        }
    }

    @ExperimentalKotlinGradlePluginApi
    public fun KotlinHierarchyBuilder.preDev(
        version: KotlinVersion,
        name: String,
        build: KotlinHierarchyBuilder.() -> Unit = {},
    ) {
        group(name) {
            withVersions { it.toKotlinVersion() < version }
            build()
        }
    }

    @ExperimentalKotlinGradlePluginApi
    public fun KotlinHierarchyBuilder.post(
        version: KotlinToolingVersion,
        name: String,
        build: KotlinHierarchyBuilder.() -> Unit = {},
    ) {
        group(name) {
            withVersions { it >= version }
            build()
        }
    }

    @ExperimentalKotlinGradlePluginApi
    public fun KotlinHierarchyBuilder.postDev(
        version: KotlinVersion,
        name: String,
        build: KotlinHierarchyBuilder.() -> Unit = {},
    ) {
        group(name) {
            withVersions { it.toKotlinVersion() >= version }
            build()
        }
    }

    @ExperimentalKotlinGradlePluginApi
    public fun KotlinHierarchyBuilder.pre(
        version: String,
        name: String,
        build: KotlinHierarchyBuilder.() -> Unit = {},
    ) {
        pre(KotlinToolingVersion(version), name, build)
    }

    @ExperimentalKotlinGradlePluginApi
    public fun KotlinHierarchyBuilder.preDev(
        major: Int,
        minor: Int,
        name: String,
        build: KotlinHierarchyBuilder.() -> Unit = {},
    ) {
        preDev(KotlinVersion(major, minor), name, build)
    }

    @ExperimentalKotlinGradlePluginApi
    public fun KotlinHierarchyBuilder.preDev(
        major: Int,
        minor: Int,
        patch: Int,
        name: String,
        build: KotlinHierarchyBuilder.() -> Unit = {},
    ) {
        preDev(KotlinVersion(major, minor, patch), name, build)
    }

    @ExperimentalKotlinGradlePluginApi
    public fun KotlinHierarchyBuilder.post(
        version: String,
        name: String,
        build: KotlinHierarchyBuilder.() -> Unit = {},
    ) {
        post(KotlinToolingVersion(version), name, build)
    }

    @ExperimentalKotlinGradlePluginApi
    public fun KotlinHierarchyBuilder.postDev(
        major: Int,
        minor: Int,
        name: String,
        build: KotlinHierarchyBuilder.() -> Unit = {},
    ) {
        postDev(KotlinVersion(major, minor), name, build)
    }

    @ExperimentalKotlinGradlePluginApi
    public fun KotlinHierarchyBuilder.postDev(
        major: Int,
        minor: Int,
        patch: Int,
        name: String,
        build: KotlinHierarchyBuilder.() -> Unit = {},
    ) {
        postDev(KotlinVersion(major, minor, patch), name, build)
    }
}

package org.jetbrains.kotlin.compiler.plugin.devkit

import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import org.jetbrains.kotlin.tooling.core.toKotlinVersion

private val String.kotlinToolingVersionGroupName
    get() = replace(".", "").split("-").joinToString("") { it.capitalizeAsciiOnly() }
private val KotlinToolingVersion.groupName
    get() = toString().kotlinToolingVersionGroupName

private fun kotlinVersionGroupName(major: Int, minor: Int, patch: Int = 0) =
    "$major$minor${if (patch != 0) patch else ""}Dev"

private val KotlinVersion.groupName
    get() = kotlinVersionGroupName(major, minor, patch)

public interface DevKitHierarchyBuilder {
    public fun groupVersions(
        name: String,
        condition: (KotlinToolingVersion) -> Boolean,
    )

    public fun groupVersions(
        range: ClosedRange<KotlinToolingVersion>,
        name: String,
    ) {
        groupVersions(name) { it in range }
    }

    public fun pre(
        version: KotlinToolingVersion,
        name: String = "pre${version.groupName}",
    ) {
        groupVersions(name) { it < version }
    }

    public fun preDev(
        version: KotlinVersion,
        name: String = "pre${version.groupName}",
    ) {
        groupVersions(name) { it.toKotlinVersion() < version }
    }

    public fun post(
        version: KotlinToolingVersion,
        name: String = "post${version.groupName}",
    ) {
        groupVersions(name) { it >= version }
    }

    public fun postDev(
        version: KotlinVersion,
        name: String = "post${version.groupName}",
    ) {
        groupVersions(name) { it.toKotlinVersion() >= version }
    }

    public fun split(
        name: String,
        condition: (KotlinToolingVersion) -> Boolean,
    ) {
        groupVersions("pre$name", condition)
        groupVersions("post$name") { !condition(it) }
    }

    public fun split(
        version: KotlinToolingVersion,
        name: String = version.groupName,
    ) {
        pre(version, "pre$name")
        post(version, "post$name")
    }

    public fun splitDev(
        version: KotlinVersion,
        name: String = version.groupName,
    ) {
        preDev(version, "pre$name")
        postDev(version, "post$name")
    }

    public fun pre(
        version: String,
        name: String = "pre${version.kotlinToolingVersionGroupName}",
    ) {
        pre(KotlinToolingVersion(version), name)
    }

    public fun preDev(
        major: Int,
        minor: Int,
        name: String = "pre${kotlinVersionGroupName(major, minor)}",
    ) {
        preDev(KotlinVersion(major, minor), name)
    }

    public fun preDev(
        major: Int,
        minor: Int,
        patch: Int,
        name: String = "pre${kotlinVersionGroupName(major, minor, patch)}",
    ) {
        preDev(KotlinVersion(major, minor, patch), name)
    }

    public fun post(
        version: String,
        name: String = "post${version.kotlinToolingVersionGroupName}",
    ) {
        post(KotlinToolingVersion(version), name)
    }

    public fun postDev(
        major: Int,
        minor: Int,
        name: String = "post${kotlinVersionGroupName(major, minor)}",
    ) {
        postDev(KotlinVersion(major, minor), name)
    }

    public fun postDev(
        major: Int,
        minor: Int,
        patch: Int,
        name: String = "post${kotlinVersionGroupName(major, minor, patch)}",
    ) {
        postDev(KotlinVersion(major, minor, patch), name)
    }

    public fun split(
        version: String,
        name: String = version.kotlinToolingVersionGroupName,
    ) {
        split(KotlinToolingVersion(version), name)
    }

    public fun splitDev(
        major: Int,
        minor: Int,
        name: String = kotlinVersionGroupName(major, minor),
    ) {
        splitDev(KotlinVersion(major, minor), name)
    }

    public fun splitDev(
        major: Int,
        minor: Int,
        patch: Int,
        name: String = kotlinVersionGroupName(major, minor, patch),
    ) {
        splitDev(KotlinVersion(major, minor, patch), name)
    }
}

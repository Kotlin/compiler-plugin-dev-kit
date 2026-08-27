// Copied from Metro
// https://github.com/ZacSweers/metro/blob/f4fb09ea6b6894aa7816f02d52398d65f9be404d/compiler-compat/src/main/kotlin/dev/zacsweers/metro/compiler/compat/KotlinToolingVersion.kt

package org.jetbrains.kotlin.compiler.plugin.devkit

import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import org.jetbrains.kotlin.tooling.core.isDev
import org.jetbrains.kotlin.tooling.core.toKotlinVersion

@Deprecated(
    "Use KotlinToolingVersion directly",
    ReplaceWith("KotlinToolingVersion", "org.jetbrains.kotlin.tooling.core.KotlinToolingVersion"),
)
public typealias KotlinToolingVersion = KotlinToolingVersion

@Deprecated(
    "Use KotlinToolingVersion directly",
    ReplaceWith(
        "KotlinToolingVersion(kotlinVersionString)",
        "org.jetbrains.kotlin.tooling.core.KotlinToolingVersion",
    ),
)
public fun KotlinToolingVersion(kotlinVersionString: String): KotlinToolingVersion =
    KotlinToolingVersion(kotlinVersionString)

@Deprecated(
    "Use KotlinToolingVersion directly",
    ReplaceWith(
        "KotlinToolingVersion(kotlinVersion, classifier)",
        "org.jetbrains.kotlin.tooling.core.KotlinToolingVersion",
    ),
)
public fun KotlinToolingVersion(
    kotlinVersion: KotlinVersion,
    classifier: String? = null,
): KotlinToolingVersion =
    KotlinToolingVersion(kotlinVersion.major, kotlinVersion.minor, kotlinVersion.patch, classifier)

@Deprecated(
    "Use KotlinToolingVersion directly",
    ReplaceWith(
        "this.isDev",
        "org.jetbrains.kotlin.tooling.core.isDev",
    ),
)
public val KotlinToolingVersion.isDev: Boolean
    get() = isDev

public val KotlinToolingVersion.isIdeBuild: Boolean
    get() = classifier?.startsWith("ij", ignoreCase = true) == true

/**
 * The IDE branch an `-ij` build was cut on - `ij261` of `2.4.0-ij261-32` - or null for anything
 * else.
 *
 * Two builds sharing it are the same branch at different points, so their trailing numbers do order
 * them. Across branches those numbers mean nothing to each other.
 */
internal val KotlinToolingVersion.idePlatform: String?
    get() = classifier?.takeIf { isIdeBuild }?.substringBefore('-')

public fun KotlinToolingVersion.hasSameBaseVersionAs(other: KotlinToolingVersion): Boolean =
    toKotlinVersion() == other.toKotlinVersion()

package org.jetbrains.kotlin.compiler.plugin.devkit

import java.io.Serializable
import org.gradle.api.Named
import org.gradle.api.attributes.Attribute
import org.jetbrains.kotlin.gradle.plugin.HasProject
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion

internal abstract class KotlinCompilerVersion : Serializable, Named {
    override fun equals(other: Any?): Boolean = other is KotlinCompilerVersion && other.name == name

    override fun hashCode(): Int = name.hashCode()

    override fun toString(): String = name

    companion object {
        val attribute: Attribute<KotlinCompilerVersion> =
            Attribute.of("org.jetbrains.kotlin.compiler.version", KotlinCompilerVersion::class.java)
        const val NO_VERSION = "noVersion"
        context(_: HasProject)
        val noVersion: KotlinCompilerVersion
            get() = KotlinCompilerVersion(null)

        context(_: HasProject)
        operator fun invoke(version: KotlinToolingVersion?) =
            objects.named(KotlinCompilerVersion::class.java, version?.toString() ?: NO_VERSION)
    }
}

internal abstract class FakeKotlinCompilerVersion : Serializable, Named {
    override fun equals(other: Any?): Boolean =
        other is FakeKotlinCompilerVersion && other.name == name

    override fun hashCode(): Int = name.hashCode()

    override fun toString(): String = name

    companion object {
        val attribute: Attribute<FakeKotlinCompilerVersion> =
            Attribute.of(
                "org.jetbrains.kotlin.compiler.version.fake",
                FakeKotlinCompilerVersion::class.java,
            )

        context(_: HasProject)
        operator fun invoke(version: KotlinToolingVersion?) =
            objects.named(
                FakeKotlinCompilerVersion::class.java,
                version?.toString() ?: "noVersion",
            )
    }
}

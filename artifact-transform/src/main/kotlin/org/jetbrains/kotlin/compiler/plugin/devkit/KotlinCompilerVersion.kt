package org.jetbrains.kotlin.compiler.plugin.devkit

import java.io.Serializable
import org.gradle.api.attributes.Attribute
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion

public data class KotlinCompilerVersion(val version: KotlinToolingVersion?) : Serializable {
    public companion object {
        public val attribute: Attribute<KotlinCompilerVersion> =
            Attribute.of("org.jetbrains.kotlin.compiler.version", KotlinCompilerVersion::class.java)
        public val noVersion: KotlinCompilerVersion = KotlinCompilerVersion(null)
    }
}

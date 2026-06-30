package org.jetbrains.kotlin.compiler.plugin.devkit

import java.io.Serializable
import org.gradle.api.attributes.Attribute
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion

data class KotlinCompilerVersion(val version: KotlinToolingVersion?) : Serializable {
    companion object {
        val attribute =
            Attribute.of("org.jetbrains.kotlin.compiler.version", KotlinCompilerVersion::class.java)
        val none = KotlinCompilerVersion(null)
    }
}

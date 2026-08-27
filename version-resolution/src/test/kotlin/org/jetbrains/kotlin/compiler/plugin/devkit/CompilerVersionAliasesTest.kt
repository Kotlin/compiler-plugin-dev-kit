package org.jetbrains.kotlin.compiler.plugin.devkit

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CompilerVersionAliasesTest {
    private fun compareIdeBuilds(build1: String, build2: String): Int =
        IdeBuildNumber(build1).compareTo(IdeBuildNumber(build2))

    @Test
    fun `compare builds of different baselines`() {
        assertThat(compareIdeBuilds("253.29346.138", "261.23567.138")).isLessThan(0)
        assertThat(compareIdeBuilds("261.23567.138", "253.29346.138")).isGreaterThan(0)
    }

    @Test
    fun `compare builds of the same baseline`() {
        assertThat(compareIdeBuilds("261.23567.28", "261.23567.138")).isLessThan(0)
        assertThat(compareIdeBuilds("261.19799.20", "261.23567.28")).isLessThan(0)
        assertThat(compareIdeBuilds("261.23567.138", "261.23567.138")).isEqualTo(0)
    }

    @Test
    fun `exact build maps to the version it bundles`() {
        assertThat(CompilerVersionAliases.forIdeBuild(IdeBuildNumber("253.29346.138")))
            .isEqualTo(KotlinToolingVersion("2.3.20-ij253-45"))
        assertThat(CompilerVersionAliases.forIdeBuild(IdeBuildNumber("261.23567.138")))
            .isEqualTo(KotlinToolingVersion("2.4.0-ij261-32"))
    }

    @Test
    fun `unmapped build takes the highest mapping below it`() {
        assertThat(CompilerVersionAliases.forIdeBuild(IdeBuildNumber("261.99999.1")))
            .isEqualTo(KotlinToolingVersion("2.4.0-ij261-71"))
    }

    @Test
    fun `mappings never cross baselines`() {
        // Below every 262 mapping, so 261's must not stand in for it.
        assertThat(CompilerVersionAliases.forIdeBuild(IdeBuildNumber("262.1.1"))).isNull()
        assertThat(CompilerVersionAliases.forIdeBuild(IdeBuildNumber("999.1.1"))).isNull()
    }
}

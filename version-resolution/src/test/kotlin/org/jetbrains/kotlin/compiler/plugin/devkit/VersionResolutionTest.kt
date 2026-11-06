package org.jetbrains.kotlin.compiler.plugin.devkit

import com.google.common.truth.Truth.assertThat
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import org.junit.Test

class VersionResolutionTest {
    private fun resolve(current: String, vararg available: String): String? =
        VersionResolution.resolveVersion(
                KotlinToolingVersion(current),
                available.map(::KotlinToolingVersion),
            )
            ?.toString()

    /** Every 2.4.0 version an IDE-supporting plugin plausibly ships. */
    private val ij261Plugin =
        arrayOf(
            "2.4.0-dev-1645",
            "2.4.0-dev-2631",
            "2.4.0-ij261-32",
            "2.4.0-ij261-50",
            "2.4.0-ij261-64",
            "2.4.0-ij261-71",
            "2.4.0-Beta1",
            "2.4.0",
        )

    @Test
    fun `ide build takes its own branch, not the lowest same-base version`() {
        assertThat(resolve("2.4.0-ij261-64", *ij261Plugin)).isEqualTo("2.4.0-ij261-64")
        assertThat(resolve("2.4.0-ij261-32", *ij261Plugin)).isEqualTo("2.4.0-ij261-32")
        assertThat(resolve("2.4.0-ij261-71", *ij261Plugin)).isEqualTo("2.4.0-ij261-71")
    }

    @Test
    fun `ide build between two branch builds takes the older one`() {
        assertThat(resolve("2.4.0-ij261-60", *ij261Plugin)).isEqualTo("2.4.0-ij261-50")
    }

    @Test
    fun `ide build newer than every branch build takes the newest`() {
        assertThat(resolve("2.4.0-ij261-99", *ij261Plugin)).isEqualTo("2.4.0-ij261-71")
    }

    @Test
    fun `ide build never takes another branch`() {
        // ij262 is a different branch, so its numbers say nothing here: fall back to same-base.
        assertThat(resolve("2.4.0-ij261-64", "2.4.20-ij262-34", "2.4.0-dev-2631"))
            .isEqualTo("2.4.0-dev-2631")
    }

    @Test
    fun `ide build with no version of its branch takes its branch point`() {
        // ide-mappings.txt records 261's branch point as 2.4.0-dev-2631, so it wins over the
        // earlier same-base versions the plugin also ships.
        assertThat(resolve("2.4.0-ij261-64", "2.4.0-dev-1645", "2.4.0-dev-2631", "2.4.0"))
            .isEqualTo("2.4.0-dev-2631")
    }

    @Test
    fun `ide build of a later baseline takes that baseline's branch point`() {
        // ide-mappings.txt records 262's branch point as 2.4.20-dev-6724.
        assertThat(resolve("2.4.20-ij262-99", "2.4.20-dev-1435", "2.4.20-dev-6724"))
            .isEqualTo("2.4.20-dev-6724")
    }

    @Test
    fun `ide build of an unrecorded baseline falls back to the earliest same-base version`() {
        // ide-mappings.txt records no build of baseline 999, so ij999 has no branch point.
        assertThat(resolve("2.4.0-ij999-64", "2.4.0-dev-1645", "2.4.0-dev-2631", "2.4.0"))
            .isEqualTo("2.4.0-dev-1645")
    }

    @Test
    fun `ide build older than every branch build falls back to same-base`() {
        assertThat(resolve("2.4.0-ij261-20", "2.4.0-ij261-32", "2.4.0-ij261-50"))
            .isEqualTo("2.4.0-ij261-32")
    }

    @Test
    fun `branch point that selects nothing falls back to the same-base rule`() {
        // 2.4.0-dev-9999 is ahead of 261's branch point, so the branch point selects nothing.
        assertThat(resolve("2.4.0-ij261-64", "2.4.0-dev-9999")).isEqualTo("2.4.0-dev-9999")
    }

    @Test
    fun `dev build stays on its own base version`() {
        // The case the dev special-casing exists for: beta > dev in maturity ordering.
        assertThat(resolve("2.3.20-dev-7791", "2.3.20-Beta1", "2.3.20-dev-7000"))
            .isEqualTo("2.3.20-dev-7000")
    }

    @Test
    fun `dev build crosses base versions when its own has nothing`() {
        assertThat(resolve("2.4.20-dev-100", "2.4.0", "2.4.0-dev-2631")).isEqualTo("2.4.0")
    }

    @Test
    fun `stable build ignores dev versions`() {
        assertThat(resolve("2.4.0", "2.4.0-dev-2631", "2.3.20")).isEqualTo("2.3.20")
    }

    @Test
    fun `nothing compatible resolves to null`() {
        assertThat(resolve("2.3.20", "2.4.0", "2.4.20")).isNull()
    }
}

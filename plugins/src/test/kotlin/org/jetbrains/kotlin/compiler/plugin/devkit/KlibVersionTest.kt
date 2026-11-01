package org.jetbrains.kotlin.compiler.plugin.devkit

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion

class KlibVersionTest {
    @Test
    fun `reads a fragmented klib zip`() {
        val klib = zip("default/manifest" to "compiler_version=2.4.0-dev-8449\nabi_version=2.4.0")

        assertEquals(
            KotlinToolingVersion("2.4.0-dev-8449"),
            klibCompilerVersion(klib),
        )
    }

    @Test
    fun `reads a pre-1_9_20 klib zip`() {
        val klib = zip("manifest" to "compiler_version=1.9.10")

        assertEquals(KotlinToolingVersion("1.9.10"), klibCompilerVersion(klib))
    }

    @Test
    fun `reads the highest version of a common metadata jar`() {
        val klib =
            zip(
                "commonMain/default/manifest" to "compiler_version=2.2.20",
                "nativeMain/default/manifest" to "compiler_version=2.3.0-dev-1234",
            )

        assertEquals(KotlinToolingVersion("2.3.0-dev-1234"), klibCompilerVersion(klib))
    }

    @Test
    fun `reads an unpacked klib directory`() {
        val klib =
            createTempDirectory("klib").toFile().apply {
                File(this, "default").mkdirs()
                File(this, "default/manifest").writeText("compiler_version=2.2.20")
            }

        assertEquals(KotlinToolingVersion("2.2.20"), klibCompilerVersion(klib))
        klib.deleteRecursively()
    }

    @Test
    fun `returns null for a jar without a klib manifest`() {
        val jar = zip("org/example/Foo.class" to "")

        assertNull(klibCompilerVersion(jar))
    }

    @Test
    fun `returns null for a manifest without a compiler version`() {
        val klib = zip("default/manifest" to "abi_version=2.4.0")

        assertNull(klibCompilerVersion(klib))
    }

    @Test
    fun `returns null for a missing file`() {
        assertNull(klibCompilerVersion(File("does-not-exist")))
    }

    private fun zip(vararg entries: Pair<String, String>): File {
        val file = File.createTempFile("klib", ".klib").apply { deleteOnExit() }
        ZipOutputStream(file.outputStream()).use { zip ->
            for ((name, content) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return file
    }
}

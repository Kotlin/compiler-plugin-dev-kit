package org.jetbrains.kotlin.compiler.plugin.devkit

import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlin.test.builders.RegisteredDirectivesBuilder

public val versions: Set<KotlinToolingVersion> by lazy {
    val versions =
        System.getProperty("kotlin.compiler.plugin.devkit.versions", "$kotlinToolingVersion")
    versions.split(",").map { KotlinToolingVersion(it) }.toSortedSet()
}

public val kotlinToolingVersion: KotlinToolingVersion by lazy {
    KotlinToolingVersion(KotlinCompilerVersion.VERSION)
}

public class VersionClassifier(
    public val version: KotlinToolingVersion,
    override val compatibleWith: TestDumpClassifier<Root>?,
) : TestDumpClassifier<VersionClassifier.Root> {
    override val root: Root
        get() = Root

    override val name: String
        get() = version.toString().replace(".", "").replace("-", "_").lowercase()

    public companion object Root : TestDumpRoot<Root>("version") {
        override fun calculateClassifiers(): List<TestDumpClassifier<Root>> = buildList {
            var prev: VersionClassifier? = null
            // 2.4.20 -> 2.5.0 -> 2.5.20
            // highest version writes to unclassified file directly
            for (version in versions.reversed().drop(1)) {
                val versionClassifier = VersionClassifier(version, prev)
                add(versionClassifier)
                prev = versionClassifier
            }
        }

        public fun ofVersion(version: KotlinToolingVersion): VersionClassifier? =
            classifiers.firstOrNull {
                (it as VersionClassifier).version == version
            } as VersionClassifier?
    }
}

public fun RegisteredDirectivesBuilder.versionDumpClassifier() {
    TestDumpDirectives.DUMP_CLASSIFIER with
        generateSequence(VersionClassifier.ofVersion(kotlinToolingVersion)) {
                it.compatibleWith as VersionClassifier?
            }
            .toList()
            .asReversed()
}

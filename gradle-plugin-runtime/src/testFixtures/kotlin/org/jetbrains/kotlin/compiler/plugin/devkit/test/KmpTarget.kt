package org.jetbrains.kotlin.compiler.plugin.devkit.test

/**
 * A Kotlin multiplatform target a parameterized functional test can run against. [NATIVE_HOST]
 * resolves to the host's native target (e.g. `macosArm64`, `linuxX64`) — the only native target
 * compilable without a cross-compilation toolchain.
 */
public enum class KmpTarget(public val gradleTargetName: String) {
    JVM("jvm"),
    JS("js"),
    WASM_JS("wasmJs"),
    NATIVE_HOST(detectHostNativeTarget());

    /** The Gradle compile task for this target's main compilation, e.g. `compileKotlinJvm`. */
    public val compileTaskName: String =
        "compileKotlin" + gradleTargetName.replaceFirstChar { it.titlecase() }

    override fun toString(): String = gradleTargetName

    public companion object {
        /** Gradle/system property scoping [selectedTargets], forwarded by `DevKitGradlePlugin`. */
        public const val KMP_TARGET_PROPERTY: String =
            "kotlin.compiler.plugin.devkit.gradle.test.kmpTarget"

        /**
         * Targets to iterate, from [KMP_TARGET_PROPERTY]: unset → `[JVM]`; `all` → every entry;
         * `native_host` → [NATIVE_HOST]; else the entry whose [gradleTargetName] matches.
         */
        public fun selectedTargets(): List<KmpTarget> {
            val raw = System.getProperty(KMP_TARGET_PROPERTY).orEmpty()
            return when {
                raw.isEmpty() -> listOf(JVM)
                raw.equals("all", ignoreCase = true) -> entries.toList()
                raw.equals("native_host", ignoreCase = true) -> listOf(NATIVE_HOST)
                else ->
                    listOf(
                        entries.firstOrNull { it.gradleTargetName == raw }
                            ?: error(
                                "Unknown $KMP_TARGET_PROPERTY=$raw. Expected one of ${entries.map { it.gradleTargetName }} or `all` or `native_host`."
                            )
                    )
            }
        }
    }
}

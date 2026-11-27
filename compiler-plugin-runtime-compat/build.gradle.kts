import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion

plugins {
    kotlin("multiplatform")
    pluginDevKit("compiler-library")
    id("devkit.publishing")
    alias(libs.plugins.tapmoc)
}

description =
    "Compatiblity declarations for a Kotlin compiler plugin that supports many Kotlin versions."

tapmoc { java(8) }

kotlin {
    explicitApi()
    compilerOptions {
        freeCompilerArgs.addAll("-Xexpect-actual-classes", "-Xallow-kotlin-package")
    }
}

pluginDevKit {
    // `compiler-plugin-runtime` depends on this library, so the default dependency on it would be
    // a cycle.
    addRuntimeDependency = false

    providers.gradleProperty("kotlin.compiler.plugin.devkit.pinnedDevVersion").orNull?.let {
        testAgainst(it, forIde = false)
    }
    versionHierarchy {
        preDev(2, 3, 20, "preIrAnnotationAndFirNamedFunction")
        preDev(2, 3, "preSymbolUtils")
        groupVersions("preSourcelessDiagnostics") {
            it < KotlinToolingVersion("2.2.20-dev-6624") ||
                it == KotlinToolingVersion("2.2.20-Beta1")
        }
        split("2.2.20-dev-5598", "SessionHolder")
        pre("2.2.20-dev-5405", "preK1Deprecation")
        pre("2.2.20-dev-3262", "preKtDiagnosticsContainer")
        groupVersions("preDiagnosticReporterContext") {
            it < KotlinToolingVersion("2.2.0-dev-14230") ||
                it == KotlinToolingVersion("2.2.0-Beta1")
        }
        groupVersions("preStdlibContext") {
            it < KotlinToolingVersion("2.2.0-dev-13514") ||
                it == KotlinToolingVersion("2.2.0-Beta1")
        }
        groupVersions("preDirectDeclarationAccess") {
            it < KotlinToolingVersion("2.2.0-dev-11792") ||
                it == KotlinToolingVersion("2.2.0-Beta1")
        }
    }
}

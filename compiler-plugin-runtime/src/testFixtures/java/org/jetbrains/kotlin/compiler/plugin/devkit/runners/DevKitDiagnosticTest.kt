package org.jetbrains.kotlin.compiler.plugin.devkit.runners

import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.devkit.services.configurePlugin
import org.jetbrains.kotlin.test.FirParser
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.directives.FirDiagnosticsDirectives
import org.jetbrains.kotlin.test.runners.AbstractFirPhasedDiagnosticTest
import org.jetbrains.kotlin.test.services.EnvironmentBasedStandardLibrariesPathProvider

open class DevKitDiagnosticTest(
    private val registrar: () -> CompilerPluginRegistrar,
    parser: FirParser = FirParser.LightTree,
) : AbstractFirPhasedDiagnosticTest(parser) {
    private val config: MutableList<TestConfigurationBuilder.() -> Unit> = mutableListOf()

    protected fun configure(configuration: TestConfigurationBuilder.() -> Unit) {
        config.add(configuration)
    }

    override fun createKotlinStandardLibrariesPathProvider() =
        EnvironmentBasedStandardLibrariesPathProvider

    final override fun configure(builder: TestConfigurationBuilder) =
        with(builder) {
            super.configure(builder)

            defaultDirectives {
                versionDumpClassifier()
                +FirDiagnosticsDirectives.FIR_DUMP
                +FirDiagnosticsDirectives.DISABLE_GENERATED_FIR_TAGS
            }

            configurePlugin(registrar())
            config.forEach { it() }
        }
}

open class DevKitJvmDiagnosticTest(
    registrar: () -> CompilerPluginRegistrar,
    parser: FirParser = FirParser.LightTree,
) : DevKitDiagnosticTest(registrar, parser) {
    init {
        configure {
            defaultDirectives { jvmDefaults() }
        }
    }
}

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
    parser: FirParser = FirParser.LightTree
) : AbstractFirPhasedDiagnosticTest(parser) {
    override fun createKotlinStandardLibrariesPathProvider() = EnvironmentBasedStandardLibrariesPathProvider

    final override fun configure(builder: TestConfigurationBuilder) = super.configure(builder)

    context(builder: TestConfigurationBuilder)
    open fun config() = with(builder) {
        configure(this)

        defaultDirectives {
            +FirDiagnosticsDirectives.FIR_DUMP
            +FirDiagnosticsDirectives.DISABLE_GENERATED_FIR_TAGS
        }

        configurePlugin(registrar())
    }
}


open class DevKitJvmDiagnosticTest(
    registrar: () -> CompilerPluginRegistrar,
    parser: FirParser = FirParser.LightTree
) : DevKitDiagnosticTest(registrar, parser) {
    context(builder: TestConfigurationBuilder)
    override fun config() {
        super.config()
        defaultDirectives { jvmDefaults() }
    }
}

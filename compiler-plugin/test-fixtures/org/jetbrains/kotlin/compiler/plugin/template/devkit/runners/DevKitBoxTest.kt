package org.jetbrains.kotlin.compiler.plugin.template.devkit.runners

import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.template.devkit.services.configurePlugin
import org.jetbrains.kotlin.js.test.runners.AbstractJsTest
import org.jetbrains.kotlin.test.FirParser
import org.jetbrains.kotlin.test.builders.RegisteredDirectivesBuilder
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.directives.CodegenTestDirectives
import org.jetbrains.kotlin.test.directives.FirDiagnosticsDirectives
import org.jetbrains.kotlin.test.runners.codegen.AbstractFirBlackBoxCodegenTestBase
import org.jetbrains.kotlin.test.services.EnvironmentBasedStandardLibrariesPathProvider

open class DevKitJvmBoxTest(
    private val registrar: () -> CompilerPluginRegistrar,
    parser: FirParser = FirParser.LightTree
) : AbstractFirBlackBoxCodegenTestBase(parser) {
    override fun createKotlinStandardLibrariesPathProvider() = EnvironmentBasedStandardLibrariesPathProvider

    final override fun configure(builder: TestConfigurationBuilder) = with(builder) {
        super.configure(builder)

        defaultDirectives {
            boxTestDefaults()
            jvmDefaults()
        }

        configurePlugin(registrar())
        config()
    }

    context(builder: TestConfigurationBuilder)
    open fun config() {
    }
}

open class DevKitJsBoxTest(
    private val registrar: () -> CompilerPluginRegistrar,
    pathToTestDir: String,
    testGroupOutputDirPrefix: String = "box/",
    parser: FirParser = FirParser.LightTree,
) : AbstractJsTest(
    pathToTestDir = pathToTestDir,
    testGroupOutputDirPrefix = testGroupOutputDirPrefix,
    parser = parser,
) {
    override fun createKotlinStandardLibrariesPathProvider() = EnvironmentBasedStandardLibrariesPathProvider

    final override fun configure(builder: TestConfigurationBuilder) = with(builder) {
        super.configure(builder)

        defaultDirectives { boxTestDefaults() }

        configurePlugin(registrar())
        config()
    }

    context(builder: TestConfigurationBuilder)
    open fun config() {
    }
}

private fun RegisteredDirectivesBuilder.boxTestDefaults() {
    +CodegenTestDirectives.DUMP_IR
    +FirDiagnosticsDirectives.FIR_DUMP
}
package org.jetbrains.kotlin.compiler.plugin.devkit.runners

import org.jetbrains.kotlin.test.TargetBackend
import org.jetbrains.kotlin.test.TestInfrastructureInternals
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.runners.AbstractKotlinCompilerTest
import org.jetbrains.kotlin.test.runners.AbstractKotlinCompilerWithTargetBackendTest
import org.jetbrains.kotlin.test.services.EnvironmentBasedStandardLibrariesPathProvider

abstract class DevKitTest(
    vararg config: TestConfigurationBuilder.() -> Unit,
    val targetBackend: TargetBackend? = null,
) : AbstractKotlinCompilerTest() {
    constructor(
        delegate: AbstractKotlinCompilerTest,
        vararg initialConfig: TestConfigurationBuilder.() -> Unit,
    ) : this(
        delegate::configure,
        *initialConfig,
        targetBackend =
            when (delegate) {
                is AbstractKotlinCompilerWithTargetBackendTest -> delegate.targetBackend
                is DevKitTest -> delegate.targetBackend
                else -> null
            },
    )

    constructor(
        targetBackend: TargetBackend,
        vararg config: TestConfigurationBuilder.() -> Unit,
    ) : this(*config, targetBackend = targetBackend)

    private val config: MutableList<TestConfigurationBuilder.() -> Unit> = config.toMutableList()

    protected fun configure(configuration: TestConfigurationBuilder.() -> Unit) {
        config.add(configuration)
    }

    protected fun configure(vararg configuration: TestConfigurationBuilder.() -> Unit) {
        config.addAll(configuration)
    }

    override fun createKotlinStandardLibrariesPathProvider() =
        EnvironmentBasedStandardLibrariesPathProvider

    final override fun configure(builder: TestConfigurationBuilder) = config.forEach { it(builder) }

    @TestInfrastructureInternals
    final override fun configureInternal(builder: TestConfigurationBuilder) {
        super.configureInternal(builder)
        targetBackend?.let { myTargetBackend ->
            builder.globalDefaults {
                if (targetBackend == null) {
                    targetBackend = myTargetBackend
                } else {
                    require(targetBackend == myTargetBackend) {
                        """Target backend in configuration specified to $targetBackend but in
                          |AbstractKotlinCompilerWithTargetBackendTest parent it is set to $myTargetBackend"""
                            .trimMargin()
                    }
                }
            }
        }
    }
}

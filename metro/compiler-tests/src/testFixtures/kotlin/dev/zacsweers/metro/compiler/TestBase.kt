package dev.zacsweers.metro.compiler

import org.jetbrains.kotlin.test.TargetBackend
import org.jetbrains.kotlin.test.TestInfrastructureInternals
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.runners.AbstractKotlinCompilerTest
import org.jetbrains.kotlin.test.runners.AbstractKotlinCompilerWithTargetBackendTest
import org.jetbrains.kotlin.test.services.EnvironmentBasedStandardLibrariesPathProvider
import org.jetbrains.kotlin.test.services.KotlinStandardLibrariesPathProvider

abstract class TestBase : AbstractKotlinCompilerTest() {
  override fun createKotlinStandardLibrariesPathProvider(): KotlinStandardLibrariesPathProvider =
    EnvironmentBasedStandardLibrariesPathProvider

  open val targetBackend: TargetBackend? = null

  @TestInfrastructureInternals
  override fun configureInternal(builder: TestConfigurationBuilder) {
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

abstract class DelegatingTestBase(private val original: AbstractKotlinCompilerTest) : TestBase() {
  override val targetBackend: TargetBackend?
    get() = (original as? AbstractKotlinCompilerWithTargetBackendTest)?.targetBackend

  override fun configure(builder: TestConfigurationBuilder) = original.configure(builder)
}

abstract class TestBaseWithTargetBackend(override val targetBackend: TargetBackend) : TestBase()

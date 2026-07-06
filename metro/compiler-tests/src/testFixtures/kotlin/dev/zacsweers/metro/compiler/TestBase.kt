package dev.zacsweers.metro.compiler

import org.jetbrains.kotlin.test.TargetBackend
import org.jetbrains.kotlin.test.TestInfrastructureInternals
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.runners.AbstractKotlinCompilerTest
import org.jetbrains.kotlin.test.runners.AbstractKotlinCompilerWithTargetBackendTest
import org.jetbrains.kotlin.test.services.KotlinStandardLibrariesPathProvider as KTStandardLibrariesPathProvider

abstract class TestBase : AbstractKotlinCompilerTest() {
  open val targetBackend: TargetBackend? = null

  final override fun createKotlinStandardLibrariesPathProvider(): KTStandardLibrariesPathProvider =
    MetroDiagnosticsCompat.createKotlinStandardLibrariesPathProvider(
      createStandardLibrariesPathProvider()
    )

  open fun createStandardLibrariesPathProvider(): KotlinStandardLibrariesPathProvider =
    MetroDiagnosticsCompat.createStandardLibrariesPathProvider(
      super.createKotlinStandardLibrariesPathProvider()
    )

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

  // Default empty implementation
  override fun configure(builder: TestConfigurationBuilder) {}
}

abstract class DelegatingTestBase(private val original: AbstractKotlinCompilerTest) : TestBase() {
  override val targetBackend: TargetBackend?
    get() = (original as? AbstractKotlinCompilerWithTargetBackendTest)?.targetBackend

  override fun configure(builder: TestConfigurationBuilder) = original.configure(builder)
}

abstract class TestBaseWithTargetBackend(targetBackend: TargetBackend) :
  DelegatingTestBase(
    object : AbstractKotlinCompilerWithTargetBackendTest(targetBackend) {
      override fun configure(builder: TestConfigurationBuilder) {}
    }
  )

package dev.zacsweers.metro.compiler

import com.intellij.testFramework.TestDataFile
import org.jetbrains.kotlin.test.TargetBackend
import org.jetbrains.kotlin.test.runners.AbstractKotlinCompilerTest
import org.jetbrains.kotlin.test.runners.AbstractKotlinCompilerWithTargetBackendTest

// Replaced with version-specific implementations
abstract class TestBase {
  open val targetBackend: TargetBackend? = null

  open fun runTest(@TestDataFile filePath: String) {
    error("overridden in version-specific sources")
  }

  open fun createStandardLibrariesPathProvider(): KotlinStandardLibrariesPathProvider {
    error("overridden in version-specific sources")
  }

  abstract fun configure(builder: TestConfigurationBuilder)
}

fun TestBase.postConfigure(builder: TestConfigurationBuilder) {
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

abstract class DelegatingTestBase(private val original: AbstractKotlinCompilerTest) : TestBase() {
  override val targetBackend: TargetBackend?
    get() = (original as? AbstractKotlinCompilerWithTargetBackendTest)?.targetBackend

  override fun configure(builder: TestConfigurationBuilder) {
    builder.configureUsing(original)
  }
}

abstract class TestBaseWithTargetBackend(targetBackend: TargetBackend) :
  DelegatingTestBase(
    object : AbstractKotlinCompilerWithTargetBackendTest(targetBackend) {
      override fun configure(
        builder: org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
      ) {}
    }
  )

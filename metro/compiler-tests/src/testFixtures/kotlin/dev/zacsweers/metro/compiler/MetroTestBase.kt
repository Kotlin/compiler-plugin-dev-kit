package dev.zacsweers.metro.compiler

import org.jetbrains.kotlin.test.TargetBackend
import org.jetbrains.kotlin.test.runners.AbstractKotlinCompilerTest

abstract class MetroTestBaseWithTargetBackend(targetBackend: TargetBackend) :
  TestBaseWithTargetBackend(targetBackend), MetroDiagnosticsCompat by MetroDiagnosticsCompat {
  override fun createStandardLibrariesPathProvider(): KotlinStandardLibrariesPathProvider =
    ClasspathBasedStandardLibrariesPathProvider
}

abstract class MetroDelegatingTestBase(original: AbstractKotlinCompilerTest) :
  DelegatingTestBase(original), MetroDiagnosticsCompat by MetroDiagnosticsCompat {
  override fun createStandardLibrariesPathProvider(): KotlinStandardLibrariesPathProvider =
    ClasspathBasedStandardLibrariesPathProvider
}

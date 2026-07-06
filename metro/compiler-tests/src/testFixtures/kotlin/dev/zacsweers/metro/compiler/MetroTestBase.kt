package dev.zacsweers.metro.compiler

import org.jetbrains.kotlin.test.TargetBackend
import org.jetbrains.kotlin.test.runners.AbstractKotlinCompilerTest

abstract class MetroTestBaseWithTargetBackend(targetBackend: TargetBackend) :
  TestBaseWithTargetBackend(targetBackend) {
  override fun createStandardLibrariesPathProvider(): KotlinStandardLibrariesPathProvider =
    ClasspathBasedStandardLibrariesPathProvider
}

abstract class MetroDelegatingTestBase(original: AbstractKotlinCompilerTest) :
  DelegatingTestBase(original) {
  override fun createStandardLibrariesPathProvider(): KotlinStandardLibrariesPathProvider =
    ClasspathBasedStandardLibrariesPathProvider
}

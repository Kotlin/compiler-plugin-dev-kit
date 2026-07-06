// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.metro.compiler

import java.io.File
import org.jetbrains.kotlin.platform.wasm.WasmTarget

// KotlinStandardLibrariesPathProvider was changed to an interface in 2.3.20
// fullWasmStdlib() was added as a new abstract member in 2.4.0-Beta1
// kotlinTestWasmKLib() was added as a new abstract member in 2.4.0-Beta1
interface KotlinStandardLibrariesPathProvider {
  /** kotlin-stdlib.jar */
  fun runtimeJarForTests(): File

  /** kotlin-stdlib-jdk8.jar */
  fun runtimeJarForTestsWithJdk8(): File

  /** Jar with minimal version of kotlin stdlib (may be same as runtimeJarForTests) */
  fun minimalRuntimeJarForTests(): File

  /** kotlin-reflect.jar */
  fun reflectJarForTests(): File

  /** kotlin-test.jar */
  fun kotlinTestJarForTests(): File

  /** kotlin-script-runtime.jar */
  fun scriptRuntimeJarForTests(): File

  /** kotlin-annotations-jvm.jar */
  fun jvmAnnotationsForTests(): File

  /** compiler/testData/mockJDK/jre/lib/annotations.jar */
  fun getAnnotationsJar(): File

  /** kotlin-stdlib-js.klib */
  fun fullJsStdlib(): File

  /** Jar with minimal version of kotlin stdlib JS (may be same as fullJsStdlib) */
  fun defaultJsStdlib(): File

  /** kotlin-test-js.jar */
  fun kotlinTestJsKLib(): File

  /** kotlin-stdlib-common.klib */
  fun commonStdlibForTests(): File

  /** scriptingPlugin classpath jars */
  fun scriptingPluginFilesForTests(): Collection<File>

  fun webStdlibForTests(): File

  fun fullWasmStdlib(target: WasmTarget): File

  fun kotlinTestWasmKLib(target: WasmTarget): File
}

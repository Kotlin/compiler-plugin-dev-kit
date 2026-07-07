// Copyright (C) 2025 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.metro.compiler

import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.javaConstructor
import org.jetbrains.kotlin.compiler.plugin.devkit.runners.DevKitTest
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.test.backend.ir.IrBackendInput
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.builders.configureIrHandlersStep
import org.jetbrains.kotlin.test.directives.CodegenTestDirectives.IGNORE_DEXING
import org.jetbrains.kotlin.test.directives.ConfigurationDirectives.WITH_STDLIB
import org.jetbrains.kotlin.test.directives.JvmEnvironmentConfigurationDirectives.FULL_JDK
import org.jetbrains.kotlin.test.directives.JvmEnvironmentConfigurationDirectives.JVM_TARGET
import org.jetbrains.kotlin.test.model.BackendInputHandler
import org.jetbrains.kotlin.test.runners.codegen.AbstractFirLightTreeBlackBoxCodegenTest

@Suppress("UNCHECKED_CAST")
private val NoIrCompilationErrorsHandler =
  listOf("NoIrCompilationErrorsHandler")
    .firstNotNullOf {
      try {
        Class.forName("org.jetbrains.kotlin.test.backend.handlers.$it")
      } catch (_: ClassNotFoundException) {
        null
      }
    }
    .kotlin as KClass<BackendInputHandler<IrBackendInput>>?
    ?: error("Could not find NoIrCompilationErrorsHandler for the current kotlin version")

open class AbstractBoxTest(vararg config: TestConfigurationBuilder.() -> Unit) :
  DevKitTest(
    AbstractFirLightTreeBlackBoxCodegenTest(),
    {
      configurePlugin()

      useSourcePreprocessor(::KotlinTestImportPreprocessor)

      useMetaTestConfigurators(::MetroTestConfigurator)

      defaultDirectives {
        JVM_TARGET.with(JvmTarget.JVM_11)
        +FULL_JDK
        +WITH_STDLIB
        commonMetroTestDirectives()

        +IGNORE_DEXING // Avoids loading R8 from the classpath.
      }

      configureIrHandlersStep {
        useHandlers(
          // Errors in compiler plugin backend should fail test without running box function.
          { NoIrCompilationErrorsHandler.primaryConstructor!!.javaConstructor!!.newInstance(it) })
      }
    },
    *config,
  )

open class AbstractFastInitBoxTest :
  AbstractBoxTest({
    defaultDirectives { MetroDirectives.ENABLE_SWITCHING_PROVIDERS.with(true) }
  })

open class AbstractContributionProvidersBoxTest :
  AbstractBoxTest({
    defaultDirectives {
      // Only run on 2.3.20+ due to top-level requirements
      MetroDirectives.MIN_COMPILER_VERSION.with("2.3.20")
      MetroDirectives.GENERATE_CONTRIBUTION_HINTS.with(true)
      +MetroDirectives.GENERATE_CONTRIBUTION_HINTS_IN_FIR

      MetroDirectives.GENERATE_CONTRIBUTION_PROVIDERS.with(true)
    }
  })

open class AbstractIrOnlyClassesBoxTest :
  AbstractBoxTest({
    defaultDirectives {
      MetroDirectives.MIN_COMPILER_VERSION.with("2.4.20-dev-6138")
      MetroDirectives.GENERATE_CLASSES_IN_IR.with(true)
    }
  })

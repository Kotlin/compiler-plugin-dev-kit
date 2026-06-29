package org.jetbrains.kotlin.compiler.plugin.template.runners

import org.jetbrains.kotlin.compiler.plugin.devkit.runners.DevKitJvmDiagnosticTest
import org.jetbrains.kotlin.compiler.plugin.template.SimplePluginComponentRegistrar

open class AbstractJvmDiagnosticTest : DevKitJvmDiagnosticTest(::SimplePluginComponentRegistrar)

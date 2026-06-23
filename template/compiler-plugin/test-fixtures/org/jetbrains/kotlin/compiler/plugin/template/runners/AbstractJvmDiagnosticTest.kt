package org.jetbrains.kotlin.compiler.plugin.template.runners

import org.jetbrains.kotlin.compiler.plugin.template.SimplePluginComponentRegistrar
import org.jetbrains.kotlin.compiler.plugin.devkit.runners.DevKitJvmDiagnosticTest

open class AbstractJvmDiagnosticTest : DevKitJvmDiagnosticTest(::SimplePluginComponentRegistrar)
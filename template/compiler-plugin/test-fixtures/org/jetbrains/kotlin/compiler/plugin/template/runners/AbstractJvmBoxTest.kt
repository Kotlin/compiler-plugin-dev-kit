package org.jetbrains.kotlin.compiler.plugin.template.runners

import org.jetbrains.kotlin.compiler.plugin.devkit.runners.DevKitJvmBoxTest
import org.jetbrains.kotlin.compiler.plugin.template.SimplePluginComponentRegistrar

open class AbstractJvmBoxTest : DevKitJvmBoxTest(::SimplePluginComponentRegistrar)

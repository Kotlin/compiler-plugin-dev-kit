package org.jetbrains.kotlin.compiler.plugin.template.runners

import org.jetbrains.kotlin.compiler.plugin.template.SimplePluginComponentRegistrar
import org.jetbrains.kotlin.compiler.plugin.devkit.runners.DevKitJsBoxTest

open class AbstractJsBoxTest : DevKitJsBoxTest(
    ::SimplePluginComponentRegistrar,
)
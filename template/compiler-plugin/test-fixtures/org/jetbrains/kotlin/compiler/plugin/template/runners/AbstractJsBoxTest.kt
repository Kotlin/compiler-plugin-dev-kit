package org.jetbrains.kotlin.compiler.plugin.template.runners

import org.jetbrains.kotlin.compiler.plugin.template.SimplePluginComponentRegistrar
import org.jetbrains.kotlin.compiler.plugin.devkit.runners.DevKitJsBoxTest
import org.jetbrains.kotlin.compiler.plugin.template.TestCompatContext

open class AbstractJsBoxTest : DevKitJsBoxTest(
    ::SimplePluginComponentRegistrar,
    pathToTestDir = TestCompatContext.context.pathToTestDir,
)
package org.jetbrains.kotlin.compiler.plugin.devkit

import org.jetbrains.kotlin.KtFakeSourceElementKind

public actual fun pluginGeneratedSourceElementKind(marker: Any?): KtFakeSourceElementKind =
    KtFakeSourceElementKind.PluginGenerated

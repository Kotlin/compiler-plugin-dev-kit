package org.jetbrains.kotlin.compiler.plugin.devkit

import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.KtSourceElement

/** Compatibility function for [KtSourceElement.fakeElement] */
public expect fun KtSourceElement.fakeElementCompat(
    newKind: KtFakeSourceElementKind,
    offsetStrategy: KtSourceElementOffsetStrategy = KtSourceElementOffsetStrategy.Default,
): KtSourceElement

/**
 * Compatibility function for [KtFakeSourceElementKind.PluginGenerated] [marker] is ignored pre
 * 2.4.20 since it's neither supported nor needed if [marker] is `null`,
 * [KtFakeSourceElementKind.PluginGenerated.Default] is used
 */
public expect fun pluginGeneratedSourceElementKind(marker: Any? = null): KtFakeSourceElementKind

/**
 * Creates a plugin-generated fake source element using [fakeElementCompat]
 *
 * [marker] is ignored pre 2.4.20 since it's neither supported nor needed if [marker] is `null`,
 * [KtFakeSourceElementKind.PluginGenerated.Default] is used
 */
public fun KtSourceElement.pluginGenerated(
    marker: Any? = null,
    offsetStrategy: KtSourceElementOffsetStrategy = KtSourceElementOffsetStrategy.Default,
): KtSourceElement = fakeElementCompat(pluginGeneratedSourceElementKind(marker), offsetStrategy)

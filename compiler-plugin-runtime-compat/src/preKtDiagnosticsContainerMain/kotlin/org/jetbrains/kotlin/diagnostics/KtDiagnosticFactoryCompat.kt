@file:Suppress("UNUSED_PARAMETER")

package org.jetbrains.kotlin.diagnostics

import kotlin.reflect.KClass
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory

public fun KtDiagnosticFactory0(
    name: String,
    severity: Severity,
    defaultPositioningStrategy: AbstractSourceElementPositioningStrategy,
    psiType: KClass<*>,
    rendererFactory: BaseDiagnosticRendererFactory,
): KtDiagnosticFactory0 = KtDiagnosticFactory0(name, severity, defaultPositioningStrategy, psiType)

public fun <A> KtDiagnosticFactory1(
    name: String,
    severity: Severity,
    defaultPositioningStrategy: AbstractSourceElementPositioningStrategy,
    psiType: KClass<*>,
    rendererFactory: BaseDiagnosticRendererFactory,
): KtDiagnosticFactory1<A> =
    KtDiagnosticFactory1(name, severity, defaultPositioningStrategy, psiType)

public fun <A, B> KtDiagnosticFactory2(
    name: String,
    severity: Severity,
    defaultPositioningStrategy: AbstractSourceElementPositioningStrategy,
    psiType: KClass<*>,
    rendererFactory: BaseDiagnosticRendererFactory,
): KtDiagnosticFactory2<A, B> =
    KtDiagnosticFactory2(name, severity, defaultPositioningStrategy, psiType)

public fun <A, B, C> KtDiagnosticFactory3(
    name: String,
    severity: Severity,
    defaultPositioningStrategy: AbstractSourceElementPositioningStrategy,
    psiType: KClass<*>,
    rendererFactory: BaseDiagnosticRendererFactory,
): KtDiagnosticFactory3<A, B, C> =
    KtDiagnosticFactory3(name, severity, defaultPositioningStrategy, psiType)

public fun <A, B, C, D> KtDiagnosticFactory4(
    name: String,
    severity: Severity,
    defaultPositioningStrategy: AbstractSourceElementPositioningStrategy,
    psiType: KClass<*>,
    rendererFactory: BaseDiagnosticRendererFactory,
): KtDiagnosticFactory4<A, B, C, D> =
    KtDiagnosticFactory4(name, severity, defaultPositioningStrategy, psiType)

/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.diagnostics

import org.jetbrains.kotlin.AbstractKtSourceElement

// #### KtDiagnosticFactory0 ####

context(context: DiagnosticContext)
public fun DiagnosticReporter.reportOn(
    source: AbstractKtSourceElement?,
    factory: KtDiagnosticFactory0,
    positioningStrategy: AbstractSourceElementPositioningStrategy? = null,
) {
    reportOn(source, factory, context, positioningStrategy)
}

// #### KtDiagnosticFactory1 ####

context(context: DiagnosticContext)
public fun <A> DiagnosticReporter.reportOn(
    source: AbstractKtSourceElement?,
    factory: KtDiagnosticFactory1<A>,
    a: A,
    positioningStrategy: AbstractSourceElementPositioningStrategy? = null,
) {
    reportOn(source, factory, a, context, positioningStrategy)
}

// #### KtDiagnosticFactory2 ####

context(context: DiagnosticContext)
public fun <A, B> DiagnosticReporter.reportOn(
    source: AbstractKtSourceElement?,
    factory: KtDiagnosticFactory2<A, B>,
    a: A,
    b: B,
    positioningStrategy: AbstractSourceElementPositioningStrategy? = null,
) {
    reportOn(source, factory, a, b, context, positioningStrategy)
}

// #### KtDiagnosticFactory3 ####

context(context: DiagnosticContext)
public fun <A, B, C> DiagnosticReporter.reportOn(
    source: AbstractKtSourceElement?,
    factory: KtDiagnosticFactory3<A, B, C>,
    a: A,
    b: B,
    c: C,
    positioningStrategy: AbstractSourceElementPositioningStrategy? = null,
) {
    reportOn(source, factory, a, b, c, context, positioningStrategy)
}

// #### KtDiagnosticFactory4 ####
context(context: DiagnosticContext)
public fun <A, B, C, D> DiagnosticReporter.reportOn(
    source: AbstractKtSourceElement?,
    factory: KtDiagnosticFactory4<A, B, C, D>,
    a: A,
    b: B,
    c: C,
    d: D,
    positioningStrategy: AbstractSourceElementPositioningStrategy? = null,
) {
    reportOn(source, factory, a, b, c, d, context, positioningStrategy)
}

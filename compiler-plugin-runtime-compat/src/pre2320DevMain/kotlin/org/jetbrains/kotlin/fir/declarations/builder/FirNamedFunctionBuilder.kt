package org.jetbrains.kotlin.fir.declarations.builder

import org.jetbrains.kotlin.fir.declarations.FirNamedFunction

public typealias FirNamedFunctionBuilder = FirSimpleFunctionBuilder

public inline fun buildNamedFunction(init: FirNamedFunctionBuilder.() -> Unit): FirNamedFunction =
    buildSimpleFunction(init)

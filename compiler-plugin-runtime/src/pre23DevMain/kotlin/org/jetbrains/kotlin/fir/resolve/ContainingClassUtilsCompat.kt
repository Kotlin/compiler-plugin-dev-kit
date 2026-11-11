/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 * Copied from: https://github.com/JetBrains/kotlin/blob/3b369b6b56edf2410d595cac5a45b93a98d33449/compiler/fir/semantics.api/src/org/jetbrains/kotlin/fir/resolve/ContainingClassUtils.kt
 */

package org.jetbrains.kotlin.fir.resolve

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.getContainingClassSymbol as realGetContainingClassSymbol
import org.jetbrains.kotlin.fir.analysis.checkers.getContainingSymbol as realGetContainingSymbol
import org.jetbrains.kotlin.fir.containingClassLookupTag
import org.jetbrains.kotlin.fir.declarations.FirCallableDeclaration
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol

/**
 * The containing symbol is resolved using the declaration-site session. The semantics is similar to
 * [FirBasedSymbol<*>.getContainingClassSymbol][org.jetbrains.kotlin.fir.analysis.checkers.getContainingClassSymbol1],
 * see its KDoc for an example.
 */
@OptIn(SymbolInternals::class)
public fun FirCallableDeclaration.getContainingClass(): FirRegularClass? =
    this.containingClassLookupTag()?.let { lookupTag ->
        lookupTag.toRegularClassSymbol(moduleData.session)?.fir
    }

/**
 * Returns the ClassLikeDeclaration where the Fir object has been defined or null if no proper
 * declaration has been found. The containing symbol is resolved using the declaration-site session.
 * For example:
 * ```kotlin
 * expect class MyClass {
 *     fun test() // (1)
 * }
 *
 * actual class MyClass {
 *     actual fun test() {} // (2)
 * }
 * ```
 *
 * Calling [getContainingClassSymbol] for the symbol of `(1)` will return `expect class MyClass`,
 * but calling it for `(2)` will give `actual class MyClass`.
 */
public fun FirBasedSymbol<*>.getContainingClassSymbol(): FirClassLikeSymbol<*>? =
    realGetContainingClassSymbol()

/**
 * Returns the containing class or file if the callable is top-level. The containing symbol is
 * resolved using the declaration-site session.
 */
public fun FirCallableSymbol<*>.getContainingSymbol(session: FirSession): FirBasedSymbol<*>? =
    realGetContainingSymbol(session)

/** The containing symbol is resolved using the declaration-site session. */
public fun FirDeclaration.getContainingClassSymbol(): FirClassLikeSymbol<*>? =
    realGetContainingClassSymbol()

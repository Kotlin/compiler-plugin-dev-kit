/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 * Adapted from: https://github.com/JetBrains/kotlin/blob/e1d2b53e28b9ac6eaf271bf1e20723b3bd07549e/compiler/fir/providers/src/org/jetbrains/kotlin/fir/resolve/ToSymbolUtils.kt
 */

package org.jetbrains.kotlin.fir.resolve

import org.jetbrains.kotlin.fir.SessionHolder
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassifierSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirTypeAliasSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirTypeParameterSymbol
import org.jetbrains.kotlin.fir.types.ConeClassLikeLookupTag
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.ConeClassifierLookupTag
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.name.ClassId

// ----------------------------------------------- lookup tag
// -----------------------------------------------

/** @see toSymbol */
context(sessionHolder: SessionHolder)
public fun ConeClassifierLookupTag.toSymbol(): FirClassifierSymbol<*>? {
    return toSymbol(useSiteSession = sessionHolder.session)
}

context(sessionHolder: SessionHolder)
public fun ConeClassifierLookupTag.toClassLikeSymbol(): FirClassLikeSymbol<*>? {
    return toClassLikeSymbol(useSiteSession = sessionHolder.session)
}

context(sessionHolder: SessionHolder)
public fun ConeClassifierLookupTag.toRegularClassSymbol(): FirRegularClassSymbol? {
    return toRegularClassSymbol(useSiteSession = sessionHolder.session)
}

/** @see toSymbol */
context(sessionHolder: SessionHolder)
public fun ConeClassLikeLookupTag.toSymbol(): FirClassLikeSymbol<*>? {
    return toSymbol(useSiteSession = sessionHolder.session)
}

/** @see toSymbol */
context(sessionHolder: SessionHolder)
public fun ConeClassLikeLookupTag.toClassSymbol(): FirClassSymbol<*>? {
    return toClassSymbol(session = sessionHolder.session)
}

/** @see toSymbol */
context(sessionHolder: SessionHolder)
public fun ConeClassLikeLookupTag.toRegularClassSymbol(): FirRegularClassSymbol? {
    return toRegularClassSymbol(session = sessionHolder.session)
}

context(sessionHolder: SessionHolder)
public fun ConeClassLikeLookupTag.toTypeAliasSymbol(): FirTypeAliasSymbol? {
    return toTypeAliasSymbol(useSiteSession = sessionHolder.session)
}

// ----------------------------------------------- cone type (without expansion)
// -----------------------------------------------

context(sessionHolder: SessionHolder)
public fun ConeClassLikeType.toSymbol(): FirClassLikeSymbol<*>? {
    return toSymbol(session = sessionHolder.session)
}

context(sessionHolder: SessionHolder)
public fun ConeKotlinType.toSymbol(): FirClassifierSymbol<*>? {
    return toSymbol(session = sessionHolder.session)
}

context(sessionHolder: SessionHolder)
public fun ConeKotlinType.toClassLikeSymbol(): FirClassLikeSymbol<*>? {
    return toClassLikeSymbol(session = sessionHolder.session)
}

context(sessionHolder: SessionHolder)
public fun ConeKotlinType.toTypeAliasSymbol(): FirTypeAliasSymbol? {
    return toTypeAliasSymbol(session = sessionHolder.session)
}

context(sessionHolder: SessionHolder)
public fun ConeKotlinType.toTypeParameterSymbol(): FirTypeParameterSymbol? {
    return toTypeParameterSymbol(session = sessionHolder.session)
}

// ----------------------------------------------- cone type (with expansion)
// -----------------------------------------------

context(sessionHolder: SessionHolder)
public fun ConeKotlinType.toClassSymbol(): FirClassSymbol<*>? {
    return toClassSymbol(session = sessionHolder.session)
}

context(sessionHolder: SessionHolder)
public fun ConeClassLikeType.toClassSymbol(): FirClassSymbol<*>? {
    return toClassSymbol(session = sessionHolder.session)
}

context(sessionHolder: SessionHolder)
public fun ConeKotlinType.toRegularClassSymbol(): FirRegularClassSymbol? {
    return toRegularClassSymbol(session = sessionHolder.session)
}

context(sessionHolder: SessionHolder)
public fun ConeClassLikeType.toRegularClassSymbol(): FirRegularClassSymbol? {
    return toRegularClassSymbol(session = sessionHolder.session)
}

// ----------------------------------------------- ClassId
// -----------------------------------------------

context(sessionHolder: SessionHolder)
public fun ClassId.toSymbol(): FirClassifierSymbol<*>? {
    return toSymbol(session = sessionHolder.session)
}

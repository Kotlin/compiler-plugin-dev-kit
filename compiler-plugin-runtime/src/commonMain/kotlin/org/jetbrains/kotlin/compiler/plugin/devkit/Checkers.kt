@file:Suppress("UnnecessaryOptInAnnotation")
@file:OptIn(DeprecatedForRemovalCompilerApi::class, ExperimentalContracts::class)

package org.jetbrains.kotlin.compiler.plugin.devkit

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import org.jetbrains.kotlin.DeprecatedForRemovalCompilerApi
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.SessionHolder
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirDeclarationChecker
import org.jetbrains.kotlin.fir.analysis.checkers.type.FirTypeChecker
import org.jetbrains.kotlin.fir.declarations.FirAnonymousFunction
import org.jetbrains.kotlin.fir.declarations.FirAnonymousInitializer
import org.jetbrains.kotlin.fir.declarations.FirAnonymousObject
import org.jetbrains.kotlin.fir.declarations.FirBackingField
import org.jetbrains.kotlin.fir.declarations.FirCallableDeclaration
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.declarations.FirClassLikeDeclaration
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirEnumEntry
import org.jetbrains.kotlin.fir.declarations.FirFile
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirPropertyAccessor
import org.jetbrains.kotlin.fir.declarations.FirReceiverParameter
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.FirReplSnippet
import org.jetbrains.kotlin.fir.declarations.FirScript
import org.jetbrains.kotlin.fir.declarations.FirTypeAlias
import org.jetbrains.kotlin.fir.declarations.FirTypeParameter
import org.jetbrains.kotlin.fir.declarations.FirValueParameter
import org.jetbrains.kotlin.fir.resolve.SessionHolder as ResolveSessionHolder
import org.jetbrains.kotlin.fir.types.FirFunctionTypeRef
import org.jetbrains.kotlin.fir.types.FirIntersectionTypeRef
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.FirTypeRef

public interface FirDeclarationsCheckerCompatMethods<D : FirDeclaration> {
    public fun check(declaration: D, context: CheckerContext, reporter: DiagnosticReporter)

    context(context: CheckerContext, reporter: DiagnosticReporter)
    public fun check(declaration: D)
}

public abstract class FirDeclarationCheckerCompat<D : FirDeclaration>(mppKind: MppCheckerKind) :
    FirDeclarationChecker<D>(mppKind), FirDeclarationsCheckerCompatMethods<D> {
    override fun check(declaration: D, context: CheckerContext, reporter: DiagnosticReporter) {
        context(context, reporter) { check(declaration) }
    }

    // Hide the default implementation from 2.2.0-Beta2..<2.2.20, otherwise it could result in
    // infinite recursion
    context(context: CheckerContext, reporter: DiagnosticReporter)
    abstract override fun check(declaration: D)
}

public typealias FirBasicDeclarationCheckerCompat = FirDeclarationCheckerCompat<FirDeclaration>

public typealias FirCallableDeclarationCheckerCompat =
    FirDeclarationCheckerCompat<FirCallableDeclaration>

public typealias FirFunctionCheckerCompat = FirDeclarationCheckerCompat<FirFunction>

public typealias FirNamedFunctionCheckerCompat = FirDeclarationCheckerCompat<FirNamedFunction>

public typealias FirPropertyCheckerCompat = FirDeclarationCheckerCompat<FirProperty>

public typealias FirClassLikeCheckerCompat = FirDeclarationCheckerCompat<FirClassLikeDeclaration>

public typealias FirClassCheckerCompat = FirDeclarationCheckerCompat<FirClass>

public typealias FirRegularClassCheckerCompat = FirDeclarationCheckerCompat<FirRegularClass>

public typealias FirConstructorCheckerCompat = FirDeclarationCheckerCompat<FirConstructor>

public typealias FirFileCheckerCompat = FirDeclarationCheckerCompat<FirFile>

public typealias FirScriptCheckerCompat = FirDeclarationCheckerCompat<FirScript>

public typealias FirReplSnippetCheckerCompat = FirDeclarationCheckerCompat<FirReplSnippet>

public typealias FirTypeParameterCheckerCompat = FirDeclarationCheckerCompat<FirTypeParameter>

public typealias FirTypeAliasCheckerCompat = FirDeclarationCheckerCompat<FirTypeAlias>

public typealias FirAnonymousFunctionCheckerCompat =
    FirDeclarationCheckerCompat<FirAnonymousFunction>

public typealias FirPropertyAccessorCheckerCompat = FirDeclarationCheckerCompat<FirPropertyAccessor>

public typealias FirBackingFieldCheckerCompat = FirDeclarationCheckerCompat<FirBackingField>

public typealias FirValueParameterCheckerCompat = FirDeclarationCheckerCompat<FirValueParameter>

public typealias FirEnumEntryCheckerCompat = FirDeclarationCheckerCompat<FirEnumEntry>

public typealias FirAnonymousObjectCheckerCompat = FirDeclarationCheckerCompat<FirAnonymousObject>

public typealias FirAnonymousInitializerCheckerCompat =
    FirDeclarationCheckerCompat<FirAnonymousInitializer>

public typealias FirReceiverParameterCheckerCompat =
    FirDeclarationCheckerCompat<FirReceiverParameter>

public interface FirTypeCheckerCompatMethods<T : FirTypeRef> {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    public fun check(typeRef: T)

    public fun check(typeRef: T, context: CheckerContext, reporter: DiagnosticReporter)
}

public abstract class FirTypeCheckerCompat<T : FirTypeRef>(mppKind: MppCheckerKind) :
    FirTypeChecker<T>(mppKind), FirTypeCheckerCompatMethods<T> {

    override fun check(typeRef: T, context: CheckerContext, reporter: DiagnosticReporter) {
        context(context, reporter) { check(typeRef) }
    }
}

public typealias FirTypeRefCheckerCompat = FirTypeCheckerCompat<FirTypeRef>

public typealias FirResolvedTypeRefCheckerCompat = FirTypeCheckerCompat<FirResolvedTypeRef>

public typealias FirFunctionTypeRefCheckerCompat = FirTypeCheckerCompat<FirFunctionTypeRef>

public typealias FirIntersectionTypeRefCheckerCompat = FirTypeCheckerCompat<FirIntersectionTypeRef>

context(context: CheckerContext)
public inline fun <R> withSession(block: context(SessionHolder) () -> R): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return block(object : SessionHolder, ResolveSessionHolder by context.sessionHolder {})
}

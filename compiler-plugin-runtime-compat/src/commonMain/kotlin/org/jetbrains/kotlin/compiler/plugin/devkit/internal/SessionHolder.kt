package org.jetbrains.kotlin.compiler.plugin.devkit.internal

import org.jetbrains.kotlin.fir.FirSession

/**
 * Session holder did not exist in its current form until 2.2.20. This is normally fine, since we
 * can just backport it, but we also have `toSymbol` utilities that need `SessionHolder`. Instead of
 * splitting those utilities across versions, we instead use `expect/actual interface`.
 */
public expect interface SessionHolder {
    public val session: FirSession
}

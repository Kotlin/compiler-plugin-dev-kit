/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir

import org.jetbrains.kotlin.fir.resolve.ScopeSession
import org.jetbrains.kotlin.fir.resolve.SessionHolder as ResolveSessionHolder

public interface SessionHolder {
    public val session: FirSession
}

public interface ScopeSessionHolder {
    public val scopeSession: ScopeSession
}

public interface SessionAndScopeSessionHolder :
    SessionHolder, ScopeSessionHolder, ResolveSessionHolder

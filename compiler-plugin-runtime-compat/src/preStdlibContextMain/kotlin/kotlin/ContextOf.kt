/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:JvmName("ContextParametersKt")
@file:JvmMultifileClass

package kotlin

/**
 * Retrieves the context argument, extension or dispatch receiver in scope with the given type. The
 * compiler ensures that at least one such given value exists. If more than one is found, more
 * nested scopes are prioritized, and otherwise an ambiguity error is raised by the compiler.
 *
 * You must always provide a type argument to [contextOf], even if the type could be inferred from
 * the context.
 */
@Suppress("NOTHING_TO_INLINE")
@SinceKotlin("2.2")
context(context: A)
public inline fun <A> contextOf(): A = context

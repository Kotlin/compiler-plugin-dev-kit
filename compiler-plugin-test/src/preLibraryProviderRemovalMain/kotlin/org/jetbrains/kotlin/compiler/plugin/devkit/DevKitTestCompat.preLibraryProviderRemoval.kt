package org.jetbrains.kotlin.compiler.plugin.devkit

import org.jetbrains.kotlin.test.Constructor
import org.jetbrains.kotlin.test.services.LibraryProvider
import org.jetbrains.kotlin.test.services.TestService

public actual val libraryProviderCtor: Constructor<TestService>?
    get() = ::LibraryProvider

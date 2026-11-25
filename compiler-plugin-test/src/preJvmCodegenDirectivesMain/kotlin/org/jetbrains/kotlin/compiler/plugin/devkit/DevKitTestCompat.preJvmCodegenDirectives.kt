// The facade class that a file's top-level declarations compile into is part of the published
// binary ABI. This file was renamed, so `@file:JvmName` keeps its pre-rename facade class.
@file:JvmName("DevKitTestCompat_pre2520Dev3434Kt")

package org.jetbrains.kotlin.compiler.plugin.devkit

import org.jetbrains.kotlin.test.directives.CodegenTestDirectives

public actual typealias JvmCodegenDirectivesCompat = CodegenTestDirectives

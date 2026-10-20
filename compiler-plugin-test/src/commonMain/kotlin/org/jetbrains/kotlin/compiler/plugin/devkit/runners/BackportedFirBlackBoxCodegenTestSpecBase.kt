/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.compiler.plugin.devkit.runners

import org.jetbrains.kotlin.compiler.plugin.devkit.useFailureSuppressorsCompat
import org.jetbrains.kotlin.test.FirParser
import org.jetbrains.kotlin.test.backend.BlackBoxCodegenSuppressor
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.directives.AdditionalFilesDirectives.SPEC_HELPERS
import org.jetbrains.kotlin.test.directives.ConfigurationDirectives.WITH_STDLIB
import org.jetbrains.kotlin.test.directives.JvmEnvironmentConfigurationDirectives.FULL_JDK
import org.jetbrains.kotlin.test.directives.JvmEnvironmentConfigurationDirectives.WITH_REFLECT
import org.jetbrains.kotlin.test.frontend.fir.FirFailingTestSuppressor
import org.jetbrains.kotlin.test.services.PackageNamePreprocessor
import org.jetbrains.kotlin.test.services.sourceProviders.SpecHelpersSourceFilesProvider
import org.jetbrains.kotlin.utils.bind

public abstract class BackportedFirBlackBoxCodegenTestSpecBase(
    vararg config: TestConfigurationBuilder.() -> Unit,
    parser: FirParser = FirParser.LightTree,
) :
    BackportedJvmBlackBoxCodegenTestBase(
        {
            defaultDirectives {
                +SPEC_HELPERS
                +WITH_STDLIB
                +WITH_REFLECT
                +FULL_JDK
            }
            useSourcePreprocessor(::PackageNamePreprocessor)
            useAdditionalSourceProviders(::SpecHelpersSourceFilesProvider.bind("codegen/box"))
            useFailureSuppressorsCompat(
                ::FirFailingTestSuppressor,
                ::BlackBoxCodegenSuppressor,
            )
        },
        *config,
        parser = parser,
    )

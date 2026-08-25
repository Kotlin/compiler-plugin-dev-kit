package org.jetbrains.kotlin.compiler.plugin.devkit.services

import org.jetbrains.kotlin.compiler.plugin.devkit.KotlinToolingVersion
import org.jetbrains.kotlin.test.TargetBackend
import org.jetbrains.kotlin.test.services.MetaTestConfigurator
import org.jetbrains.kotlin.test.services.TestServices
import org.jetbrains.kotlin.test.services.defaultsProvider

public val BUILD_COMPILER_VERSION: KotlinToolingVersion by lazy {
    KotlinToolingVersion(System.getProperty("kotlin.compiler.plugin.devkit.build.kotlin-version"))
}

public val TEST_COMPILER_VERSION: KotlinToolingVersion by lazy {
    KotlinToolingVersion(System.getProperty("kotlin.compiler.plugin.devkit.test.kotlin-version"))
}

public class DevKitTestConfigurator(testServices: TestServices) :
    MetaTestConfigurator(testServices) {
    override fun shouldSkipTest(): Boolean {
        // TODO should likely apply to all non-JVM backends.
        //  Verify when we have native tests
        if (testServices.isJsBackend() && TEST_COMPILER_VERSION < BUILD_COMPILER_VERSION) {
            // JS box tests compile against runtime KLIBs. KLIB compatibility depends on the
            // producing compiler, so only run these tests against the compiler version that built
            // it or higher
            return true
        }
        return super.shouldSkipTest()
    }
}

private fun TestServices.isJsBackend(): Boolean {
    val targetBackend = defaultsProvider.targetBackend
    return targetBackend == TargetBackend.JS_IR || targetBackend == TargetBackend.JS_IR_ES6
}

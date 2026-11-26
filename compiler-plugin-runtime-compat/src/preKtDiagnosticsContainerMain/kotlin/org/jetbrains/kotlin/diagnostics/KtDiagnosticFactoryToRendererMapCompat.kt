package org.jetbrains.kotlin.diagnostics

public fun KtDiagnosticFactoryToRendererMap(
    name: String,
    init: (KtDiagnosticFactoryToRendererMap) -> Unit,
): Lazy<KtDiagnosticFactoryToRendererMap> =
    lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        KtDiagnosticFactoryToRendererMap(name).also(init)
    }

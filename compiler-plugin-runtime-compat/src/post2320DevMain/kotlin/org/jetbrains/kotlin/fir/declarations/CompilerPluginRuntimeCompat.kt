package org.jetbrains.kotlin.fir.declarations

// The versioned source sets below 2.3.20 hold the compatibility declarations. Versions from
// 2.3.20 compile no other source, so without this object their `versions/<version>` directory
// would be missing from the published jar. Version resolution would then fall back to a lower
// directory, whose declarations clash with the compiler's own.
internal object CompilerPluginRuntimeCompat

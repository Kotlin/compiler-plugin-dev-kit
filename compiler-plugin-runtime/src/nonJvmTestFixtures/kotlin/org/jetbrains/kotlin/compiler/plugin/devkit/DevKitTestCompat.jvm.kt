package org.jetbrains.kotlin.compiler.plugin.devkit

// TODO hacky. JVM target should be removed, but for now, we shadow its `actual`s so that consumers
// don't see them on other versions
internal fun foo() {}

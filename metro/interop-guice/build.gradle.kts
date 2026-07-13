// Copyright (C) 2025 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
plugins {
  alias(libs.plugins.kotlin.jvm)
  id("metro.publish")
  id("org.jetbrains.kotlin.compiler.plugin.devkit.functional-test-publishing")
}

metroArtifact {
  artifactId.set("interop-guice")
  name.set("Metro Guice Interop")
}

dependencies {
  api(project(":runtime"))
  // Guice dropped javax.inject in 7.0
  api(project(":interop-jakarta"))
  api(libs.guice)
}

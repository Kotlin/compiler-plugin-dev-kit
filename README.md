# Kotlin Compiler Plugin DevKit

[![Kotlin Experimental](https://kotl.in/badges/experimental.svg)](https://kotlinlang.org/docs/components-stability.html)
![JetBrains incubator](https://camo.githubusercontent.com/6e2750106a3263d7de756abf314657b331f94ecf58726ab8bfd413bb7bd7c545/68747470733a2f2f6a622e67672f6261646765732f696e63756261746f722d706c61737469632e737667)
[![Kotlin](https://img.shields.io/badge/kotlin-2.3.20+-blue.svg?logo=kotlin)](http://kotlinlang.org)
[![GitHub License](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat)](http://www.apache.org/licenses/LICENSE-2.0)

> [!WARNING]
> ## **Here be dragons!**
>
> The DevKit is very experimental! Try it out and give feedback!
>
> For questions, issues, etc, use the [KotlinLang Slack](https://slack-chats.kotlinlang.org/) #compiler channel,
> or [file a GitHub issue](https://github.com/Kotlin/compiler-plugin-dev-kit/issues/new)

A DevKit for Kotlin compiler plugin development

## Features
- **Multiversioning**: your plugin is built, tested, and published for a plethora of compiler and IDE versions. Simply specify a range of supported versions, and the DevKit works out the relevant compiler and IDE versions, compiling your code against each one.
- **Source compatibility**: your code is compiled against each version's compiler, meaning that source-compatible changes in the compiler (a new default parameter, for example) don't break your plugin! In other words, you won't get any `ClassNotFoundException`s or `NoSuchMethodError`s
- **`expect`/`actual` support**: Each compiler/IDE version is treated as a separate "platform" (in the KMP sense), so you can `expect` some methods to exist, and `actual`ize them for particular compiler versions
- **Testing**: JVM, JS, and Gradle functional tests at the palm of your hand! No complex setup necessary; the DevKit configures the needed Kotlin libraries for `kotlin-compiler-internal-test-framework`. Did I mention the tests are multi-versioned too?
    - There's even multi-versioned dump files!
- **IDE support out-of-the-box**: your compiler plugin "just works" in IntelliJ-based IDEs (2026.3+). No registry options necessary.

## Getting Started

Use the [compiler-plugin-template](https://github.com/Kotlin/compiler-plugin-template/tree/devkit)'s `devkit` branch.

Alternatively, for a pre-existing project, use the [Migration guide](./docs/migration/)

### Compatibility

The DevKit supports Kotlin 2.3.20+, Gradle 9.2+, and IntelliJ 2026.1+

## Further Info

There's a [spec](./docs/spec.md)

## Attributions

A significant amount of code, particularly [version resolution](./version-resolution) and [gradle plugin testing](./gradle-plugin-runtime/src/testFixtures), is inspired by or copied from Metro

Some [Gradle incantations](./plugins) were adapted from TestBalloon, Kotlin Gradle Plugin, and Gradle itself
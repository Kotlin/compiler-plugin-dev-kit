# Publishing

Publish every part with the same `group` and `version`. The Gradle support plugin applies the compiler plugin and the
companion libraries at its own `group` and `version`, unless you set explicit coordinates.

## Compiler plugin

Apply a publishing plugin, as for a Kotlin Multiplatform library:

```kotlin title="compiler-plugin/build.gradle.kts"
plugins {
    kotlin("multiplatform")
    pluginDevKit("compiler-plugin")
    `maven-publish`
}
```

The project publishes two modules:

| Module                     | Content                                                                         |
|----------------------------|---------------------------------------------------------------------------------|
| `my-compiler-plugin`       | The Gradle module metadata. Gradle resolves this module to the jar.             |
| `my-compiler-plugin-k2220` | One jar with the classes of every version and the dependencies of the plugin.   |

The suffix is the [target name](versions.md#target-names) of the first registered version. If that version changes,
the name of the module changes.

The jar declares the [range of IDE builds](ide.md#supported-ide-builds) in its manifest.

## Gradle support plugin

`pluginDevKit("gradle-plugin")` applies `java-gradle-plugin`. Declare the plugin and publish it:

```kotlin title="gradle-plugin/build.gradle.kts"
plugins {
    pluginDevKit("gradle-plugin")
    `maven-publish`
}

gradlePlugin {
    plugins {
        create("myPlugin") {
            id = "com.example.myplugin"
            implementationClass = "com.example.MySupportPlugin"
        }
    }
}
```

The published plugin depends on the `gradle-plugin-runtime` library of the DevKit. Your users need the DevKit EAP
repository to resolve it.

## Other build tools

The jar works with `-Xplugin` and needs no other jar. The plugin ID is the
[`pluginPackage`](compiler-plugin.md#plugin-id).

```bash
kotlinc \
    -Xplugin=my-compiler-plugin-k2220-1.0.0.jar \
    -P plugin:com.example.myplugin:myOption=value \
    -cp my-plugin-annotations-jvm-1.0.0.jar \
    Main.kt
```

Only the Gradle support plugin adds the companion libraries. With another build tool, add them to the classpath of the
code yourself.

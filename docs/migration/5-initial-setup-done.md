# Initial setup done

## Workflows

You can copy the workflows from the DevKit-based compiler-plugin-template. They work without changes.

If you keep your own workflows, change the task names:

| Old task                           | New task                                        |
|------------------------------------|-------------------------------------------------|
| `:my-compiler-plugin:test`         | `:my-compiler-plugin:allTests`                  |
| `:my-gradle-plugin:functionalTest` | `:my-gradle-plugin:allVersionedFunctionalTests` |

`check` and `build` don't change.

If you use `mikepenz/action-junit-report` or a similar action, set `report_paths` to
`'**/build/test-results/**/TEST-*.xml'`. This path includes the results of all versioned tests.

## Cleanup

Delete the unused entries in `libs.versions.toml`.

## Optional: Set up functional tests

[comment]: # (TODO #58)

If you have a Gradle support plugin, you can use the DevKit setup for its functional and integration tests. The
DevKit-based template already uses this setup. To set it up yourself, do these steps in the `functionalTest` source set:

1. Declare `open class MyProject : AbstractDevKitGradleProject()` and override `pluginUnderTest`.
2. Write a JUnit 5 test like this one:

    ```kotlin
    import com.autonomousapps.kit.GradleBuilder.build

    val project = object : MyProject() {
        override fun sources() = listOf(source("..."))
    }.gradleProject
    val result = build(project.rootDir, "someTaskName")
    // assertions on the result, for example:
    result.assertOutputContains("foo-bar-baz")
    ```

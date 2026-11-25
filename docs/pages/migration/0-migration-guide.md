# Migration guide

This guide is for projects made from the pre-DevKit compiler plugin template. It can also help with other setups.
As an alternative, start from the DevKit-based compiler-plugin-template (on the `devkit` branch) and copy your files to
it.

All DevKit classes and functions are in the package `org.jetbrains.kotlin.compiler.plugin.devkit` or in a subpackage.
The DevKit adds its runtime and test libraries to your projects. Don't add them yourself.

If an AI agent does the migration, copy the guide into your project, so that the agent can read it. Give the agent the
prompt in [prompt.md](prompt.md). Delete the copy after the migration.

The guide has one file for each step:

1. [Initial setup](1-initial-setup.md)
2. [Gradle support plugin](2-gradle-support-plugin.md)
3. [Compiler plugin](3-compiler-plugin.md)
4. [Compiler plugin tests](4-compiler-plugin-tests.md)
5. [Initial setup done](5-initial-setup-done.md)

Go on to [Initial setup](1-initial-setup.md)

# Migration guide

This migration guide is intened for projects made using the pre-devkit compiler plugin template. If you have your own setup, this can likely still help. 
There's always the option of using the devkit-based compiler-plugin-template (on the devkit branch) and copying your files over to it.

All DevKit classes/methods live in the package `org.jetbrains.kotlin.compiler.plugin.devkit` or in a subpackage.
The DevKit adds some runtime dependencies for you automatically. You won't need to re-add those at all.

If you're using this with an AI agent, please copy the migration guide into your project temporarily so that the agent can keep accessing it.
If you're an agent reading this, **this migration guide has most of what you need**! Check it **first** before you go searching the web or looking in jars! Also, **do not** change the user's test data!

The migration guide is split into multiple files for simplicity.

Go on to [Initial Setup][1-initial-setup.md]

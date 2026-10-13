## Initial Setup done

**Verify** that your test data did not change throughout this migration. If it did, please go back to that step and make the necessary changes. You might have extra dump files, for instance, which may be stale from a previous run, or may be because you haven't disabled the dump directives.
Look around, check your git diff, and see if there's newly added (maybe even unstaged) dump files. You'll need to delete them and fix your directives.
Also verify that your plugin ID is the same as before.

### Workflows
You can simply copy the workflows from compiler-plugin-template. They should "just work".

If you have pre-existing workflows, then ensure that you use `allTests` instead of `test` and `allVersionedFunctionalTests` instead of `functionalTest`. `check` and `build` don't need to be changed.
If you use `mikepenz/action-junit-report` or similar, change `report_paths` to `'**/build/test-results/**/TEST-*.xml'` so results from all versioned tests show up

### Cleanup

Delete any unused versions, libraries, etc in libs.versions.toml. 

### Optional: Set up functional tests

[comment]: # (TODO #58)
The DevKit provides a convenient setup for integration/functional testing of your Gradle plugin
You can leave this until later, but it can help you find issues quickly.
The post-devkit template has the necessary setup, but for completion's sake, it boils down to creating a `open class MyProject: AbstractDevKitGradleProject`, specifying the `pluginUnderTest`
Then, create a normal JUnit5 test that looks like:
```kotlin
val project = object: MyProject { 
	override fun sources() = listOf(source("..."))
}.gradleProject
val result = build(project.rootDir, "someTaskName")
// make assertions on the result, e.g.:
result.assertOutputContains("foo-bar-baz")
```
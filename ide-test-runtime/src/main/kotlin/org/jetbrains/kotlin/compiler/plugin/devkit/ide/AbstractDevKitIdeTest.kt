package org.jetbrains.kotlin.compiler.plugin.devkit.ide

import com.autonomousapps.kit.gradle.Plugin
import com.intellij.driver.client.service
import com.intellij.driver.sdk.DeclarativeInlayRenderer
import com.intellij.driver.sdk.FileEditorManager
import com.intellij.driver.sdk.Inlay
import com.intellij.driver.sdk.InlayModel
import com.intellij.driver.sdk.WaitForException
import com.intellij.driver.sdk.getHighlights
import com.intellij.driver.sdk.openFile
import com.intellij.driver.sdk.singleProject
import com.intellij.driver.sdk.waitForIndicators
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.junit5.hyphenateWithClass
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.models.VMOptions
import com.intellij.ide.starter.project.LocalProjectInfo
import com.intellij.ide.starter.runner.CurrentTestMethod
import com.intellij.ide.starter.runner.Starter
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.extension
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.readText
import kotlin.io.path.walk
import kotlin.io.path.writeText
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import org.jetbrains.kotlin.compiler.plugin.devkit.test.AbstractDevKitGradleProject
import org.jetbrains.kotlin.compiler.plugin.devkit.test.pluginUnderTestVersion
import org.junit.jupiter.api.Test

/**
 * Launches a real IDE on a generated Gradle project that applies the compiler plugin under test,
 * and checks that the IDE's own analysis of it is what the sources declare it should be.
 *
 * The project is described exactly like a functional test's, by overriding [pluginUnderTest] and
 * [sources]; the expectations live in the sources themselves, as `// EXPECT_DIAGNOSTIC:` and `//
 * EXPECT_INLAY:` comments. See [IdeExpectations].
 *
 * Which IDE is launched is not this class's decision: the `ideTest { }` block registers one task
 * per IDE and names it in [DevKitIdeTestProperties.IDE_SPEC], so a single test class covers every
 * IDE the build supports.
 *
 * What is actually being tested is that the IDE loads the plugin's FIR extensions at all and that
 * they survive analysis. Nothing here reaches into IDE internals beyond the Driver SDK, so the same
 * test runs unchanged on IntelliJ IDEA and Android Studio.
 */
public abstract class AbstractDevKitIdeTest(kotlinVersion: String? = null) :
    AbstractDevKitGradleProject(kotlinVersion) {

    /**
     * The plugin the generated project applies. Defaults to the id `ideTest { }` was configured
     * with, at the version its `installForFunctionalTest` published.
     */
    override val pluginUnderTest: Plugin
        get() =
            Plugin(
                checkNotNull(System.getProperty(DevKitIdeTestProperties.PLUGIN_ID)) {
                    "No plugin to apply. Set `pluginDevKit { ideTest { pluginId = ... } }` in the " +
                        "build script, or override pluginUnderTest."
                },
                pluginUnderTestVersion,
            )

    /** The IDE this task runs against. */
    protected open val ide: IdeUnderTest by lazy {
        val spec =
            checkNotNull(System.getProperty(DevKitIdeTestProperties.IDE_SPEC)) {
                "No IDE to run against. ${DevKitIdeTestProperties.IDE_SPEC} is unset, which means " +
                    "this test was not launched by one of the `<id>IdeTest` tasks."
            }
        IdeUnderTest.parse(spec)
    }

    /**
     * Substrings that mark an IDE error as caused by the plugin under test rather than by the IDE.
     * Defaults to the patterns `ideTest { }` derived from the plugin's own id.
     */
    protected open val errorPatterns: List<String> =
        systemPropertyList(DevKitIdeTestProperties.ERROR_PATTERNS)

    /**
     * Strings that must not appear in `idea.log`. A plugin that logs when it declines to install
     * its extensions can name that message here and have the test say so, rather than reporting
     * every expected diagnostic as missing.
     */
    protected open val forbiddenLogPatterns: List<String> =
        systemPropertyList(DevKitIdeTestProperties.FORBIDDEN_LOG_PATTERNS)

    /** How long to wait for Gradle import, indexing and analysis to go quiet. */
    protected open val indicatorTimeout: Duration = 3.minutes

    /** Extra VM options for the IDE process. */
    protected open fun VMOptions.applyExtraVmOptions() {}

    /** Last chance to configure the IDE run, after every default has been applied. */
    protected open fun IDETestContext.configureContext(): IDETestContext = this

    /**
     * Last chance to add to the generated project before the IDE imports it - a `local.properties`
     * pointing Android Studio at an SDK, say, or IDE configuration under `.idea`.
     */
    protected open fun prepareProject(projectRoot: Path) {}

    /**
     * Files to open and analyse, relative to the project root. Defaults to every Kotlin source of
     * the generated project.
     */
    protected open fun filesUnderTest(projectRoot: Path): List<String> =
        projectRoot
            .walk()
            .filter { it.extension == "kt" && "${projectRoot.relativize(it)}".startsWith("src") }
            .map { projectRoot.relativize(it).invariantSeparatorsPathString }
            .sorted()
            .toList()

    @Test
    public fun ideAnalysisMatchesExpectations() {
        val projectRoot = gradleProject.rootDir.toPath().toAbsolutePath()
        writeGradleWrapper(projectRoot)
        prepareProject(projectRoot)

        val files = filesUnderTest(projectRoot)
        check(files.isNotEmpty()) { "No sources to analyse in $projectRoot" }
        val sources = files.associateWith { projectRoot.resolve(it).readText() }

        val testContext =
            Starter.newContext(
                    "${CurrentTestMethod.hyphenateWithClass()}-${ide.id}",
                    TestCase(ide.toIdeInfo(), LocalProjectInfo(projectRoot)),
                )
                .prepareProjectCleanImport()
                .addProjectToTrustedLocations()
                .applyVMOptionsPatch {
                    // The IDE sets java.system.class.loader, which trips the JBR's CDS warning on
                    // every launch.
                    addLine("-Xlog:cds=off")

                    // The one option this harness exists to turn on. Without it, the Kotlin
                    // plugin's FIR analysis loads only the compiler plugins bundled with the IDE.
                    // RegistryValue falls back to system properties, so this reaches Registry.get()
                    // as well.
                    addSystemProperty("kotlin.k2.only.bundled.compiler.plugins.enabled", false)

                    // Keep VCS out of it; the IDE writes files the project doesn't track.
                    addSystemProperty("vcs.log.index.git", false)
                    addSystemProperty("git.showDialogsOnUnversionedFiles", false)

                    // First-run dialogs block forever on CI.
                    addSystemProperty("jb.consents.confirmation.enabled", false)
                    addSystemProperty("idea.initially.ask.config", "never")
                    addSystemProperty("disable.android.first.run", true)
                    addSystemProperty("jb.privacy.policy.text", "<!--999.999-->")
                    addSystemProperty("ide.show.tips.on.startup.default.value", false)

                    applyAndroidStudioWorkarounds(ide)
                    applyExtraVmOptions()
                }
                .configureContext()

        // Collected inside the driver block and asserted after the IDE closes, so the logs can be
        // read first: "analysis timed out" is a far worse error message than the crash behind it.
        val analyzed = mutableListOf<AnalyzedFile>()
        var analysisException: WaitForException? = null

        val result =
            testContext.runIdeWithDriver().useDriverAndCloseIde {
                // Every wait in here is caught, so that a timeout is reported alongside what the
                // IDE's own logs say about it rather than as a bare driver stack trace.
                try {
                    // waitSmartLongEnough (on by default) needs ten consecutive quiet seconds,
                    // which is what catches the gap before Gradle import starts.
                    waitForIndicators(indicatorTimeout)
                } catch (e: WaitForException) {
                    analysisException = e
                    return@useDriverAndCloseIde
                }

                for ((path, sourceText) in sources) {
                    try {
                        openFile(path, singleProject())
                        waitForIndicators(indicatorTimeout)
                    } catch (e: WaitForException) {
                        analysisException = e
                        return@useDriverAndCloseIde
                    }

                    val project = singleProject()
                    val editor =
                        checkNotNull(service<FileEditorManager>(project).getSelectedTextEditor()) {
                            "No editor open after opening $path"
                        }
                    val document = editor.getDocument()
                    val sourceLines = sourceText.lines()

                    val highlights =
                        getHighlights(document, project).map {
                            HighlightData(
                                severity = it.getSeverity().getName(),
                                description = it.getDescription(),
                                highlightedText = it.getText(),
                                highlightedTextLines =
                                    sourceLinesContaining(sourceLines, it.getText()),
                            )
                        }

                    val inlayModel = cast(editor.getInlayModel(), InlayModel::class)
                    val documentLength = document.getText().length
                    fun List<Inlay>.collect(isBlock: Boolean) = map { inlay ->
                        InlayData(
                            offset = inlay.getOffset(),
                            text =
                                if (isBlock) {
                                    // Block renderers expose nothing else to the driver.
                                    inlay.getRenderer().toString()
                                } else {
                                    cast(inlay.getRenderer(), DeclarativeInlayRenderer::class)
                                        .getPresentationList()
                                        .getEntries()
                                        .joinToString("") { it.getText() }
                                },
                            isBlock = isBlock,
                        )
                    }

                    analyzed +=
                        AnalyzedFile(
                            path = path,
                            sourceText = sourceText,
                            expectations = IdeExpectations.parse(sourceText),
                            highlights = highlights,
                            inlays =
                                inlayModel
                                    .getInlineElementsInRange(0, documentLength)
                                    .collect(isBlock = false) +
                                    inlayModel
                                        .getBlockElementsInRange(0, documentLength)
                                        .collect(isBlock = true),
                        )
                }
            }

        result.failureError?.let { fail("The ${ide.displayName} run failed", it) }
        val logsDir = result.runContext.logsDir

        analysisException?.let { exception ->
            fail(
                buildString {
                    appendLine(
                        "The IDE never went quiet within $indicatorTimeout. Either FIR crashed, or " +
                            "the IDE is sitting on a modal dialog - the screenshots under " +
                            "$logsDir/screenshots show which."
                    )
                    val errors = collectPluginErrors(logsDir, errorPatterns)
                    if (errors.isNotEmpty()) {
                        appendLine("Errors naming the plugin under test:")
                        errors.forEach(::appendLine)
                    } else {
                        val mentions = findLogMentions(logsDir, errorPatterns)
                        if (mentions != null) {
                            appendLine("idea.log mentions the plugin under test around:")
                            appendLine(mentions)
                        } else {
                            appendLine("Nothing in the logs names the plugin under test.")
                        }
                    }
                },
                exception,
            )
        }

        // Before the expectations: an unloaded plugin makes every one of them fail, which says
        // nothing about why.
        findLogMentions(logsDir, forbiddenLogPatterns)?.let {
            fail("idea.log contains a forbidden pattern $forbiddenLogPatterns:\n$it")
        }

        val failures = analyzed.associateWith { it.failures() }.filterValues { it.isNotEmpty() }
        if (failures.isNotEmpty()) {
            fail(
                buildString {
                    appendLine("${ide.displayName} did not analyse the project as declared:")
                    for ((file, fileFailures) in failures) {
                        appendLine("${file.path}:")
                        fileFailures.forEach { appendLine("  - $it") }
                        appendLine(file.report())
                    }
                }
            )
        }

        val errors = collectPluginErrors(logsDir, errorPatterns)
        if (errors.isNotEmpty()) {
            fail(
                "The plugin under test caused ${errors.size} internal error(s) during analysis:\n" +
                    errors.joinToString("\n---\n")
            )
        }
    }

    /**
     * Points the generated project's wrapper at a Gradle distribution.
     *
     * Only the properties file is written: the IDE reads the distribution out of it and drives
     * Gradle through the tooling API, so it never runs `gradlew`.
     */
    private fun writeGradleWrapper(projectRoot: Path) {
        val distributionUrl =
            System.getProperty(DevKitIdeTestProperties.GRADLE_DISTRIBUTION_URL) ?: return
        projectRoot
            .resolve("gradle/wrapper")
            .createDirectories()
            .resolve("gradle-wrapper.properties")
            .writeText(
                """
                distributionBase=GRADLE_USER_HOME
                distributionPath=wrapper/dists
                distributionUrl=${distributionUrl.replace(":", "\\:")}
                zipStoreBase=GRADLE_USER_HOME
                zipStorePath=wrapper/dists
                """
                    .trimIndent()
            )
    }
}

/**
 * Answers the modal dialogs Android Studio blocks on before it will import anything.
 *
 * `studio.run.under.integration.test` covers the parallel-sync migration prompt; the consent prompt
 * has no such switch and has to be pre-answered on disk instead, the way Android Studio's own test
 * infrastructure does it (`AnalyticsTestUtils.kt` in JetBrains/android): its ConsentDialog checks
 * three things in order, and satisfying only some of them still leaves the dialog up.
 */
private fun VMOptions.applyAndroidStudioWorkarounds(ide: IdeUnderTest) {
    if (ide !is AndroidStudio) return

    addSystemProperty("studio.run.under.integration.test", true)

    // 1. ConsentOptions.getConsents(), out of PathManager.getCommonDataPath()
    val commonDataDir = createTempDirectory("ide-test-common-data")
    commonDataDir
        .resolve("consentOptions")
        .createDirectories()
        .resolve("accepted")
        .writeText("rsch.send.usage.stat:1.0:0:${System.currentTimeMillis()}")

    // 2. AnalyticsSettings.optedIn and 3. hasUserBeenPromptedForOptIn, both out of
    // analytics.settings - hence the far-future lastOptinPromptVersion.
    val androidPrefsDir = createTempDirectory("ide-test-android-prefs")
    androidPrefsDir
        .resolve("analytics.settings")
        .writeText(
            """
            {"userId":"00000000-0000-0000-0000-000000000000","hasOptedIn":true,
            "debugDisablePublishing":true,"saltSkew":-1,"lastOptinPromptVersion":"9999.9999"}
            """
                .trimIndent()
                .replace("\n", "")
        )

    addSystemProperty("google.login.enable.startup.sign.in.dialog", false)
    addSystemProperty("idea.common.data.path", "${commonDataDir.toAbsolutePath()}")
    addSystemProperty("ANDROID_PREFS_ROOT", "${androidPrefsDir.toAbsolutePath()}")
}

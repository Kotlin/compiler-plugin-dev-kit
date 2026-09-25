package org.jetbrains.kotlin.compiler.plugin.devkit.ide

import com.intellij.ide.starter.community.IdeByLinkDownloader
import com.intellij.ide.starter.community.model.BuildType
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.ide.installer.StandardInstaller
import com.intellij.ide.starter.models.IdeInfo
import java.net.URI

/**
 * The IDE Starter product this [IdeUnderTest] resolves to.
 *
 * A released IntelliJ IDEA is looked up by marketing version through Starter's own release API. A
 * prerelease isn't in that API, so it is downloaded by link from its build number instead.
 */
internal fun IdeUnderTest.toIdeInfo(): IdeInfo =
    when (this) {
        is IntelliJIdea -> {
            val product =
                when (edition) {
                    IntelliJIdea.Edition.ULTIMATE -> IdeProductProvider.IU
                    IntelliJIdea.Edition.COMMUNITY -> IdeProductProvider.IC
                }
            when (buildType) {
                IdeBuildType.RELEASE ->
                    product.copy(version = version, buildType = BuildType.RELEASE.type)

                else ->
                    product.copy(
                        buildNumber = version,
                        buildType = buildType.starterBuildType,
                        downloadURI =
                            URI(
                                "https://download.jetbrains.com/idea/" +
                                        "${product.installerFilePrefix}-$version${product.installerFileExt}"
                            ),
                        getInstaller = { StandardInstaller(IdeByLinkDownloader) },
                    )
            }
        }

        is AndroidStudio -> IdeProductProvider.AI.copy(buildNumber = version)
    }

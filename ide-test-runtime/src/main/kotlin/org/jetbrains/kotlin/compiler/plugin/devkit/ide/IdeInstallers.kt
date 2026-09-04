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
 * prerelease isn't in that API, so it is downloaded by link from its build number instead. Android
 * Studio is left to Starter's Android installer, which finds the build in Google's release list,
 * unless a [AndroidStudio.downloadFilePrefix] names the installer outright.
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

        is AndroidStudio ->
            IdeProductProvider.AI.copy(buildNumber = version).let { product ->
                if (downloadFilePrefix == null) product
                else
                    product.copy(
                        downloadURI = androidStudioDownloadUri(version, downloadFilePrefix),
                        getInstaller = { StandardInstaller(IdeByLinkDownloader) },
                    )
            }
    }

private fun androidStudioDownloadUri(version: String, filePrefix: String): URI {
    val osName = System.getProperty("os.name")
    val (pathType, platformSuffix) =
        when {
            osName.startsWith("Mac", ignoreCase = true) ->
                "install" to
                    if (System.getProperty("os.arch") == "aarch64") "mac_arm.dmg" else "mac.dmg"

            osName.startsWith("Linux", ignoreCase = true) -> "ide-zips" to "linux.tar.gz"
            osName.startsWith("Windows", ignoreCase = true) -> "install" to "windows.exe"
            else -> error("Unsupported OS: $osName")
        }
    return URI(
        "https://dl.google.com/android/studio/$pathType/$version/$filePrefix-$platformSuffix"
    )
}

package dev.cmdrchara.songlass.ui

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal actual fun rememberAppInformation(): AppInformation {
    val context = LocalContext.current.applicationContext
    return remember(context) { AndroidAppInformation(context) }
}

private class AndroidAppInformation(private val context: Context) : AppInformation {
    override val licenses = listOf(
        BundledLicense.AutoEq, BundledLicense.Miuix, BundledLicense.TagLib, BundledLicense.OtherOpenSource,
    )

    override suspend fun readVersion(): InstalledAppVersion = withContext(Dispatchers.IO) {
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0L))
        InstalledAppVersion(info.versionName?.takeIf { it.isNotBlank() }, info.longVersionCode.toString())
    }

    override suspend fun readLicense(license: BundledLicense): List<LicenseDocument> = when (license) {
        BundledLicense.AutoEq -> readAutoEqLicense()
        else -> withContext(Dispatchers.IO) {
            val files = when (license) {
                BundledLicense.Miuix -> listOf(
                    LicenseTextKind.Notice to "miuix-NOTICE.txt",
                    LicenseTextKind.Apache2 to "miuix-APACHE-2.0.txt",
                )
                BundledLicense.TagLib -> listOf(
                    LicenseTextKind.Notice to "taglib-NOTICE.txt",
                    LicenseTextKind.Apache2 to "taglib-android-APACHE-2.0.txt",
                    LicenseTextKind.Lgpl21 to "taglib-COPYING.LGPL.txt",
                    LicenseTextKind.Mpl11 to "taglib-COPYING.MPL.txt",
                )
                BundledLicense.OtherOpenSource -> listOf(
                    LicenseTextKind.Notice to "third-party-NOTICE.txt",
                    LicenseTextKind.Notice to "kotlinx-coroutines-NOTICE.txt",
                    LicenseTextKind.Notice to "kotlinx-serialization-NOTICE.txt",
                    LicenseTextKind.Bsd3 to "kotlin-threetenbp-BSD-3-Clause.txt",
                    LicenseTextKind.Boost10 to "kotlin-boost-LICENSE.txt",
                    LicenseTextKind.Apache2 to "material-color-utilities-APACHE-2.0.txt",
                )
                BundledLicense.AutoEq -> error("AutoEQ uses shared resources")
            }
            files.map { (kind, fileName) ->
                context.assets.open("licenses/$fileName").bufferedReader(Charsets.UTF_8).use {
                    licenseDocument(kind, it.readText())
                }
            }
        }
    }
}

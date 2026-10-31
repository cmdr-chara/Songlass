package dev.cmdrchara.songlass.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSBundle

@Composable
internal actual fun rememberAppInformation(): AppInformation = remember { IosAppInformation }

private object IosAppInformation : AppInformation {
    // The other notices currently live in Android-only assets. Do not offer missing files.
    override val licenses = listOf(BundledLicense.Songlass, BundledLicense.AutoEq)

    override suspend fun readVersion(): InstalledAppVersion = withContext(Dispatchers.Default) {
        val bundle = NSBundle.mainBundle
        InstalledAppVersion(
            (bundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String)?.takeIf { it.isNotBlank() },
            (bundle.objectForInfoDictionaryKey("CFBundleVersion") as? String)?.takeIf { it.isNotBlank() },
        )
    }

    override suspend fun readLicense(license: BundledLicense): List<LicenseDocument> = when (license) {
        BundledLicense.Songlass -> readSonglassLicense()
        BundledLicense.AutoEq -> readAutoEqLicense()
        else -> throw IllegalArgumentException("This notice is not bundled on iOS")
    }
}

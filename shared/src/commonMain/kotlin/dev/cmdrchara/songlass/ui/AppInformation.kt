package dev.cmdrchara.songlass.ui

import androidx.compose.runtime.Composable
import dev.cmdrchara.songlass.shared.resources.Res
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Values from the installed application's metadata, never from a duplicated version constant. */
internal data class InstalledAppVersion(val name: String?, val build: String?)

/** Only notices already packaged with this platform are offered in About. */
internal enum class BundledLicense { Songlass, AutoEq, Miuix, TagLib, OtherOpenSource }

internal enum class LicenseTextKind { Notice, PolyFormPerimeter, Mit, Apache2, Lgpl21, Mpl11, Bsd3, Boost10 }

internal data class LicenseDocument(val kind: LicenseTextKind, val paragraphs: List<String>)

internal interface AppInformation {
    val licenses: List<BundledLicense>
    suspend fun readVersion(): InstalledAppVersion
    suspend fun readLicense(license: BundledLicense): List<LicenseDocument>
}

/** Captures platform services only; all package and file reads happen in the suspend methods. */
@Composable
internal expect fun rememberAppInformation(): AppInformation

/** Split away from composition so long notices can be laid out one paragraph at a time. */
internal fun licenseDocument(kind: LicenseTextKind, text: String): LicenseDocument {
    val normalized = text.replace("\r\n", "\n").trim()
    require(normalized.isNotEmpty()) { "The bundled notice is empty" }
    // The canonical PolyForm document is Markdown. Remove presentation markers only;
    // its bundled bytes and the legal wording remain unchanged.
    val display = if (kind == LicenseTextKind.PolyFormPerimeter) normalized
        .replace(Regex("(?m)^#{1,6}[\\t ]+(.+)$"), "\n\$1\n")
        .replace(Regex("""\[([^\]]+)\]\(#[^)]+\)"""), "\$1")
        .replace(Regex("""<(https?://[^>]+)>"""), "\$1")
        .replace(Regex("(?m)^>[\\t ]?"), "")
        .replace("***", "").replace("**", "").replace("`", "")
    else normalized
    return LicenseDocument(kind, display.split(Regex("\n[\t ]*\n+")).filter { it.isNotBlank() })
}

internal suspend fun readSonglassLicense(): List<LicenseDocument> = withContext(Dispatchers.Default) {
    listOf(
        licenseDocument(LicenseTextKind.Notice, Res.readBytes("files/licenses/songlass-NOTICE.txt").decodeToString()),
        licenseDocument(LicenseTextKind.PolyFormPerimeter, Res.readBytes("files/licenses/songlass-POLYFORM-PERIMETER-1.0.0.txt").decodeToString()),
        licenseDocument(LicenseTextKind.Notice, Res.readBytes("files/licenses/songlass-CONTRIBUTING-PERMISSION.txt").decodeToString()),
    )
}

internal suspend fun readAutoEqLicense(): List<LicenseDocument> = withContext(Dispatchers.Default) {
    listOf(
        licenseDocument(LicenseTextKind.Notice, Res.readBytes("files/licenses/autoeq-NOTICE.txt").decodeToString()),
        licenseDocument(LicenseTextKind.Mit, Res.readBytes("files/licenses/autoeq-MIT.txt").decodeToString()),
    )
}

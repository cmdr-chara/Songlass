package dev.cmdrchara.songlass.core.settings

/** Stable destinations, independent of translated labels and the UI's navigation stack. */
enum class SettingsTarget {
    InterfaceStyle, Appearance, ArtworkStyle, ResumeOnReconnect, ReplayGain, Crossfade,
    CrossfadeDuration, ManualCrossfadeDuration, AmllLyrics, ReduceMotion, ReduceTransparency, Equalizer,
    EqualizerPresets, OutputProfiles, HeadphoneCorrection, MinimumDuration, ExcludedFolders,
    HomeCustomization, Backup, About, ResumePerTrack, LongTrackThreshold,
}

/** Localized text is supplied by the UI; current values are intentionally not cached in the index. */
data class SettingsSearchDocument(val target: SettingsTarget, val title: String, val path: String)

/**
 * A small, immutable, on-device index. It has no preference, storage, player or network dependency.
 * All query words must match. Exact labels rank before partial labels, synonyms and paths;
 * otherwise ties retain the order of the first document for each destination.
 */
class SettingsSearchIndex(documents: List<SettingsSearchDocument>) {
    private val entries = documents.groupBy { it.target }.map { (target, documentsForTarget) ->
        val terms = target.searchTerms()
        IndexedSetting(
            target = target,
            labels = (documentsForTarget.map { it.title } + terms.labels)
                .map(::normalizeSettingsText).filter(String::isNotEmpty).distinct(),
            aliases = terms.aliases.split('|').map(::normalizeSettingsText).filter(String::isNotEmpty).distinct(),
            paths = documentsForTarget.map { normalizeSettingsText(it.path) }.filter(String::isNotEmpty).distinct(),
        )
    }

    /** Only registered, available destinations are returned. Searching never changes a preference. */
    fun search(query: String, supportsAmllLyrics: Boolean, supportsCrossfade: Boolean = true): List<SettingsTarget> {
        val normalized = normalizeSettingsText(query)
        if (normalized.isEmpty()) return emptyList()
        val tokens = normalized.split(' ').distinct()
        return entries.mapIndexedNotNull { order, entry ->
            if (entry.target == SettingsTarget.AmllLyrics && !supportsAmllLyrics) return@mapIndexedNotNull null
            if (!supportsCrossfade && entry.target in crossfadeTargets) return@mapIndexedNotNull null
            entry.match(normalized, tokens)?.let { Match(entry.target, it.first, it.second, order) }
        }.sortedWith(compareBy<Match> { it.priority }.thenBy { it.cost }.thenBy { it.order })
            .map { it.target }
    }

    private data class Match(val target: SettingsTarget, val priority: Int, val cost: Int, val order: Int)

    private companion object {
        val crossfadeTargets = setOf(SettingsTarget.Crossfade, SettingsTarget.CrossfadeDuration, SettingsTarget.ManualCrossfadeDuration)
    }
}

private class IndexedSetting(
    val target: SettingsTarget,
    private val labels: List<String>,
    private val aliases: List<String>,
    paths: List<String>,
) {
    private val labelWordGroups = labels.map { it.split(' ') }
    private val labelWords = labelWordGroups.flatten().distinct()
    private val aliasWords = aliases.flatMap { it.split(' ') }.distinct()
    private val pathWords = paths.flatMap { it.split(' ') }.distinct()

    fun match(query: String, tokens: List<String>): Pair<Int, Int>? {
        var cost = 0
        for (token in tokens) {
            cost += when {
                token in labelWords -> 0
                labelWords.any { it.startsWith(token) } -> 1
                token in aliasWords -> 2
                aliasWords.any { it.startsWith(token) } -> 3
                token in pathWords -> 4
                pathWords.any { it.startsWith(token) } -> 5
                // Short units such as "ms" must not match arbitrary letters inside another word.
                token.length >= 3 && labelWords.any { token in it } -> 6
                token.length >= 3 && aliasWords.any { token in it } -> 7
                token.length >= 3 && pathWords.any { token in it } -> 8
                else -> return null
            }
        }
        val priority = when {
            query in labels -> 0
            labels.any { it.startsWith(query) } -> 1
            labelWordGroups.any { words -> tokens.all { token -> words.any { it.startsWith(token) } } } -> 2
            query in aliases -> 3
            aliases.any { it.startsWith(query) } -> 4
            else -> 5
        }
        return priority to cost
    }
}

/** Latin accent folding for the supported English/Italian vocabulary, including decomposed accents. */
private fun normalizeSettingsText(text: String): String = buildString {
    var separator = false
    for (original in text.lowercase()) {
        val character = when (original) {
            'à', 'á', 'â', 'ä', 'ã', 'å' -> 'a'
            'è', 'é', 'ê', 'ë' -> 'e'
            'ì', 'í', 'î', 'ï' -> 'i'
            'ò', 'ó', 'ô', 'ö', 'õ' -> 'o'
            'ù', 'ú', 'û', 'ü' -> 'u'
            'ç' -> 'c'
            'ñ' -> 'n'
            in '\u0300'..'\u036f' -> continue
            else -> original
        }
        if (character.isLetterOrDigit()) {
            if (separator && isNotEmpty()) append(' ')
            append(character)
            separator = false
        } else separator = isNotEmpty()
    }
}

private data class SearchTerms(val labels: List<String>, val aliases: String)
private fun terms(vararg labels: String, aliases: String) = SearchTerms(labels.toList(), aliases)

/** Both languages remain searchable regardless of the language currently displayed. */
private fun SettingsTarget.searchTerms(): SearchTerms = when (this) {
    SettingsTarget.InterfaceStyle -> terms("Interface style", "UI style", "Stile interfaccia",
        aliases = "theme|tema|themes|temi|liquid glass|material glass|miuix|hyperos|skin")
    SettingsTarget.Appearance -> terms("Appearance", "Aspetto", "Color scheme",
        aliases = "dark mode|modalità scura|light mode|modalità chiara|automatic|automatico|system|sistema|colori|colours|colors")
    SettingsTarget.ArtworkStyle -> terms("Artwork style", "Cover layout", "Stile copertina",
        aliases = "album art|artwork|cover|copertine|full bleed|tutto schermo|square|quadrata")
    SettingsTarget.ResumeOnReconnect -> terms("Resume on reconnect", "Riprendi al ricollegamento",
        aliases = "headphones|cuffie|bluetooth|wired|cavo|usb|car|auto|reconnect|ricollega|ripresa|disconnessione")
    SettingsTarget.ReplayGain -> terms("ReplayGain", "Volume normalization", "Normalizza volume", "Normalizzazione",
        aliases = "loudness|gain|guadagno|track|album|brano|volume|clipping|normalize|normalise")
    SettingsTarget.Crossfade -> terms("Crossfade", "Automatic crossfade", "Crossfade automatico", "Sfumatura tra brani",
        aliases = "transition|transizione|overlap|sovrapposizione|gapless|fade|dissolvenza|automatic advance|avanzamento automatico")
    SettingsTarget.CrossfadeDuration -> terms("Crossfade duration", "Durata crossfade", "Custom crossfade duration", "Durata personalizzata",
        aliases = "ms|milliseconds|millisecondi|millisecond|custom|personalizzata|exact|precisa|duration|durata|automatic|automatico")
    SettingsTarget.ManualCrossfadeDuration -> terms("Manual crossfade duration", "Durata crossfade manuale", "Manual track changes", "Cambio manuale",
        aliases = "ms|milliseconds|millisecondi|millisecond|skip|next|previous|avanti|indietro|transition|transizione|overlap|sovrapposizione|fade|dissolvenza|custom|personalizzata")
    SettingsTarget.ResumePerTrack -> terms("Resume each track", "Riprendi ogni brano", "Per-track resume",
        aliases = "resume|ripresa|riprendi|progress|progresso|posizione|podcast|audiolibri|audiobooks|long tracks|brani lunghi")
    SettingsTarget.LongTrackThreshold -> terms("Long track threshold", "Soglia brani lunghi", "Track duration",
        aliases = "minutes|minuti|durata|durazione|soglia|10 seconds|10 secondi|salto|podcast|audiobooks")
    SettingsTarget.AmllLyrics -> terms("Lyrics", "Testi", "AMLL lyrics", "Testi parola per parola",
        aliases = "amll|karaoke|word timed|synchronized|sincronizzati|sincronizzazione|testo|lyrics provider")
    SettingsTarget.ReduceMotion -> terms("Reduce motion", "Riduci movimento",
        aliases = "animation|animations|animazioni|accessibilità|accessibility")
    SettingsTarget.ReduceTransparency -> terms("Reduce transparency", "Riduci trasparenza",
        aliases = "transparency|trasparenza|blur|sfocatura|glass|vetro|leggibilità|accessibilità|accessibility")
    SettingsTarget.Equalizer -> terms("Equalizer", "Equalizzatore", "EQ",
        aliases = "sound|suono|audio|bands|bande|bass|bassi|treble|alti|graphic|grafico")
    SettingsTarget.EqualizerPresets -> terms("Equalizer presets", "Preimpostazioni equalizzatore", "Preset",
        aliases = "eq|presets|preimpostazioni|flat|piatto|voice|voce|more bass|bassi")
    SettingsTarget.OutputProfiles -> terms("Output profiles", "Profili per uscita",
        aliases = "equalizer|equalizzatore|eq|device|dispositivo|headphones|cuffie|bluetooth|wired|cavo|usb|speaker|altoparlante|automatic|automatico|associazione")
    SettingsTarget.HeadphoneCorrection -> terms("Headphone correction", "Correzione cuffie", "AutoEQ",
        aliases = "headphones|cuffie|autoeq|profile|profilo|import|importazione|correction|correzione|parametric|parametrico")
    SettingsTarget.MinimumDuration -> terms("Minimum duration", "Durata minima",
        aliases = "library|libreria|track|brano|tracks|brani|filter|filtro|seconds|secondi|short|brevi")
    SettingsTarget.ExcludedFolders -> terms("Excluded folders", "Cartelle escluse",
        aliases = "library|libreria|directory|cartelle|hide|nascondi|exclude|esclusioni|esclusione|filter|filtro")
    SettingsTarget.HomeCustomization -> terms("Customize Home", "Personalizza Home", "Home customization",
        aliases = "shortcuts|scorciatoie|sections|sezioni|order|ordine|pins|fissati|home")
    SettingsTarget.Backup -> terms("Backup", "Backup and restore", "Backup e ripristino",
        aliases = "export|esporta|import|importa|importazione|esportazione|restore|ripristina|save|salva|dati|settings|preferences|impostazioni|preferenze")
    SettingsTarget.About -> terms("About", "Informazioni",
        aliases = "privacy|licenses|licenze|data|dati|permissions|permessi|source|fonti|songlass")
}

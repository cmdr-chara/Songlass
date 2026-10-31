package dev.cmdrchara.songlass.core.settings

/** Stable destinations, independent of translated labels and the UI's navigation stack. */
enum class SettingsTarget {
    InterfaceStyle, LiquidMaterial, Appearance, ArtworkStyle, TextScale, BlurIntensity, SearchInNavigationBar, ResumeOnReconnect, ReplayGain, Crossfade,
    CrossfadeDuration, ManualCrossfadeDuration, AmllLyrics, ReduceMotion, ReduceTransparency, Equalizer,
    EqualizerPresets, OutputProfiles, HeadphoneCorrection, MinimumDuration, MinimumRating, ExcludedFolders,
    HomeCustomization, Backup, About, ResumePerTrack, LongTrackThreshold, PlayCountRule, AlbumLayout, SleepTimer,
    TrackClickAction, QueueStart, QueueAddOrder, QueueReplaces, QueueEnd, ShuffleBias, KeepShuffle, AppIcon,
    ReplayGainProcessing, ReplayGainPreamp, ReplayGainMissingPreamp, ReplayGainLimiter,
    Balance, StereoExpand, Tempo, Reverb, EqualizerBass, EqualizerTreble, EqualizerBands, EqualizerLock,
    ResumeBluetooth, BluetoothCommandDelay,
    FocusDucking, ResumeOnFocusGain, ResumeAfterCall,
    AudioFocus, PauseOnDisconnect, HeadsetMultiPress, LrclibLyrics, PauseFadeDuration, StopFadeDuration, SeekFadeDuration,
    OnlineArtworkEnabled, OnlineArtworkWifiOnly, LastFm,
    ResumeOverrides, ShowPlayerRating,
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
    fun search(query: String, supportsAmllLyrics: Boolean, supportsCrossfade: Boolean = true, supportsOnlineLyrics: Boolean = true, supportsTransportFades: Boolean = true, supportsReplayGainProcessing: Boolean = true, supportsAudioEffects: Boolean = true, supportsAudioFocusSettings: Boolean = true, supportsHeadphoneSettings: Boolean = true, supportsOnlineArtwork: Boolean = true, supportsScrobbling: Boolean = true): List<SettingsTarget> {
        val normalized = normalizeSettingsText(query)
        if (normalized.isEmpty()) return emptyList()
        val tokens = normalized.split(' ').distinct()
        return entries.mapIndexedNotNull { order, entry ->
            if (entry.target == SettingsTarget.AmllLyrics && !supportsAmllLyrics) return@mapIndexedNotNull null
            if (entry.target == SettingsTarget.LrclibLyrics && !supportsOnlineLyrics) return@mapIndexedNotNull null
            if (!supportsCrossfade && entry.target in crossfadeTargets) return@mapIndexedNotNull null
            if (!supportsTransportFades && entry.target in transportFadeTargets) return@mapIndexedNotNull null
            if (!supportsReplayGainProcessing && entry.target in replayGainProcessingTargets) return@mapIndexedNotNull null
            if (!supportsHeadphoneSettings && entry.target in setOf(SettingsTarget.ResumeOnReconnect, SettingsTarget.ResumeBluetooth, SettingsTarget.BluetoothCommandDelay)) return@mapIndexedNotNull null
            if (!supportsAudioFocusSettings && entry.target in setOf(SettingsTarget.FocusDucking, SettingsTarget.ResumeOnFocusGain, SettingsTarget.ResumeAfterCall)) return@mapIndexedNotNull null
            if (!supportsAudioEffects && entry.target in audioEffectTargets) return@mapIndexedNotNull null
            if (!supportsOnlineArtwork && entry.target in onlineArtworkTargets) return@mapIndexedNotNull null
            if (!supportsScrobbling && entry.target in scrobblingTargets) return@mapIndexedNotNull null
            entry.match(normalized, tokens)?.let { Match(entry.target, it.first, it.second, order) }
        }.sortedWith(compareBy<Match> { it.priority }.thenBy { it.cost }.thenBy { it.order })
            .map { it.target }
    }

    private data class Match(val target: SettingsTarget, val priority: Int, val cost: Int, val order: Int)

    private companion object {
        val onlineArtworkTargets = setOf(SettingsTarget.OnlineArtworkEnabled, SettingsTarget.OnlineArtworkWifiOnly)
        val scrobblingTargets = setOf(SettingsTarget.LastFm)
        val audioEffectTargets = setOf(SettingsTarget.Balance, SettingsTarget.StereoExpand, SettingsTarget.Tempo, SettingsTarget.Reverb, SettingsTarget.EqualizerBass, SettingsTarget.EqualizerTreble, SettingsTarget.EqualizerBands)
        val replayGainProcessingTargets = setOf(SettingsTarget.ReplayGainProcessing, SettingsTarget.ReplayGainPreamp, SettingsTarget.ReplayGainMissingPreamp, SettingsTarget.ReplayGainLimiter)
        val transportFadeTargets = setOf(SettingsTarget.PauseFadeDuration, SettingsTarget.StopFadeDuration, SettingsTarget.SeekFadeDuration)
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
    SettingsTarget.ShowPlayerRating -> terms("Show rating", "Player rating", "Mostra valutazione", "Valutazione nel player",
        aliases = "rating visibility|nascondi valutazione|hide rating|voto player|valutazione brano")
    SettingsTarget.InterfaceStyle -> terms("Interface style", "UI style", "Stile interfaccia",
        aliases = "theme|tema|themes|temi|liquid glass|material glass|miuix|hyperos|skin")
    SettingsTarget.LiquidMaterial -> terms("Liquid Glass finish", "Finitura Liquid Glass", "Liquid Metal",
        aliases = "metal|metallo|chrome|cromo|finitura|finish|riflessi|reflections|liquid metal")
    SettingsTarget.Appearance -> terms("Appearance", "Aspetto", "Color scheme",
        aliases = "dark mode|modalità scura|light mode|modalità chiara|automatic|automatico|system|sistema|colori|colours|colors")
    SettingsTarget.ArtworkStyle -> terms("Artwork style", "Cover layout", "Stile copertina",
        aliases = "album art|artwork|cover|copertine|full bleed|tutto schermo|square|quadrata")
    SettingsTarget.ResumeOnReconnect -> terms("Resume on reconnect", "Riprendi al ricollegamento", "Resume on wired headset", "Riprendi con cuffie cablate",
        aliases = "headphones|cuffie|wired|cavo|usb|reconnect|ricollega|ripresa|disconnessione|jack")
    SettingsTarget.ResumeBluetooth -> terms("Resume on Bluetooth", "Riprendi con Bluetooth",
        aliases = "headphones|cuffie|car|auto|earbuds|auricolari|a2dp|ble|ricollegamento bluetooth|bluetooth reconnect")
    SettingsTarget.BluetoothCommandDelay -> terms("Bluetooth command filter", "Filtro comandi Bluetooth", "Ignore Bluetooth commands",
        aliases = "tasti|buttons|headset|cuffie|headunit|auto|car|ritardo|delay|filtro|filter|connessione|connection|ignore|ignora")
    SettingsTarget.TrackClickAction -> terms("Tap action", "Song tap action", "Azione al tocco", "Tocco su un brano",
        aliases = "click|clic|tap|tocca|riproduci|play|enqueue|accoda|apri player|open player|lista|list")
    SettingsTarget.QueueStart -> terms("Queue start", "When additions play", "Quando partono le aggiunte",
        aliases = "queue|coda|accoda|add|aggiungi|subito|immediately|right away|dopo|after|next|prossimo|album|playlist")
    SettingsTarget.QueueAddOrder -> terms("Order of additions", "Ordine delle aggiunte",
        aliases = "queue|coda|shuffle|casuale|random|fondo|end|ordine|order")
    SettingsTarget.QueueReplaces -> terms("Replace queue additions", "Sostituisci le aggiunte", "Clear queue on add",
        aliases = "queue|coda|svuota|clear|replace|sostituisci|attesa|waiting")
    SettingsTarget.QueueEnd -> terms("End of additions", "Fine delle aggiunte", "Queue end",
        aliases = "queue|coda|stop|fermati|torna|return|continue|continua|album|playlist")
    SettingsTarget.ShuffleBias -> terms("Shuffle randomness", "Casualità dello shuffle", "Least played first",
        aliases = "shuffle|casuale|random|meno ascoltati|least played|bias|ascolti|plays|preferisci")
    SettingsTarget.KeepShuffle -> terms("Keep shuffle", "Mantieni lo shuffle", "No reshuffle",
        aliases = "shuffle|casuale|random|scegli|pick|selezione|selection|reshuffle|rimescola")
    SettingsTarget.AppIcon -> terms("App icon", "Night icon", "Icona dell’app", "Icona notte",
        aliases = "launcher|home|schermata home|icone|icons|logo|dark|scura|nera|black|heart|cuore|gold|oro")
    SettingsTarget.LrclibLyrics -> terms("LRCLIB lyrics", "Online lyrics", "Testi LRCLIB", "Testi online",
        aliases = "lyrics|testi|synced|sincronizzati|internet|rete|privacy|network|fonti|sources")
    SettingsTarget.FocusDucking -> terms("Short interruptions", "Volume durante le interruzioni", "Ducking volume",
        aliases = "ducking|abbassamento|notifiche|notifications|navigatore|navigator|attenuazione|attenuation|pausa breve|short pause")
    SettingsTarget.ResumeOnFocusGain -> terms("Resume on focus gain", "Riprendi al ritorno del focus",
        aliases = "ripresa focus|focus resume|interruzioni brevi|short interruptions|audio focus|altre app|other apps")
    SettingsTarget.ResumeAfterCall -> terms("Resume after call", "Riprendi dopo le chiamate",
        aliases = "chiamata|call|hang up|riaggancio|telefonata|phone|voip|conversazione|communication")
    SettingsTarget.AudioFocus -> terms("Audio focus", "Pause for calls", "Focus audio", "Interruzioni",
        aliases = "calls|chiamate|notifications|notifiche|duck|abbassa|interruption|interruzioni|other apps|altre app|mix|sovrapponi|navigator|navigatore")
    SettingsTarget.PauseOnDisconnect -> terms("Pause when disconnected", "Pausa allo scollegamento", "Pause on unplug",
        aliases = "headphones|cuffie|bluetooth|unplug|scollega|noisy|speaker|altoparlante|cavo|wired|jack")
    SettingsTarget.HeadsetMultiPress -> terms("Headset buttons", "Tasti delle cuffie", "Multiple presses",
        aliases = "headset|cuffie|button|tasto|double|doppio|triple|triplo|press|pressione|earbuds|auricolari")
    SettingsTarget.SleepTimer -> terms("Sleep timer", "Timer di spegnimento", "Timer",
        aliases = "sleep|sonno|dormire|spegnimento|stop music|ferma musica|end of track|fine brano|minutes|minuti|night|notte|bedtime")
    SettingsTarget.EqualizerBass -> terms("Bass", "Bassi", "Bass tone", "Tono bassi", aliases = "tone|toni|low shelf|shelf basso|basse frequenze|low frequencies|decibel|db")
    SettingsTarget.EqualizerTreble -> terms("Treble", "Alti", "Treble tone", "Tono alti", aliases = "tone|toni|high shelf|shelf alto|alte frequenze|high frequencies|decibel|db")
    SettingsTarget.EqualizerBands -> terms("Number of EQ bands", "Numero di bande EQ", "Bande configurabili", "Configurable bands", aliases = "graphic|grafico|layout|5|10|15|31|frequencies|frequenze|equalizer|equalizzatore|bande|bands")
    SettingsTarget.EqualizerLock -> terms("Equalizer lock", "Lock the EQ bands", "Blocco equalizzatore", "Blocca le bande",
        aliases = "lock|blocco|blocca|sblocca|unlock|accidental|accidentale|involontario|tocchi|touch|scroll|scorrimento|bande|bands|graph|grafico|equalizer|equalizzatore")
    SettingsTarget.Balance -> terms("Balance", "Bilanciamento", aliases = "left|right|sinistra|destra|canali|channels|stereo|pan")
    SettingsTarget.StereoExpand -> terms("Stereo expand", "Espansione stereo", aliases = "width|ampiezza|larghezza|spazialità|mid side|stereo widening")
    SettingsTarget.Tempo -> terms("Playback tempo", "Tempo di riproduzione", "Velocità di riproduzione", aliases = "speed|velocità|tempo|pitch|intonazione|lento|veloce|slow|fast")
    SettingsTarget.Reverb -> terms("Reverb", "Riverbero", aliases = "room|stanza|ambiente|ambience|echo|eco|reverberation|effetti|effects")
    SettingsTarget.ReplayGain -> terms("ReplayGain", "Volume normalization", "Normalizza volume", "Normalizzazione",
        aliases = "loudness|gain|guadagno|track|album|brano|volume|clipping|normalize|normalise")
    SettingsTarget.ReplayGainProcessing -> terms("Advanced ReplayGain", "ReplayGain avanzato", "Preamplificazione e limitatore",
        aliases = "replaygain|processing|elaborazione|boost|amplificazione|normalizzazione|normalization")
    SettingsTarget.ReplayGainPreamp -> terms("ReplayGain preamp", "Preamplificazione con tag", "Preamp with tags",
        aliases = "replaygain|preamp|preamplificazione|tag|gain|guadagno|decibel|db|volume")
    SettingsTarget.ReplayGainMissingPreamp -> terms("Preamp without tags", "Preamplificazione senza tag", "Missing ReplayGain preamp",
        aliases = "replaygain|preamp|preamplificazione|untagged|senza tag|missing|mancanti|decibel|db|volume")
    SettingsTarget.ReplayGainLimiter -> terms("ReplayGain limiter", "Limitatore ReplayGain", "Prevenzione clipping",
        aliases = "replaygain|limiter|limitatore|clipping|picchi|peaks|protezione|protection|distorsione|distortion")
    SettingsTarget.Crossfade -> terms("Crossfade", "Automatic crossfade", "Crossfade automatico", "Sfumatura tra brani",
        aliases = "transition|transizione|overlap|sovrapposizione|gapless|fade|dissolvenza|automatic advance|avanzamento automatico")
    SettingsTarget.CrossfadeDuration -> terms("Crossfade duration", "Durata crossfade", "Custom crossfade duration", "Durata personalizzata",
        aliases = "ms|milliseconds|millisecondi|millisecond|custom|personalizzata|exact|precisa|duration|durata|automatic|automatico")
    SettingsTarget.ManualCrossfadeDuration -> terms("Manual crossfade duration", "Durata crossfade manuale", "Manual track changes", "Cambio manuale",
        aliases = "ms|milliseconds|millisecondi|millisecond|skip|next|previous|avanti|indietro|transition|transizione|overlap|sovrapposizione|fade|dissolvenza|custom|personalizzata")
    SettingsTarget.PauseFadeDuration -> terms("Pause fade", "Dissolvenza pausa", "Pause and resume fade", "Dissolvenza pausa e ripresa",
        aliases = "pause|pausa|resume|ripresa|fade|dissolvenza|milliseconds|millisecondi|ms|duration|durata")
    SettingsTarget.StopFadeDuration -> terms("Stop fade", "Dissolvenza stop", "Fade before stopping", "Dissolvenza arresto",
        aliases = "stop|arresto|ferma|fade|dissolvenza|milliseconds|millisecondi|ms|duration|durata")
    SettingsTarget.SeekFadeDuration -> terms("Seek fade", "Dissolvenza seek", "Dissolvenza spostamento",
        aliases = "seek|scrub|posizione|spostamento|cursore|fade|dissolvenza|milliseconds|millisecondi|ms|duration|durata")
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
    SettingsTarget.TextScale -> terms("Text size", "App text size", "Dimensione del testo", "Testo dell’app",
        aliases = "font|font size|testo|scala|dimensione|carattere|large text|testo grande|ingrandisci|enlarge|dynamic type|accessibilità|accessibility|leggibilità|readability|display")
    SettingsTarget.SearchInNavigationBar -> terms("Search in the navigation bar", "Search tab in the bar", "Cerca nella barra di navigazione",
        aliases = "navigation|navigazione|navbar|bar|barra|tab|tab bar|search|cerca|search button|pulsante cerca|circle|cerchio|unified|unica|merge|unisci|liquid glass")
    SettingsTarget.BlurIntensity -> terms("Background blur", "Blur intensity", "Sfocatura dello sfondo", "Intensità della sfocatura",
        aliases = "blur|sfocatura|glass|vetro|background|sfondo|intensità|intensity|effetto vetro|glass effect|trasparenza|transparency|display")
    SettingsTarget.Equalizer -> terms("Equalizer", "Equalizzatore", "EQ",
        aliases = "sound|suono|audio|bands|bande|bass|bassi|treble|alti|graphic|grafico")
    SettingsTarget.EqualizerPresets -> terms("Equalizer presets", "Preimpostazioni equalizzatore", "Preset",
        aliases = "eq|presets|preimpostazioni|flat|piatto|voice|voce|more bass|bassi")
    SettingsTarget.OutputProfiles -> terms("Output profiles", "Profili per uscita",
        aliases = "equalizer|equalizzatore|eq|device|dispositivo|headphones|cuffie|bluetooth|wired|cavo|usb|speaker|altoparlante|automatic|automatico|associazione")
    SettingsTarget.HeadphoneCorrection -> terms("Headphone correction", "Correzione cuffie", "AutoEQ", "Cuffie",
        aliases = "headphones|cuffie|autoeq|profile|profilo|import|importazione|correction|correzione|parametric|parametrico")
    SettingsTarget.MinimumDuration -> terms("Minimum duration", "Durata minima",
        aliases = "library|libreria|track|brano|tracks|brani|filter|filtro|seconds|secondi|short|brevi")
    SettingsTarget.MinimumRating -> terms("Minimum rating", "Valutazione minima", "Minimum stars", "Stelle minime",
        aliases = "library|libreria|rating|ratings|valutazione|valutazioni|stars|stelle|star|stella|filter|filtro|rating filter|filtro valutazione|5 stelle|5 stars")
    SettingsTarget.ExcludedFolders -> terms("Excluded folders", "Cartelle escluse",
        aliases = "library|libreria|directory|cartelle|hide|nascondi|exclude|esclusioni|esclusione|filter|filtro")
    SettingsTarget.PlayCountRule -> terms("Count a play after", "Play count rule", "Conta un ascolto dopo", "Regola conteggio ascolti",
        aliases = "statistics|statistiche|history|cronologia|listening|ascolti|30 seconds|30 secondi|50 percent|90 percent|soglia")
    SettingsTarget.AlbumLayout -> terms("Album layout", "Layout album", "Album view", "Vista album",
        aliases = "library|libreria|grid|griglia|list|lista|density|densità|rows|righe|covers|copertine")
    SettingsTarget.HomeCustomization -> terms("Customize Home", "Personalizza Home", "Home customization",
        aliases = "shortcuts|scorciatoie|sections|sezioni|order|ordine|pins|fissati|home")
    SettingsTarget.Backup -> terms("Backup", "Backup and restore", "Backup e ripristino",
        aliases = "export|esporta|import|importa|importazione|esportazione|restore|ripristina|save|salva|dati|settings|preferences|impostazioni|preferenze")
    SettingsTarget.OnlineArtworkEnabled -> terms("Online covers", "Cover search", "Copertine online", "Ricerca copertine",
        aliases = "artwork|cover|copertina|copertine|album art|download|scaricare|itunes|internet|rete|network|privacy|immagini|images")
    SettingsTarget.OnlineArtworkWifiOnly -> terms("Download on Wi-Fi only", "Scarica solo in Wi-Fi", "Wi-Fi only covers",
        aliases = "wifi|wi fi|dati mobili|mobile data|metered|rete|network|download|scaricare|copertine|covers|risparmio dati|data saver")
    SettingsTarget.About -> terms("About", "Informazioni",
        aliases = "privacy|licenses|licenze|data|dati|permissions|permessi|source|fonti|songlass")
    SettingsTarget.LastFm -> terms("Last.fm", "Last.fm scrobbling", "Scrobbling Last.fm",
        aliases = "lastfm|last fm|scrobble|scrobbling|scrobbler|scrobbli|ascolti|listening|account|profilo|profile|collega|connect|sessione|session|api|chiave|key|social")
    SettingsTarget.ResumeOverrides -> terms("Resume by collection", "Collection resume", "Ripresa per raccolta",
        "Eccezioni di ripresa", "Ripresa per cartella",
        aliases = "resume|ripresa|riprendi|continua|continuare|collection|raccolta|raccolte|folder|cartella|cartelle|playlist|override|eccezione|eccezioni|exception|exceptions|threshold|soglia|ripresa automatica")
}

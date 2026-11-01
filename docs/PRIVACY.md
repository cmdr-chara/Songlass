# Songlass — informativa sui dati, bozza da completare

Aggiornamento tecnico: 21 ottobre 2026. Ambito: applicazione Android e codice di produzione
presente nel repository a questa data. Questa bozza documenta il comportamento implementato;
non è ancora una pagina pubblica approvata per uno store. Il proprietario deve fornire identità
del titolare, contatto privacy, URL pubblico e data di efficacia prima della pubblicazione.
Non sono stati inventati recapiti o indirizzi. La compilazione del modulo iOS non dimostra
che questi flussi siano disponibili o verificati su iPhone.

## Integrazione musicale iOS e isole di sistema — 28 ottobre 2026

Il nuovo sorgente iOS richiede il consenso di accesso alla libreria Music di sistema. Legge metadati e copertine
dei brani locali riproducibili; esclude elementi solo cloud o protetti. Non usa questo consenso per un invio della
libreria a Songlass o per un abbonamento Apple Music. Il codice è stato aggiunto localmente; runtime iPhone e
la nuova IPA restano da verificare.

Il player fornisce al sistema metadati, copertina e stato di riproduzione per isola, blocco schermo e controlli
multimediali. Su Xiaomi i dati di condivisione per trascinamento contengono testo ricavato da titolo/artista/album;
non contengono il file audio, il suo URI locale o un collegamento pubblico generato. Il sistema e l'app destinataria
gestiscono un'eventuale condivisione avviata dall'utente. Questa integrazione non aggiunge un servizio di rete.
Fonti: [libreria iOS](../shared/src/iosMain/kotlin/dev/cmdrchara/songlass/IosMusicSource.kt),
[Now Playing](../shared/src/iosMain/kotlin/dev/cmdrchara/songlass/IosNowPlaying.kt),
[contratto condiviso](../core/src/commonMain/kotlin/dev/cmdrchara/songlass/core/playback/SystemNowPlaying.kt).

## Dati usati sul dispositivo

Songlass è un lettore di file musicali locali. Su Android chiede il permesso di leggere i file
audio per indicizzare e riprodurre la raccolta. Legge metadati come titolo, artisti, album,
durata, cartella, formato e copertine. Il servizio di riproduzione mantiene i controlli audio
disponibili anche fuori dalla schermata dell'app. I permessi dichiarati sono lettura audio,
Internet e servizio in primo piano per la riproduzione; non sono dichiarati permessi di
microfono, contatti o posizione nel manifest applicativo esaminato.
Il manifest Android unito include anche `ACCESS_NETWORK_STATE` e `WAKE_LOCK`, forniti
dall'integrazione Media3 per rete e riproduzione; sono stati riscontrati nell'APK debug
integrato e vengono controllati anche nell'artefatto release.
Fonti: [manifest](../androidApp/src/main/AndroidManifest.xml),
[scansione MediaStore](../androidApp/src/main/kotlin/dev/cmdrchara/songlass/library/MediaStoreMusicSource.kt).

L'app conserva localmente preferenze, preferiti, playlist, personalizzazione della Home,
profili di equalizzazione, riferimenti alla coda, statistiche di ascolto, posizioni di ripresa
e segnalibri. La cronologia della ricerca registra elementi aperti dalla ricerca, non il
testo digitato. I testi importati sono associati alla traccia. Questi dati risiedono nelle
preferenze private dell'app; i riferimenti ai file possono comprendere identificativi MediaStore
e URI validi solo sul dispositivo su cui sono stati creati.
Fonti: [store Android](../androidApp/src/main/kotlin/dev/cmdrchara/songlass/SharedPreferencesStore.kt),
[cronologia](../core/src/commonMain/kotlin/dev/cmdrchara/songlass/core/history/ListeningHistory.kt),
[cronologia ricerca](../core/src/commonMain/kotlin/dev/cmdrchara/songlass/core/search/SearchHistory.kt),
[testi](../core/src/commonMain/kotlin/dev/cmdrchara/songlass/core/lyrics/LyricsRepository.kt),
[segnalibri e ripresa](../core/src/commonMain/kotlin/dev/cmdrchara/songlass/core/playback/TrackMarkers.kt).

Nel codice applicativo e nelle dipendenze dichiarate esaminate non sono stati individuati
servizi di pubblicità, analytics, account Songlass o invio automatico di crash a un server.
Questa è una conclusione della revisione del sorgente, non una certificazione di ogni
dipendenza transitiva né una cattura del traffico di una release in esecuzione. I servizi
Android, il dispositivo e le altre app possono avere proprie impostazioni diagnostiche.

## Quando viene usata la rete

La ricerca e la riproduzione della libreria sono locali. I flussi di rete applicativi
identificati riguardano testi, profili AutoEQ e, solo se l'utente la attiva, la ricerca delle
copertine. Le richieste usano HTTPS; il codice limita
dimensioni delle risposte, attese e redirect. Non invia file audio ai servizi elencati.
Questi servizi ricevono i parametri della richiesta e i normali dati di connessione, compreso
l'indirizzo IP. Le richieste identificano l'app con un header User-Agent Songlass. L'assenza
di un account nell'app non rende queste richieste anonime per il server.

| Funzione e destinatario | Quando avviene | Dati inviati dal codice |
| --- | --- | --- |
| Testi parola per parola, `api.amll.dev` | Quando si apre il pannello testi, non sono disponibili testi importati/locali o un risultato già utilizzabile e AMLL è abilitato. Il provider può precedere LRCLIB. | Titolo del brano, primo artista accreditato, album se presente; pagina/dimensione della ricerca e identificativo del risultato scelto per recuperarne il testo. La durata e gli altri crediti servono al confronto/cache locale, non sono parametri aggiuntivi della richiesta AMLL. |
| Testi, `lrclib.net` | Nel flusso di caricamento dei testi, quando le sorgenti precedenti non danno un risultato sufficiente e non è disponibile un risultato in cache. | Titolo, artista, album se presente e durata se compresa tra un secondo e un'ora. |
| Profilo AutoEQ, `raw.githubusercontent.com` | Quando si seleziona una correzione non già disponibile localmente. La ricerca nel catalogo incluso nell'app avviene offline. | Percorso del modello/profilo selezionato e revisione del catalogo nel progetto AutoEq. Non vengono aggiunti metadati dei brani o la libreria musicale. |
| Collegamento AMLL su `github.com` | Solo quando si preme il collegamento alla fonte nella schermata Informazioni. | Apertura dell'URL nel gestore del dispositivo; il browser/app destinataria gestisce la visita. |
| Scrobbling, `ws.audioscrobbler.com` | Solo con lo scrobbling Last.fm attivo e un account collegato: «now playing» all'inizio di un brano, ascolti validi in coda e inviati a gruppi. | Artista, titolo, album se presente, durata, numero di traccia e ora di inizio dell'ascolto, più la chiave API e la chiave di sessione necessarie alla firma. Mai percorsi, nomi di file, audio o il resto della libreria. |
| Autorizzazione Last.fm, `www.last.fm` | Solo quando l'utente preme «Collega» e poi «Apri Last.fm» nella pagina Impostazioni → Last.fm. | Apertura della pagina di autorizzazione nel browser del dispositivo, con chiave API e token temporaneo; il browser e Last.fm gestiscono la visita e l'accesso all'account. |
| Copertine, `itunes.apple.com` | Con «Copertine online» attivo, recupero automatico della copertina mancante del brano corrente mentre l’app è in primo piano, rispettando la scelta della rete. Un risultato non ambiguo viene salvato automaticamente; la ricerca manuale e la scelta di un’altra copertina restano disponibili. | Artista e titolo del brano, oppure artista e album per una ricerca manuale dell’album, più i parametri della richiesta (media, entity, limite, store country) e l’URL dell’immagine. Non vengono aggiunti percorsi, audio o identificativi di libreria. Se mancano i tag, il titolo può derivare dal nome del file. |

Fonti: [pannello testi](../shared/src/commonMain/kotlin/dev/cmdrchara/songlass/ui/LyricsPane.kt),
[ordine delle sorgenti](../shared/src/commonMain/kotlin/dev/cmdrchara/songlass/AppGraph.kt),
[AMLL](../androidApp/src/main/kotlin/dev/cmdrchara/songlass/lyrics/AmllLyricsSource.kt),
[trasporto AMLL](../androidApp/src/main/kotlin/dev/cmdrchara/songlass/lyrics/LyricsHttpRequest.kt),
[LRCLIB](../androidApp/src/main/kotlin/dev/cmdrchara/songlass/lyrics/LrcLibLyricsSource.kt),
[catalogo AutoEQ](../core/src/commonMain/kotlin/dev/cmdrchara/songlass/core/equalizer/AutoEqCatalog.kt),
[download AutoEQ](../shared/src/androidMain/kotlin/dev/cmdrchara/songlass/ui/AutoEqPlatform.android.kt),
[Informazioni](../shared/src/commonMain/kotlin/dev/cmdrchara/songlass/ui/AboutScreen.kt).

**AMLL è abilitato per impostazione iniziale. Disattivarlo non disattiva LRCLIB.**
Il codice attuale non offre un interruttore generale per bloccare tutte le richieste dei testi.
Testi importati o incorporati hanno precedenza sulle sorgenti remote. Non aprire il pannello
testi evita questo flusso di ricerca online; la riproduzione non lo richiede.
Fonte: [preferenze](../core/src/commonMain/kotlin/dev/cmdrchara/songlass/core/settings/AppPreferences.kt)
e [repository testi](../core/src/commonMain/kotlin/dev/cmdrchara/songlass/core/lyrics/LyricsRepository.kt).

**Le copertine online sono attive per impostazione iniziale, con download solo su rete non a consumo.** Una preferenza esplicitamente disattivata rimane disattivata dopo l’aggiornamento. Con l'interruttore spento
l'app non contatta `itunes.apple.com`: la ricerca e il download restano bloccati dal codice. Con «Scarica solo in Wi-Fi» (predefinito)
il download attende una rete non a consumo; se il sistema non può indicare il tipo di
connessione, l'app assume la connessione a consumo e attende invece di scaricare. Non viene
mai scritto nulla nei file audio né nella raccolta multimediale. Le immagini scaricate restano
in una directory privata dell'app (`files/online-covers`), sono associate all'album logico
(artista e album); per la ricerca dal brano la scelta è associata localmente alla sua identità canonica, che non viene inviata al provider. Le copertine si eliminano dalla stessa pagina o dalle
Impostazioni, oppure con la cancellazione dei dati dell'app.
Fonti: [preferenze](../core/src/commonMain/kotlin/dev/cmdrchara/songlass/core/settings/AppPreferences.kt),
[provider e filtro](../core/src/commonMain/kotlin/dev/cmdrchara/songlass/core/artwork/ITunesArtwork.kt),
[ricerca](../core/src/commonMain/kotlin/dev/cmdrchara/songlass/core/artwork/ArtworkSearch.kt),
[cache privata](../core/src/commonMain/kotlin/dev/cmdrchara/songlass/core/artwork/DownloadedArtworks.kt),
[directory e downloader](../shared/src/androidMain/kotlin/dev/cmdrchara/songlass/ui/OnlineArtworkPlatform.android.kt).

**Cartella delle copertine su Android.** L’utente può autorizzare una cartella tramite il selettore di sistema. Songlass legge solo immagini corrispondenti al brano/album e conserva il permesso locale; nessuna immagine della cartella viene inviata online. Il permesso si può rimuovere dalle impostazioni delle copertine.

**Lo scrobbling Last.fm è disattivato per impostazione iniziale e richiede una chiave API
propria.** Songlass non include chiavi Last.fm: l'utente incolla la chiave e il segreto di un
account API personale nella pagina Impostazioni → Last.fm, e solo dopo può collegare l'account
e attivare l'invio. Con la funzione spenta o senza chiave il codice non contatta
`ws.audioscrobbler.com` e non invia nulla, e i brani non ascoltati per almeno metà durata (o
quattro minuti) non vengono inviati. Chiave API, segreto, chiave di sessione e coda degli
ascolti in attesa sono conservati nelle preferenze private dell'app in testo semplice, protetti
dallo spazio dati del sistema operativo ma non cifrati da Songlass: è un limite dichiarato.
Queste voci usano il prefisso `lastfm.*` e quindi non entrano nel backup `.songlass` v1 (che
esporta `settings.*`, `library.*`, `home.*`): dopo un ripristino su un altro dispositivo lo
scrobbling va riconfigurato. «Scollega» cancella chiave di sessione e coda; «Scollega e
dimentica» cancella anche chiave e segreto e disattiva la funzione.
Fonti: [preferenze](../core/src/commonMain/kotlin/dev/cmdrchara/songlass/core/settings/AppPreferences.kt),
[chiavi](../core/src/commonMain/kotlin/dev/cmdrchara/songlass/core/scrobble/LastFmStorageKeys.kt),
[client](../core/src/commonMain/kotlin/dev/cmdrchara/songlass/core/scrobble/LastFmClient.kt),
[coda](../core/src/commonMain/kotlin/dev/cmdrchara/songlass/core/scrobble/ScrobbleQueue.kt),
[regola d'ascolto](../core/src/commonMain/kotlin/dev/cmdrchara/songlass/core/scrobble/LastFmListenRule.kt),
[pagina Impostazioni](../shared/src/commonMain/kotlin/dev/cmdrchara/songlass/ui/LastFmSettingsPage.kt).

La conservazione dei log, le sedi di trattamento e le condizioni dei servizi esterni non
sono determinate dal codice Songlass. Prima di pubblicare questa informativa, il titolare
deve verificare e collegare le informative effettive dei fornitori e classificare correttamente
questi flussi nella scheda dati dello store. Questo documento non presume che i fornitori
cancellino immediatamente le richieste o non ne conservino traccia.

## Backup e trasferimento ad altri dispositivi

Songlass esclude i dati privati dell'app dal backup automatico Android nel cloud e dal
trasferimento Android tra dispositivi: il manifest disabilita il backup e le regole XML
escludono esplicitamente preferenze, file, database, cartelle private e dati protetti del
dispositivo. Questa scelta evita di trasferire identificativi di una diversa libreria o
copie di recupero di una modifica audio interrotta. Le regole esprimono la policy richiesta
al sistema; il comportamento dei diversi produttori deve ancora essere provato su dispositivi
reali. La modifica non cancella retroattivamente eventuali copie create da versioni precedenti.
Fonti: [manifest](../androidApp/src/main/AndroidManifest.xml),
[regole attuali](../androidApp/src/main/res/xml/data_extraction_rules.xml),
[regole legacy](../androidApp/src/main/res/xml/backup_rules.xml).

Per trasferire i propri dati si può usare **Backup e ripristino** dentro Songlass. L'esportazione
scrive un file nella destinazione scelta tramite il selettore Android. Include gli elementi
gestiti dal formato: preferiti, playlist, impostazioni, cronologia, profili EQ, testi importati,
segnalibri e posizioni di ripresa. Include anche identità delle tracce necessarie al ripristino,
che possono contenere titolo, artista, album, durata, URI e percorso relativo. Non contiene
i file audio, la coda di riproduzione o i download in cache. Il file è testo strutturato senza
cifratura aggiunta da Songlass; il controllo di integrità non è una firma di autenticità.
La destinazione scelta potrebbe essere un provider cloud: in quel caso quel provider gestisce
il file. L'app non esegue un caricamento separato verso un server Songlass.
Fonti: [selettore/import/export Android](../shared/src/androidMain/kotlin/dev/cmdrchara/songlass/ui/BackupFiles.android.kt),
[allowlist](../core/src/commonMain/kotlin/dev/cmdrchara/songlass/core/backup/BackupKeys.kt),
[formato](../core/src/commonMain/kotlin/dev/cmdrchara/songlass/core/backup/BackupFormat.kt),
[ripristino](../core/src/commonMain/kotlin/dev/cmdrchara/songlass/core/backup/DataBackup.kt).

La disinstallazione o la cancellazione dei dati dell'app non ripristina automaticamente le
preferenze tramite questa policy. Un'esportazione scelta dall'utente resta nella sua destinazione
finché non viene eliminata da lì. La policy non governa i backup personali dei file musicali
o le copie gestite da altre app.

## Modifiche ai file e condivisione

La modifica di tag o copertina avviene solo nel flusso di modifica richiesto dall'utente, con
gli eventuali consensi Android necessari a scrivere il file. Per proteggere il brano da un
salvataggio interrotto, l'app prepara copie locali dell'originale e del risultato in
`files/tag-editor`, con un registro di recupero e una prova locale dell'installazione esclusa
dal backup. Dopo una scrittura verificata i file temporanei vengono rimossi. Se il recupero
non può identificare con certezza il file originale, le copie restano disponibili: l'utente
può esportare uno ZIP tramite il selettore Android e, soltanto dopo verifica della copia
esportata, chiudere esplicitamente il recupero. La chiusura conserva anche le copie private
in `noBackupFilesDir/tag-recovery-archives`, senza riscrivere il file audio ambiguo. Lo ZIP
può contenere file audio completi e metadati: non viene cifrato da Songlass e va conservato
in una destinazione adatta. L'esportazione richiede un documento locale vuoto, leggibile
e riscrivibile per verificare i byte; un provider incompatibile restituisce un errore e
lascia intatte le copie private. Le copie archiviate restano fino alla cancellazione dei
dati o alla disinstallazione; non esiste un browser interno dedicato per recuperarle.
Le regole di backup non eliminano questi file e non impediscono il recupero sullo stesso
dispositivo. Le copie audio non vengono incluse nel backup manuale dei dati Songlass.
Fonte: [editor dei tag](../androidApp/src/main/kotlin/dev/cmdrchara/songlass/library/TagLibFileTagEditor.kt).

La condivisione di una scheda testi genera un PNG in una cache privata e apre il selettore
di condivisione Android. L'app selezionata riceve un permesso temporaneo di lettura del PNG;
gestisce poi l'invio secondo le proprie regole. I controlli multimediali e i browser compatibili,
incluso Android Auto, possono leggere metadati della raccolta e copertine per mostrare il player
e navigare la musica. Questa integrazione non costituisce un servizio di sincronizzazione
Songlass.
Fonti: [condivisione](../shared/src/androidMain/kotlin/dev/cmdrchara/songlass/ui/LyricsCardSharing.android.kt),
[sessione multimediale](../androidApp/src/main/kotlin/dev/cmdrchara/songlass/playback/PlaybackService.kt),
[provider copertine](../androidApp/src/main/kotlin/dev/cmdrchara/songlass/artwork/ArtworkProvider.kt).

## Conservazione e controllo dei dati locali

Preferenze, playlist, importazioni, segnalibri e totali storici restano nel contenitore
dell'app finché non vengono modificati/rimossi dai rispettivi flussi o cancellati insieme ai
dati dell'app. I totali storici non hanno una scadenza automatica. Gli eventi datati usati
per le statistiche sono limitati a 50.000 entro una finestra di 731 giorni; la manutenzione
avviene quando il repository legge/aggiorna i dati, non tramite una cancellazione pianificata.
La cronologia di ricerca conserva fino a 20 elementi aperti e dispone di cancellazione.
Fonti: [cronologia ascolti](../core/src/commonMain/kotlin/dev/cmdrchara/songlass/core/history/ListeningHistory.kt),
[cronologia ricerca](../core/src/commonMain/kotlin/dev/cmdrchara/songlass/core/search/SearchHistory.kt).

Le cache dei testi conservano fino a 32 risultati per provider; dopo 30 giorni un risultato
non viene più riutilizzato. Questa scadenza di lettura non garantisce l'eliminazione fisica
esattamente al trentesimo giorno. Le schede testi più vecchie di un giorno sono pulite quando
si prepara una nuova scheda. La cache AutoEQ limita i profili scaricati a 32, con dimensione
massima di 64 KiB ciascuno. Le copertine scaricate, quando la funzione è attiva, sono limitate
a 32 MiB e a 120 elementi in una directory privata dell'app; oltre il limite la copertina più
vecchia viene eliminata. Android può eliminare i file nella directory cache; i profili
AutoEQ scaricati sono invece conservati nello store delle preferenze e non sono rimossi
dalla sola pulizia della cache Android. Non esiste nel codice esaminato un servizio che
cancelli copie già consegnate ad altre app o provider.
Fonti: [cache testi](../androidApp/src/main/kotlin/dev/cmdrchara/songlass/lyrics/LyricsDiskCache.kt),
[schede testi](../shared/src/androidMain/kotlin/dev/cmdrchara/songlass/ui/LyricsCardSharing.android.kt),
[cache AutoEQ](../core/src/commonMain/kotlin/dev/cmdrchara/songlass/core/equalizer/AutoEqCatalog.kt).

## Dati che il proprietario deve fornire prima della pubblicazione

| Elemento | Stato |
| --- | --- |
| Identità del titolare/editore e contatto per richieste sui dati | Da fornire dal proprietario. Il nome dell'autore Git non è stato assunto come recapito legale. |
| URL pubblico stabile di questa informativa e data di efficacia | Da fornire e pubblicare dopo revisione. La pubblicazione del codice non sostituisce una policy privacy completa. |
| Paesi di distribuzione, pubblico previsto e informazioni richieste dalle norme applicabili | Da definire dal proprietario. |
| Informative e condizioni effettive di AMLL, LRCLIB e GitHub/AutoEq | Da verificare per la release; nessuna politica di conservazione dei fornitori è stata inventata. |
| Scheda dati/privacy dello store e corrispondenza con il testo nell'app | Da compilare e verificare sulla release finale, includendo metadati inviati per i testi, download AutoEQ e condivisioni scelte dall'utente. |
| Verifica finale dei flussi di rete e dei manifest delle dipendenze | Aperta: revisione statica eseguita, cattura del traffico/runtime della release non eseguita. |

Per prove e limiti della policy Android vedere il
report di preparazione alla pubblicazione.

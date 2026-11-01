# Scheda store — bozza

Non pubblicata. Testo preparato dal codice corrente; non sostituisce l'approvazione dell'account
store, la QA su dispositivi reali o la verifica delle dichiarazioni richieste dal distributore.

## Italiano

**Nome:** Songlass

**Descrizione breve:** La tua musica, tre stili e controlli precisi per audio, coda e raccolta.

**Descrizione completa**

Songlass è un lettore per i file musicali presenti sul tuo dispositivo Android. Organizza la
raccolta, scegli il tuo stile e controlla come prosegue l'ascolto.

- Tre interfacce: Liquid Glass, Material Glass e Miuix, con aspetto chiaro o scuro e opzioni
  per ridurre movimento e trasparenza.
- Libreria per brani, album, artisti e cartelle, con categorie per artista dell'album, anno e
  compositore quando i file contengono quei tag. Ricerca locale e navigazione alfabetica.
- Playlist personali e intelligenti, importazione/esportazione M3U e M3U8, preferiti e Home
  personalizzabile. Le aggiunte temporanee alla coda tornano alla raccolta di partenza.
- Riproduzione gapless, crossfade automatico e manuale configurabili, ReplayGain e timer.
  Equalizzatore grafico e parametrico, profili per uscita e catalogo AutoEQ.
- Ripresa per singola traccia e segnalibri, utili per registrazioni lunghe. Statistiche con
  regole di conteggio configurabili per i nuovi ascolti.
- Testi locali e importati, inclusi formati sincronizzati; ricerca online tramite i servizi
  indicati nell'informativa. L'integrazione AMLL si può disattivare nelle impostazioni.
- Editor di tag e copertine, backup manuale dei dati dell'app e strumenti per conservare
  materiale di recupero dopo una modifica interrotta.

Songlass non include un catalogo musicale e non offre streaming dei file audio. Le informazioni
tecniche distinguono i dati del file, l'elaborazione osservata e ciò che Android comunica
sull'uscita: non promettono riproduzione bit-perfect o capacità hardware non rilevate.

La ricerca online dei testi e i download AutoEQ richiedono una connessione e possono comunicare
ai rispettivi servizi i dati descritti nell'informativa. I backup manuali non sono cifrati da
Songlass; scegli una destinazione adatta ai tuoi dati. I controlli esterni dipendono dal sistema
e dal dispositivo collegato.

## English

**Name:** Songlass

**Short description:** Your music, three looks, and precise control of playback and your library.

**Full description**

Songlass plays the music files on your Android device. Organize your collection, choose your
look, and decide how the listening session continues.

- Three interfaces: Liquid Glass, Material Glass and Miuix, with light and dark appearance
  and options to reduce motion and transparency.
- Browse songs, albums, artists and folders, plus album artist, year and composer categories
  when those tags are available. Search locally and jump through alphabetical lists.
- Personal and smart playlists, M3U/M3U8 import and export, favorites and a customizable Home.
  Temporary queue additions return to the original collection when finished.
- Gapless playback, separate automatic and manual crossfade controls, ReplayGain and a sleep
  timer. Graphic and parametric EQ, output profiles and an AutoEQ catalog.
- Per-track resume and bookmarks for longer recordings. Listening statistics with configurable
  counting rules for new plays.
- Local and imported lyrics, including synchronized formats, and online lookup through the
  services listed in the privacy information. AMLL integration can be disabled in Settings.
- Tag and cover editing, manual app-data backups, and tools to preserve recovery material
  after an interrupted edit.

Songlass does not include a music catalog or stream audio files. Technical information separates
source-file details, observed processing and the output data Android exposes. It makes no
bit-perfect or unobserved hardware-capability claims.

Online lyrics lookup and AutoEQ downloads require a connection and may send the data described
in the privacy information to those services. Songlass does not encrypt manual backup files;
choose a suitable destination. External controls depend on the system and connected device.

## Materiale e decisioni necessari prima della pubblicazione

| Voce | Stato |
|---|---|
| Nome e applicationId | Songlass / `dev.cmdrchara.songlass`, già nel progetto |
| Versione | `0.1.0`, versionCode `1`, mantenuti; nessuna release pubblica creata |
| Contatto assistenza e titolare | Da fornire dal proprietario; non usare automaticamente l'indirizzo noreply dell'autore git |
| URL pubblico privacy | Testo in `docs/PRIVACY.md`; hosting/URL non pubblicati né inventati |
| Firma e account store | Chiave privata/credenziali non presenti; non generarne una sostitutiva senza mandato specifico |
| Screenshot e video promozionali | Servono catture reali delle build funzionanti; gli ANR diagnostici non sono materiale promozionale |
| Icona | Asset originali Songlass già presenti; nessun asset di terzi senza permesso |
| Questionari store | Da compilare sull'artefatto finale e sui servizi effettivi; il report dati non equivale a certificazione |
| QA di rilascio | Checklist aperta nel report Android; compilazione/R8 non sostituiscono telefono, audio e accessibilità |

Non includere credenziali, registrazioni personali, fixture debug
o i file privati usati per la verifica negli asset o negli allegati pubblici dello store.

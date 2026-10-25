# Icone Songlass

Le otto finiture condividono il cuore originale e dieci barre radiali del generatore: `violet`, `night`, `liquid_light`, `liquid_dark`, `material_light`, `material_dark`, `miuix_light`, `miuix_dark`. Nessun asset o marchio Apple, Google o Xiaomi; nessuna immagine generata con AI.

Nei fallback SVG/PNG, Liquid usa perla/grafite con ombreggiatura vettoriale sobria; Material lavanda/blu tonali e superfici piatte; Miuix ciano/corallo. Su Android le sei finiture di stile usano invece la palette dinamica descritta sotto. Sfondo, barre e cuore cambiano insieme. Violet e Night conservano geometria, palette e output legacy identici. La griglia adaptive è 108 unità e il glifo resta nella zona centrale di 66 unità.

```sh
python3 design/icon/build_style_icons.py
python3 design/icon/build_style_icons.py --android-only
python3 design/icon/build_style_icons.py --apply
```

Il primo comando aggiorna solo anteprime SVG e manifest; `--android-only` aggiorna esclusivamente le sei varianti Android dinamiche e il manifest; `--apply` aggiorna i soli percorsi generati esplicitamente ammessi, compresi splash, asset Android, PNG condivise e cataloghi iOS. Non elimina file sconosciuti. Dipendenze già presenti: Python standard e `rsvg-convert`; nessuna installazione aggiuntiva. Il rendering usa SVG in memoria, senza file temporanei.

`generated-manifest.json` identifica ogni output con SHA-256. Due esecuzioni con lo stesso generatore e renderer devono produrre lo stesso manifest; versioni diverse di librsvg possono cambiare la rasterizzazione. Alias Android e build setting Xcode sono configurazione esplicita, non riscritti dal generatore.

Android dispone di otto icone adaptive con la stessa maschera monocromatica. Le sei varianti di stile risiedono in `shared/src/androidMain/res`, così launcher e anteprime Android possono risolvere le medesime risorse. Sfondi, barre e cuore usano riferimenti pubblici `@android:color/system_*` (API minima 33); la tabella esatta è nel manifest generato. I gradienti Liquid mantengono la geometria originale. Violet/Night restano in androidApp con colori fissi e byte invariati. SVG, PNG comuni e cataloghi iOS conservano le palette fallback fisse: non mostrano la palette Android effettiva del dispositivo. La tinta del launcher è una scelta del sistema; questi asset non promettono icone arbitrarie generate a runtime. Solo LauncherViolet è abilitato inizialmente.

iOS conserva AppIcon come primaria Violet e sette set alternativi con PNG RGB opache 1024×1024, nelle versioni normal/dark/tinted. Le famiglie Light usano la corrispondente Dark nell’apparenza scura; le famiglie Dark restano scure. I nomi alternativi sono registrati in Xcode; il sistema genera CFBundleIcons, senza modifiche manuali al plist. Questi cataloghi PNG non sono file stratificati Icon Composer e non ne simulano il formato. La selezione nativa e l’esecuzione iOS richiedono verifiche separate.

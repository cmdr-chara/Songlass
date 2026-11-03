# Songlass website

An editorial presentation of Songlass built around its music, three visual interfaces, and original Android screenshots. Large typography, landscape artwork, a floating phone gallery, and a playable edition showcase introduce the app. This remains a development preview, separate from the Kotlin/Compose app and its releases.

Liquid Glass, Material 3 Expressive, and Miuix share the same page content. The interface selectors update the palette, gallery composition, screenshots, and live player controls together. The website also offers English/Italian content, browser-language selection, system/light/dark themes, reduced motion, and opaque surfaces. These controls change the website only. They do not alter an installed app. Original captures retain their embedded “Material Glass” label.

## Implementation

The site uses React 19, TypeScript, Vite 8, GSAP and Lenis. On fine-pointer devices, Lenis interpolates wheel scrolling through GSAP’s clock and keeps ScrollTrigger synchronized. Touch and reduced-motion scrolling remain native. One scroll bridge coordinates anchors and reading-position restoration; screenshot dialogs suspend interpolation. Three.js, React Three Fiber and the former Material/glass presentation packages are not dependencies. The native scrollbar changes color and shape with the selected interface.

The hero places three original app captures over a project-owned landscape. Native CSS transforms give Liquid Glass a fanned composition, Material a stepped arrangement, and Miuix a quieter parallel arrangement. A separate transform layer follows scroll progress without restarting an edition change. This requires no WebGL. Reduced motion retains the composition and disables movement; opaque surfaces remove the live player's glass treatment.

Content stays in normal document flow. The build pre-renders the root, English, and Italian pages, so text, screenshot links, download links, and native FAQ disclosures remain available without JavaScript. Interactive controls require JavaScript. Keyboard navigation, screenshot-dialog focus return, and reduced-motion handling are part of the presentation.

### Interactive edition player

The showcase is a native-inspired web demo, distinct from the original Android captures linked beneath it. Its independent light/dark selector starts with the dark example; the header theme button changes the whole website. These controls do not modify or recolor the original app captures. Its controls follow the project's native theme implementations:

| Edition | Player treatment |
| --- | --- |
| Liquid Glass | Artwork tint, unboxed transport icons, a fine seek rail, and a bounded glass utility capsule. |
| Material 3 Expressive | A play button that changes corner shape, tonal skip controls, a played-track wave, and connected utility buttons. |
| Miuix | Neutral surfaces, a thicker seek rail, a prominent rounded play button, and separate utility tiles. |

The player supports play/pause, keyboard-accessible seeking, three-second jumps, a local favorite toggle, and a one-track queue disclosure. Switching editions preserves its current position, playback, favorite, and queue state while the player remains visible. These are session-local demo controls. The same player component remains mounted; an edition change does not create a new audio engine.

### Sound study and equalizer

Both the edition player and listening lab synthesize the same original twelve-second stereo composition locally with Web Audio. They share the `SoundStudy` engine implementation and playback coordinator, while keeping their own session state. Only one demo owns playback at a time. Sound starts only after pressing **Listen**; loading the page, seeking while paused, or adjusting EQ does not start playback.

First play prepares the samples in a one-shot module worker, with a private cache per sample rate shared by both demos. Each audio context receives its own buffer. Worker creation and synthesis are deferred until an explicit play request. If workers are unavailable or fail, local synthesis remains available after yielding one task; that fallback can still briefly block the interface. Pending preparation never overrides a newer pause, seek, or playback owner.

The listening lab adds three presets—Flat, Warm, and Bright—and three bands—bass, mid, and treble. Reset returns the bands to zero. Playback pauses when the corresponding demo leaves the viewport or the page is hidden. Reduced motion keeps the spectrum still and flattens the Material seek wave while leaving audio controls available.

The study has no backend, fetched audio, microphone access, or user-file input. It does not read a personal music library or change the native app's audio settings. Language, interface, theme, motion, and opacity preferences may be saved in browser storage; blocked storage does not prevent using the page. Normal site assets are served with the website, and project/download links lead to GitHub.

## Local development

Use Node.js 24 or later and npm. From the repository root:

```bash
cd website
npm ci
npm run dev
```

Vite prints the local address. To build and serve the production output:

```bash
npm run build
npm run preview
```

## Checks

Run the unit tests and lint, then build before checking generated pages:

```bash
npm test
npm run lint
npm run build
npm run check
```

### Browser smoke test

Build the site, start `npm run preview` in one terminal, and run the following in another:

```bash
npx playwright-core install chromium
npm run test:browser
```

The script uses Playwright's Chromium and defaults to `http://127.0.0.1:4173/`. If Chromium is already installed, set `CHROMIUM_PATH` to its compatible executable instead of downloading it. Set `SONGLASS_REVIEW_URL` to the deployment root when testing a different address or path; include `/Songlass/` for a prefixed build:

```bash
SONGLASS_REVIEW_URL=http://127.0.0.1:4173/Songlass/ npm run test:browser
```

The smoke script covers responsive EN/IT layouts, synchronized interface selections, gallery and reference assets, smooth scrolling with native touch/reduced-motion fallbacks, keyboard controls, dialog focus, saved appearance preferences, edition-player state preservation, opt-in audio, EQ, deployment prefixes, and pre-rendered content without JavaScript. It observes real Web Audio contexts rather than replacing the audio engine with a mock.

## GitHub Pages

[Website workflow](../.github/workflows/website.yml) builds this directory and publishes only `dist/` from `main`. Pull requests run checks without deploying. The configured project URL is <https://cmdr-chara.github.io/Songlass/>; availability depends on a successful Pages deployment. Local builds and review packages do not publish the site.

`SONGLASS_BASE` selects the deployment prefix; it defaults to `/` for local development. The workflow uses `/Songlass/`. To reproduce that build locally:

```bash
SONGLASS_BASE=/Songlass/ npm run build
SONGLASS_BASE=/Songlass/ npm run check
SONGLASS_BASE=/Songlass/ npm run preview
```

Runtime assets and locale routes use `sitePath` to preserve this prefix. Serve `dist/` over HTTP for production review rather than opening its HTML files directly.

### Search-indexing gate

The existing development gate is intentionally retained: generated pages contain `noindex,nofollow`, and `npm run check` requires it. A publisher can remove the gate before a public launch, updating both the HTML metadata and its matching assertion. The redesign does not imply that the site has been published or opened to search indexing.

## Typography

Self-hosted Google Sans Flex in WOFF2 provides headings, reading text, controls, and the footer signature. Other existing font files and their supplied terms remain in the project; preserving an asset does not mean it is loaded by the page.

## Assets and license

Original Songlass code and assets follow the repository's [Songlass Source-Available License 1.0](../LICENSE), with context in [LICENSING.md](../LICENSING.md). Source-available does not mean OSI open source. Bundled fonts retain their supplied usage terms or OFL notices next to their files.

The six app captures are original project screenshots using synthetic demo audio and project-owned artwork. The two landscapes are generated illustrations of imaginary places; the hero, showcase, and page sections reuse optimized WebP versions of those project assets. The web sound study and preview lyric lines are original demo material, not a user's music or third-party songs. Preserve the existing notices when preparing a contribution or review build.

## Italiano

Sito di presentazione riprogettato, ancora in anteprima di sviluppo e separato dall’app. La galleria unisce paesaggi del progetto, schermate originali e trasformazioni CSS. Il player web cambia geometrie e controlli tra Liquid Glass, Material 3 Expressive e Miuix mantenendo lo stato durante il cambio di stile. Il player e il laboratorio EQ condividono una composizione locale di dodici secondi e il coordinamento audio, senza avvio automatico. Lingua, tema, movimento ridotto e superfici opache riguardano solo il sito. I comandi sopra avviano e verificano la versione locale e la build con prefisso `/Songlass/`; lo script browser verifica le interazioni reali con Chromium. Il blocco `noindex,nofollow` resta attivo fino alla decisione di pubblicazione. Codice, immagini e font mantengono le rispettive condizioni d’uso.

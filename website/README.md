# Songlass website — WIP

Work-in-progress promotional website for Songlass. This is a development preview, not an app release. It is independent of the Kotlin/Compose build.

The site presents Liquid Glass, Material 3 Expressive and Miuix, with English/Italian content, browser-language selection and system light/dark preferences. The web style selector changes the presentation of this website only. Original app captures retain their embedded “Material Glass” label.

## Local development

Use Node.js 24 or later and npm.

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

```bash
npm test
npm run lint
npm run check
```

Run `check` after `build`. For browser verification, start the preview server and run `npm run test:browser`. It uses Playwright's Chromium; install it with `npx playwright-core install chromium`, or set `CHROMIUM_PATH` to an existing compatible executable. Set `SONGLASS_REVIEW_URL` when testing another address.

## GitHub Pages

[Website workflow](../.github/workflows/website.yml) builds this directory and publishes only `dist/` from `main`. Pull requests run checks without deploying. The preview is available at <https://cmdr-chara.github.io/Songlass/> after a successful Pages deployment.

`SONGLASS_BASE` selects the deployment prefix; it defaults to `/` for local development. The workflow uses `/Songlass/`. To reproduce that build locally:

```bash
SONGLASS_BASE=/Songlass/ npm run build
SONGLASS_BASE=/Songlass/ npm run check
SONGLASS_BASE=/Songlass/ npm run preview
```

The generated root, English and Italian pages work without JavaScript. The WIP preview requests no indexing. Device/WebGL performance, Safari and final publishing polish remain review work.

## Typography

Rovel Sans provides reading text and controls in weights 400, 500 and 700. Liquid Glass also uses it for headings; Material 3 Expressive uses Puffrelle, and Miuix uses Kindredge. The static display faces use their supplied 400 weight without synthesized bold or italic. Usage terms are preserved beside the webfont files.

## Assets and license

Original Songlass code and assets follow the repository [license](../LICENSE). Bundled fonts retain their supplied usage terms or OFL notices next to their files. The six app captures use synthetic demo audio and project-owned artwork. The two footer landscapes are generated illustrations of imaginary places.

## Italiano

Sito di presentazione **WIP**, separato dall’app. I comandi sopra avviano e verificano la versione locale; il workflow pubblica l’anteprima su GitHub Pages. I tre stili, le lingue e il tema di sistema riguardano la presentazione web. Le illustrazioni del footer sono generate; le schermate dell’app sono quelle originali del progetto.

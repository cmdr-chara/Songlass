# Working on the website

This directory is a WIP React/Vite promotional site, separate from the native app. Follow the repository's contribution and licensing rules. Preserve the original logo, screenshots and font licenses.

- Keep English and Italian copy coordinated through `src/en.json` and `src/i18n.tsx`.
- Resolve runtime assets and language routes with `sitePath`; production runs under `/Songlass/` on GitHub Pages.
- Preserve native radio semantics, focus and reading position during style changes. Keep reduced-motion and normal-flow fallbacks readable.
- Run `npm test`, `npm run lint`, `npm run build`, then `npm run check`. Repeat build/check with `SONGLASS_BASE=/Songlass/` when changing deployment paths.
- Do not commit installed dependencies, build output, local recordings, design references or browser reports.

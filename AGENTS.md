# Working on Songlass

Read [CLAUDE.md](CLAUDE.md) for the architecture, current decisions, build commands and device rules before editing.
Read [CONTRIBUTING.md](CONTRIBUTING.md) and [LICENSING.md](LICENSING.md) for contribution and licensing terms.
Use [docs/ROADMAP.md](docs/ROADMAP.md) for planned work; do not turn historical verification notes into current guarantees.

## Keep changes bounded

- Inspect the branch and working tree first. Preserve unrelated local changes and other contributors' work.
- Make the smallest coherent change for the requested outcome. Do not add dependencies or change platform/toolchain versions incidentally.
- Preserve unrelated local work and follow the existing ownership and publication boundaries.
- Repository visibility, releases and pushing require the owner's request. Do not infer that authority from a previous task.

## Follow the app boundaries

- Keep domain logic in `core`, shared screen composition in `shared`, platform integration in its platform source set, and visual primitives in the theme modules.
- Support Liquid Glass, Material Glass and Miuix through the shared design-system contracts. Preserve each style's identity; do not branch on style in shared screens.
- Keep user-facing strings in the English and Italian resources. Write code and comments in English.
- Preserve playback ownership, coroutine lifetimes, navigation state and the player artwork transition contracts.
- Original Songlass material is source-available under Songlass Source-Available License 1.0. Keep third-party licenses and excluded private material separate.

## Verify and report accurately

- Run checks appropriate to the affected code, using the established commands in [CLAUDE.md](CLAUDE.md#verifica).
- Check revised UI in the actual app, including affected styles and large text. Supply the before/after screenshots required by [CONTRIBUTING.md](CONTRIBUTING.md#ui-design-evidence).
- Distinguish host tests, APK compilation, emulator behavior, Apple compilation and physical-device results. A successful compile does not prove native runtime behavior.
- Pin ADB commands to the intended device and follow the device-specific input rules in `CLAUDE.md`. Do not change the owner's device setup as an incidental test step.
- Disclose AI assistance in submitted work and PR/issue text as described in [CONTRIBUTING.md](CONTRIBUTING.md#ai-assistance). The author remains responsible for review and validation.

Respect the [Code of Conduct](CODE_OF_CONDUCT.md). Do not include credentials, signing material, personal music or unlicensed third-party assets in patches, screenshots or reports.

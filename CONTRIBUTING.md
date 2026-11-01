# Contributing to Songlass

Bug reports, translations, accessibility improvements, tests, documentation and code contributions are welcome.

## Permission to work on contributions

Songlass uses **Songlass Source-Available License 1.0**. Read [LICENSE](LICENSE) and [LICENSING.md](LICENSING.md) before contributing. This is source-available software, not open-source software under the OSI definition.

Section 3 of the license explicitly allows working forks, local changes, compilation, debugging, proposed patches, pull requests, CI artifacts and clearly identified review/test builds for the Songlass contribution workflow. Outside this contribution workflow, redistribution or publishing a separate app, whether renamed, free or paid, requires prior written permission. Paid development or review work on contributions is welcome.

Do not present a test build or fork as an official release. Preserve notices and respect the separate licenses of dependencies and assets. Do not publish private music, screenshots or credentials from a private checkout.

## Terms for submitted contributions

By intentionally submitting a contribution for inclusion in Songlass, you offer the original material you have the right to license under **Songlass Source-Available License 1.0 in LICENSE**, unless the maintainers accept a separate written agreement. Confirm that you have the right to make that offer. Keep any third-party code under its original license and identify its source and notices so compatibility can be reviewed before inclusion.

You retain copyright in your contribution. Submission does not assign ownership to the maintainer or grant an unrestricted right to relicense your work. Any broader license or assignment requires a separate explicit agreement. No contributor license agreement or DCO sign-off is required by this project at this time.

## AI assistance

AI assistance is allowed and **must be disclosed** when used to prepare a contribution, including code, tests, documentation, design work, or PR/issue text. In the template, select Yes or No; if Yes, briefly describe which parts it helped with and how you reviewed or validated them. Naming tools or models is optional. Do not paste private prompts, conversations or sensitive data.

You remain responsible for the complete submission: understand the change, check its behavior and confirm that you have the rights to contribute it. Review generated suggestions rather than treating a tool's output as verification. Apply the same disclosure rule to human- and agent-submitted PRs.

AI use is not a reason to dismiss a contribution; review its quality and evidence. Follow the [Code of Conduct](CODE_OF_CONDUCT.md).

## UI design evidence

PRs that change UI design **must include before and after screenshots from the actual app**. Include a pair for each affected style, using comparable content, device/emulator viewport, theme and text size. State that context beside the images. Identify mockups separately; they do not replace native screenshots.

For layout or readability changes include a 200% text case. For color or contrast changes include the affected light/dark cases. Motion recordings can supplement screenshots. Check Liquid Glass, Material Glass and Miuix when a shared component changes; explain any style that is unaffected.

Use media and fixtures you may share, and redact personal information. Do not attach private music, third-party assets, credentials or full backups. Report visual checks and interactions you actually performed, including limitations.

## Development workflow

1. Read [CLAUDE.md](CLAUDE.md) and the [roadmap](docs/ROADMAP.md). Preserve existing local work.
2. Use a working branch/fork, keep changes focused and explain the user-visible result in your pull request.
3. New or revised UI must fit Liquid Glass, Material Glass and Miuix. Keep text in the Italian and English resources and respect large text and accessibility.
4. Use the existing dependencies and build setup. Discuss new dependencies and broad architecture changes before adding them.
5. Run checks relevant to the change using the [README build instructions](README.md#build-and-checks). Report Android host tests, APK builds, emulator checks and Apple compilation/runtime separately.

Never commit credentials, signing keys, personal music libraries or unlicensed third-party material. Repository visibility, publishing and releases are maintainer decisions, not part of submitting a contribution.

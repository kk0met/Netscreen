# Contributing to NetScreen

Thanks for your interest! Bug reports, translations and pull requests are welcome.

## Reporting bugs
Open an [issue](../../issues/new/choose) with the bug template and attach `logs/latest.log`.
Please include your Minecraft, NeoForge and NetScreen versions.

## Building from source
Requirements: JDK 21.

```bash
./gradlew build          # Windows: gradlew.bat build
```

The mod jar is written to `build/libs/`. To start a development client:

```bash
./gradlew runClient
```

## Pull requests
- Keep changes focused; one feature or fix per PR.
- Follow the existing code style (`.editorconfig`, 4-space indentation).
- Every user-facing string goes through a translation key in
  `src/main/resources/assets/netscreen/lang/` (at least `en_us.json`).
- Add an entry under **Unreleased** in `CHANGELOG.md`.

## Adding a translation
Copy `en_us.json` to your locale file (for example `de_de.json`), translate the values
and open a pull request.

## Releasing (maintainers)
1. Update `mod_version` in `gradle.properties` and move the changelog entries under a new version.
2. Commit, then push a tag: `git tag v1.1.0 && git push origin v1.1.0`.
3. The **Release** workflow builds the jar and publishes a GitHub Release with it attached.

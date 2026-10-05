# NetScreen

[![Build](https://github.com/kk0met/netphone/actions/workflows/build.yml/badge.svg)](https://github.com/kk0met/netphone/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
![Minecraft 1.21.1](https://img.shields.io/badge/Minecraft-1.21.1-62B47A)
![NeoForge](https://img.shields.io/badge/loader-NeoForge-E68C37)
![Client side](https://img.shields.io/badge/side-client-lightgrey)
![Windows](https://img.shields.io/badge/platform-Windows-0078D6)

**Mirror any desktop window onto a small screen in your Minecraft HUD.**
Doom-scroll while you mine: keep an eye on a video, a guide, a stream chat or a timer
without alt-tabbing out of the game. Pretty dystopian, but neat.

<!-- Add a screenshot here: ![NetScreen in game](docs/screenshot.png) -->

## Features

- **Live window mirroring** at ~30 FPS, drawn in the top-right corner of the HUD.
- **Window picker**: choose any open window from an in-game list.
- **Remembers your choice**: the next time you press the key, it reattaches to the same window.
- **Three sizes** (small, medium, large) and **four corners**.
- **Media key**: optional key binding that sends Play/Pause to the system.
- **Works behind the game**: browsers keep rendering even when Minecraft covers them.
- **Lightweight**: capture runs on its own thread, resolution matches what is on screen,
  and it stops completely while the screen is hidden.
- **Client-side only**: works in singleplayer and on any server. Nothing to install server-side.
- English and Italian translations.

## Requirements

| | |
|---|---|
| Minecraft | 1.21.1 |
| Loader | NeoForge 21.1.x |
| OS | Windows 10 / 11 |
| Side | Client only |

## Installation

1. Download the latest `netscreen-1.21.1-x.y.z.jar` from [Releases](../../releases).
2. Put it in your instance's `mods` folder.
3. Start the game.

## Usage

| Key (default) | Action |
|---|---|
| `Y` | Show / hide the screen |
| `U` | Choose which window to show |
| `I` | Change size |
| *unbound* | Move to the next corner |
| *unbound* | Media Play/Pause |

All keys can be changed in **Options → Controls → Key Binds → NetScreen**.
Other settings are in **Mods → NetScreen → Config** (or `config/netscreen-client.toml`).

Sound is not captured: audio keeps playing from the original application.

## Troubleshooting

**The screen stays black.** Some applications render with the GPU in a way that window
capture cannot read, and windows showing protected content are always black by design.
Turning off hardware acceleration in that application often helps for ordinary content.

**The image goes black or freezes when the game covers the window.** Chromium-based browsers
stop drawing windows that are completely hidden. NetScreen prevents this automatically
(option *Keep covered window rendering*, on by default). If you use another app that does the
same, leave a small part of its window visible or move it to a second monitor.

**"Window is minimized".** Minimized windows are not drawn by Windows. Restore the window
and leave it behind the game instead.

**Nothing happens on macOS/Linux.** NetScreen uses the Windows GDI API and only works on Windows.

## How it works

NetScreen calls the Win32 `PrintWindow` API through JNA (already bundled with Minecraft)
on a background thread, scales the image down with GDI to the size actually shown on screen,
and uploads it to a dynamic texture drawn on a NeoForge GUI layer. No native binaries are
shipped and nothing is sent over the network.

NetScreen does not bypass DRM or any other content protection.

## Building from source

```bash
git clone https://github.com/kk0met/netphone.git
cd netphone
./gradlew build        # Windows: gradlew.bat build
```

The jar is written to `build/libs/`. See [CONTRIBUTING.md](CONTRIBUTING.md) for details.

## Contributing

Bug reports, translations and pull requests are welcome. Please read
[CONTRIBUTING.md](CONTRIBUTING.md) first.

## License

[MIT](LICENSE) © kk0met

---

### In italiano

NetScreen mostra in tempo reale una finestra di Windows in un piccolo schermo in alto a destra
nell'HUD di Minecraft. Funziona solo lato client, solo su Windows, con NeoForge 1.21.1.
Tasti predefiniti: **Y** mostra/nasconde, **U** sceglie la finestra, **I** cambia dimensione.

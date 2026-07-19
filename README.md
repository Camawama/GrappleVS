# GrappleVS — Grappling Hook Mod with Valkyrien Skies Compatibility

A fork of [yyon's Grappling Hook Mod](https://github.com/yyon/grapplemod) for **Forge 1.20.1** with
compatibility for **[Valkyrien Skies 2](https://valkyrienskies.org/)** (2.4.x).

Grappling hooks can attach to VS ships, track them as they move and rotate, wrap their rope around
ship hulls, and pay out rope when a hooked ship pulls away.

## Requirements

- Minecraft 1.20.1, Forge 47+
- [Cloth Config API (Forge)](https://www.curseforge.com/minecraft/mc-mods/cloth-config-forge) — required
- [Valkyrien Skies 2](https://modrinth.com/mod/valkyrien-skies) 2.4.x — **optional**; everything
  works without it, ship integration activates when it's installed (VS itself requires Kotlin for Forge)

## Valkyrien Skies integration notes

- Hook attachment positions and rope bend points on ships are stored in ship-local coordinates and
  re-projected every tick, so ropes follow moving/rotating ships.
- If a hooked ship moves away, rope is paid out up to the hook's max rope length before the rope snaps.
- If the ship a hook is attached to unloads or is deleted, the hook detaches after a few seconds.
- Known limitation: rope-bend *unwrap* math treats bend edges as world-axis-aligned, so ropes
  wrapped around heavily rotated ships may unwrap slightly early or late.

## Setup for Developing

1. Clone this repository
2. `./gradlew build` (standard [Forge development setup](https://docs.minecraftforge.net/en/1.20.1/gettingstarted/))
3. `./gradlew runClient` to test in-game

The manual test checklist lives in the `Testing` file, including a Valkyrien Skies section.

## Upstream project

Original mod by yyon: [CurseForge](https://www.curseforge.com/minecraft/mc-mods/grappling-hook-mod) ·
[GitHub](https://github.com/yyon/grapplemod). Licensed GPL-3.0, as is this fork (see `COPYING`).

## Credits

Fork/VS compatibility by Cama.

Upstream credits:

- 1.18 update by Nyfaria
- Textures by Mayesnake
- Bug fixes: Random832, LachimHeigrim
- Languages: Blueberryy (Russian), Neerwan (French), Eufranio (Brazilian Portuguese)
- Sound effects: Iwan Gabovitch (double jump, CC0), Outroelison (ender staff, CC0)
- Bug finding: Shivaxi

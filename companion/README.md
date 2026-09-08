# Three Hour Tour Companion

The NeoForge mod that [Three Hour Tour](https://github.com/Mousebeast/three-hour-tour)
is built around — a Minecraft 1.21.1 modpack about living on a ship in an ocean
with no land in it.

It is a small mod with a specific job. It is published on its own because a
modpack may not carry a mod jar of its own inside it: every mod a pack installs
has to be a project the launcher can fetch by name. This is that project.

## What it does

**The ship core.** The block that makes a vessel *yours* — it carries the ship's
identity, and it deliberately has no item form, because a core that can enter an
inventory is a ship identity somebody can steal. It can still be relocated, by a
gesture the block itself explains.

**The on-ship predicate.** The question "is this player actually aboard their
ship right now?", answered once and consistently, so the rest of the pack can
ask it.

**The research gate.** Placement is gated on what you have researched, so the
progression tree describes what your ship is allowed to *be* rather than only
what your crafting grid will accept.

## Running it

Built for **Minecraft 1.21.1** on **NeoForge 21.1.248** or later.

| Mod | |
|---|---|
| Sable | required |
| GuideME | required |
| KubeJS | optional — used when present |
| FTB Teams | optional — used when present |

Inside the pack all four are already installed. On its own, the two required
ones have to come with it.

Fair warning: outside the pack this mod is not much use on its own. It has no
recipes of its own to speak of and expects the pack's datapacks to give its
research gate something to gate.

## Building

    ./gradlew build

The jar lands in `build/libs/`.

It compiles against a few mods that no Maven repository serves. `gradle.properties`
names them and says where the build expects to find them; point `staged_mods_dir`
at any directory holding that set — a launcher instance's `mods/` directory will
do.

    ./gradlew test

## Licence

MIT — see [LICENSE](LICENSE).

The mods it depends on are their authors' work and carry their own terms.
Nothing here grants you any rights to them.

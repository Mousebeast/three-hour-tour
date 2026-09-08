# Three Hour Tour

A Minecraft 1.21.1 / NeoForge modpack about living on a ship in an ocean with
no land in it.

You start on a raft. There is a lever. Pulling it turns the raft into a real
physics vessel and binds it to you, and from then on that ship is your home,
your workshop, your farm and the thing your progress is attached to. Islands
exist — a median one is about 27 blocks across — but you visit them for
materials. They are never a second base.

## What is actually different about it

**The ocean is the world, not a gap between places.** Worldgen is the pack's
own. The origin is open water with a seabed 63 blocks down, and there is no
spawn island: the first thing you see is the horizon in every direction.

**Ships are physics, not decoration.** Vessels float, move under power and
carry their contents, and two players can sail to meet each other.

**Progress is bought, not levelled into.** Research is paid for with six kinds
of research pack, each earned a different way, so no single activity buys the
whole tree:

| Pack | Earned by |
|---|---|
| Trawl | the sea scoop while sailing, and line fishing |
| Foundry | the Create chain's own output — alloys, ingots |
| Farm | growing and cooking |
| Abyssal | hand-diving, which cannot be automated at all |
| Arcane | spellcraft and enchanting |
| Husbandry | animals — breeding, cheese, leather |

**Research gates placement, not just recipes.** A block you have not researched
cannot be placed, so the tree describes what your ship is allowed to *be* rather
than what your crafting grid will accept.

**Thirst never comes back from food.** Cooking buys you time; it does not buy
you water. Fresh water is a standing problem on a boat surrounded by the wrong
kind of it, and solving it properly is early progression rather than a footnote.

You also get a dog.

## Installing it

**[Three Hour Tour on CurseForge](https://www.curseforge.com/minecraft/modpacks/three-hour-tour)** — install it from the CurseForge app
and everything below is handled for you.

Failing that, take the zip from
[the latest release](https://github.com/Mousebeast/three-hour-tour/releases/latest)
and import it with the CurseForge or Prism launcher. It resolves the mod list
on import — there is nothing to place by hand.

Allow it around **9 GB** of RAM.

## What is in this repository

| Path | What it is |
|---|---|
| `pack/manifest.json` | the mod list, pinned by project and file id |
| `pack/overrides/` | every config, datapack, script and asset the pack ships |
| `companion/` | the pack's own NeoForge mod, with its tests |

Mod jars are never committed here. The manifest names them and the launcher
fetches them, which is both a licensing matter and the only way the twelve mods
that forbid third-party API downloads can be installed at all.

## The companion mod

`companion/` is the pack's own NeoForge mod — the ship core, the on-ship
predicate and the research gate that decides what you are allowed to place. It
is published as its own project, at
[three-hour-tour-companion](https://github.com/Mousebeast/three-hour-tour-companion),
so the pack can name it the way it names every other mod rather than smuggling
a jar into the overrides. The copy here is the source it is generated from.

It needs **Sable** and **GuideME** at runtime, and uses **KubeJS** and **FTB
Teams** when they are present. In the pack all four are already there; if you
install the mod on its own, the two required ones have to come with it.

Building it needs a few jars the pack depends on but no Maven repository
serves. `companion/gradle.properties` says which ones and where it expects to
find them; point `staged_mods_dir` at any directory holding that set.

## Building the pack

The pack is assembled by tooling that is not published here — it is written
against one server's layout and would only mislead anyone else. The artifact it
produces is, though: every release carries the importable zip, built from the
commit it is tagged against.

## Licence

MIT — see [LICENSE](LICENSE).

**What that covers:** the companion mod's source, and the pack's own
configuration, datapacks, scripts, generated data and written text. Everything
in this repository, in other words.

**What it does not cover:** the mods the manifest points at. Those are their
authors' work, they are downloaded by your launcher rather than distributed
here, and each carries its own terms. Nothing in this repository grants you any
rights to them.

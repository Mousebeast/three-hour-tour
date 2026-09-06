# Manual downloads required

These mods set `allowModDistribution: false` on CurseForge. The API returns no
download URL for them, so **an automated installer cannot fetch them** — they must
be downloaded by hand from the mod page and dropped into the instance's `mods/`.

This is not a tooling limitation to work around. It is the author's licensing
choice, and it is the same constraint that keeps jars out of this repo entirely
(see `lessons-learned.md`, 2026-08-20).

Two of these are core pillars of the pack: Create Aeronautics and Create: Better
High Seas. The pack cannot boot without them.

| Mod | Project | File | Page |
|---|---|---|---|
| Animal Husbandry | 1550564 | 8322440 | https://www.curseforge.com/minecraft/mc-mods/animal-husbandry |
| Create Aeronautics | 676721 | 8240058 | https://www.curseforge.com/minecraft/mc-mods/create-aeronautics |
| Create: Better High Seas | 1629286 | 8616061 | https://www.curseforge.com/minecraft/mc-mods/create-better-high-seas |
| Create: Design n' Decor | 923238 | 8156977 | https://www.curseforge.com/minecraft/mc-mods/create-design-n-decor |
| Create: Sophisticated Backpacks Compat | 1320115 | 6844021 | https://www.curseforge.com/minecraft/mc-mods/create-sophisticated-backpacks-compat |
| Create: Wizardry | 949995 | 8304856 | https://www.curseforge.com/minecraft/mc-mods/create-wizardry |
| Entity Culling Fabric/Forge | 448233 | 8287097 | https://www.curseforge.com/minecraft/mc-mods/entityculling |
| I'm Fast | 1111501 | 5991453 | https://www.curseforge.com/minecraft/mc-mods/im-fast |
| Jump Over Fences | 423421 | 6897510 | https://www.curseforge.com/minecraft/mc-mods/jump-over-fences-forge |
| Library Ferret - NeoForge | 522351 | 6118136 | https://www.curseforge.com/minecraft/mc-mods/library-ferret-neoforge |
| More Overlays Updated | 391382 | 6981252 | https://www.curseforge.com/minecraft/mc-mods/more-overlays-updated |
| Yet Another Thirst | 1563280 | 8563284 | https://www.curseforge.com/minecraft/mc-mods/yet-another-thirst |

**Count: 12 of 257 manifest entries.**

## The easy route: import the client pack

`allowModDistribution: false` blocks **third-party** API downloads. The official
CurseForge client is not third-party — it fetches these 12 itself. So the
practical way to obtain them is:

1. Build the importable pack: `tools/build-clientpack.sh <version>` →
   `pack/build/3ht-<version>.zip` (gitignored).
2. In the CurseForge app: **Create Custom Profile → Import → From File**, pick
   that zip. It resolves all 257 mods, blocked ones included.
3. The client instance's `mods/` folder now holds every jar. Copy the 12 listed
   above into the server's `mods/` directory
   (`/srv/minecraft/3ht/mods/`).

This is also how you get a playable client, so it is not extra work.

**Do not commit the zip or any jar it produces.** `pack/build/` is gitignored
for the same licensing reason this file exists.

Verify after downloading: the filename must match the pinned fileID's file, and
`tools/mod-identity.sh <jar>` should report the expected `modId`.

---

## Continents — no longer a manual download (2026-09-02)

**It is pinned in `pack/manifest.json` like any other mod**, project **682515**,
file **8396863**, `Continents_26.2_v1.1.14.jar`. CurseForge's own API reports
`allowModDistribution: true`, so the client fetches it and this repo
redistributes nothing.

This section used to describe fetching a `.zip` by hand into
`config/paxi/datapacks/`, and a licence that "forbids redistribution outright".
Both were wrong, and between them they made the pack unshippable:

- Stardust Labs publish Continents on CurseForge **as a mod jar**. Its worldgen
  data is byte-identical to the Modrinth `.zip` — verified entry by entry — so
  every measurement taken against the zip still holds.
- The zip route meant a hand install the CurseForge client could not perform,
  which meant single-player worlds simply failed to generate.

**Nothing about it is configured any more either.** `tools/worldgen-tune.py`
used to apply Continents' four sliders by editing values inside the archive.
A slider is a file edit, and an edited copy may be used on our own server but
not distributed — so the tuned world could never have shipped. All tuning now
lives in our own `3ht-worldgen` add-on, which overrides two **vanilla** density
function ids on top of Continents and references it only by id. See
`tools/worldgen-tune.py` and `docs/current/the-world.md`.

The script verifies the staged jar hashes to
`8c2f1a5483b453b5cdb341da3aa36670d402336d` and refuses to run otherwise, so a
hand-edited copy cannot quietly come back.

---

## Awesome Dungeon Ocean edition — Neoforge (added 2026-08-21)

CurseForge `allowModDistribution=false`, so `tools/download-mods.sh` logs it to
`pack/build/download-blocked.log` rather than fetching it. Pinned in
`pack/manifest.json` as projectID 533528 / fileID 7033605 so the CurseForge
client resolves it for players; the dedicated server needs it placed by hand.

- Source: https://www.curseforge.com/minecraft/mc-mods/awesome-dungeon-edition-ocean-neoforge
- Pinned build: `awesomedungeonocean-neoforge-1.21.1-3.3.0.jar`
- sha1: `e89f1bc62c974ba296d8b99ace0362c7a9a2a196`
- Requires **Library Ferret** (projectID 522351), already pinned in the manifest.

Worldgen: adds ocean dungeons to **newly generated chunks only**. Existing
explored ocean is unaffected — see the pending-world-regen note in `plan.md`.

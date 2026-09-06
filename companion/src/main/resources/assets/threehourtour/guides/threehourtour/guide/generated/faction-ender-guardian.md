---
navigation:
  title: The Ender Guardian
  parent: threehourtour:gates.md
  position: 14
item_ids:
  - threehourtour:trophy_ender_dragon
  - threehourtour:trophy_ender_guardian
  - threehourtour:void_sigil
---

# The Ender Guardian

## Opening a gate

Craft a gate pearl, drop it on open ground, and an arena forms. Survive the waves and the gate pays out.

- **I** — a small arena, 3 waves, pays **4 ×** <ItemLink id="threehourtour:ash" />. Costs 4 × <ItemLink id="minecraft:kelp" />, 2 × <ItemLink id="minecraft:prismarine_shard" />, 2 × <ItemLink id="minecraft:string" />, 1 × <ItemLink id="threehourtour:void_sigil" />.
- **II** — a medium arena, 5 waves, pays **10 ×** <ItemLink id="threehourtour:ash" />. Costs 2 × <ItemLink id="create:iron_sheet" />, 4 × <ItemLink id="minecraft:kelp" />, 2 × <ItemLink id="minecraft:prismarine_shard" />, 1 × <ItemLink id="threehourtour:void_sigil" />.
- **III** — a large arena, 7 waves, pays **24 ×** <ItemLink id="threehourtour:ash" />. Costs 2 × <ItemLink id="create:iron_sheet" />, 2 × <ItemLink id="minecraft:kelp" />, 2 × <ItemLink id="minecraft:prismarine_shard" />, 2 × <ItemLink id="threehourtour:ash" />, 1 × <ItemLink id="threehourtour:void_sigil" />.

The centre of all three recipes is <ItemLink id="threehourtour:void_sigil" />, and that is the part that makes the pearl *this* faction rather than another. It drops from **The Ancient Remnant**'s gates, so that faction has to fall first.

## Who turns up

- Shulker
- Ender Golem
- Endermaptera

## What clearing it is worth

Beyond the ash, the gate pays <ItemLink id="minecraft:end_stone" />.

## Its bosses

- **Ender Guardian**, behind its own Named Gate pearl. Drops <ItemLink id="threehourtour:trophy_ender_guardian" />, which crafts into <ItemLink id="threehourtour:seal_gamma" />.
- **Ender Dragon**, behind its own Named Gate pearl. Drops <ItemLink id="threehourtour:trophy_ender_dragon" />, which crafts into <ItemLink id="threehourtour:seal_delta" />.

## Farming it without fighting it

Killing an **Ender Golem** teaches a data model. Once it knows enough, a simulation chamber can run the fight for you.

The same model learns from **Endermaptera**.

A running simulation draws **512 FE/t**, and every one of them returns an Overworld Prediction. Whether you also get a Mob Prediction is a roll against how well the model has learned:

| The model is | After this many kills | Chance of a prediction |
|---|---|---|
| Faulty | 0 | 0% |
| Basic | 6 | 5% |
| Advanced | 54 | 22% |
| Superior | 354 | 65% |
| Self-aware | 1254 | 100% |

Run that prediction through a loot fabricator and it pays:

- **8 ×** <ItemLink id="minecraft:end_stone" />
- **4 ×** <ItemLink id="minecraft:ender_pearl" />

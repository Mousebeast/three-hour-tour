---
navigation:
  title: The Mage Covenant
  parent: threehourtour:gates.md
  position: 11
item_ids:
  - threehourtour:trophy_dead_king
  - threehourtour:trophy_fire_boss
---

# The Mage Covenant

## Opening a gate

Craft a gate pearl, drop it on open ground, and an arena forms. Survive the waves and the gate pays out.

- **I** — a small arena, 3 waves, pays **4 ×** <ItemLink id="threehourtour:ash" />. Costs 1 × <ItemLink id="minecraft:glow_ink_sac" />, 4 × <ItemLink id="minecraft:kelp" />, 2 × <ItemLink id="minecraft:prismarine_shard" />, 2 × <ItemLink id="minecraft:string" />.
- **II** — a medium arena, 5 waves, pays **10 ×** <ItemLink id="threehourtour:ash" />. Costs 2 × <ItemLink id="create:iron_sheet" />, 1 × <ItemLink id="minecraft:glow_ink_sac" />, 4 × <ItemLink id="minecraft:kelp" />, 2 × <ItemLink id="minecraft:prismarine_shard" />.
- **III** — a large arena, 7 waves, pays **24 ×** <ItemLink id="threehourtour:ash" />. Costs 2 × <ItemLink id="create:iron_sheet" />, 1 × <ItemLink id="minecraft:glow_ink_sac" />, 2 × <ItemLink id="minecraft:kelp" />, 2 × <ItemLink id="minecraft:prismarine_shard" />, 2 × <ItemLink id="threehourtour:ash" />.

The centre of all three recipes is <ItemLink id="minecraft:glow_ink_sac" />, and that is the part that makes the pearl *this* faction rather than another. You will have to go and find one.

## Who turns up

- Necromancer
- Cultist
- Pyromancer
- Cryomancer
- Apothecarist
- Archevoker
- Priest

## What clearing it is worth

Beyond the ash, the gate pays <ItemLink id="irons_spellbooks:arcane_essence" />.

## Its bosses

- **Dead King**, behind its own Named Gate pearl. Drops <ItemLink id="threehourtour:trophy_dead_king" />, which crafts into <ItemLink id="threehourtour:seal_beta" />.
- **Echo of Tyros**, behind its own Named Gate pearl. Drops <ItemLink id="threehourtour:trophy_fire_boss" />, which crafts into <ItemLink id="threehourtour:seal_delta" />.

## Farming it without fighting it

Killing an **Archevoker** teaches a data model. Once it knows enough, a simulation chamber can run the fight for you.

The same model learns from **Necromancer**, **Cultist**, **Pyromancer**, **Cryomancer**, **Apothecarist**, **Priest**.

A running simulation draws **512 FE/t**, and every one of them returns an Overworld Prediction. Whether you also get a Mob Prediction is a roll against how well the model has learned:

| The model is | After this many kills | Chance of a prediction |
|---|---|---|
| Faulty | 0 | 0% |
| Basic | 6 | 5% |
| Advanced | 54 | 22% |
| Superior | 354 | 65% |
| Self-aware | 1254 | 100% |

Run that prediction through a loot fabricator and it pays:

- **16 ×** <ItemLink id="irons_spellbooks:arcane_essence" />
- **8 ×** <ItemLink id="irons_spellbooks:common_ink" />

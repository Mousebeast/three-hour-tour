---
navigation:
  title: Maledictus
  parent: threehourtour:gates.md
  position: 13
item_ids:
  - threehourtour:trophy_maledictus
---

# Maledictus

## Opening a gate

Craft a gate pearl, drop it on open ground, and an arena forms. Survive the waves and the gate pays out.

- **I** — a small arena, 3 waves, pays **4 ×** <ItemLink id="threehourtour:ash" />. Costs 4 × <ItemLink id="minecraft:kelp" />, 1 × <ItemLink id="minecraft:packed_ice" />, 2 × <ItemLink id="minecraft:prismarine_shard" />, 2 × <ItemLink id="minecraft:string" />.
- **II** — a medium arena, 5 waves, pays **10 ×** <ItemLink id="threehourtour:ash" />. Costs 2 × <ItemLink id="create:iron_sheet" />, 4 × <ItemLink id="minecraft:kelp" />, 1 × <ItemLink id="minecraft:packed_ice" />, 2 × <ItemLink id="minecraft:prismarine_shard" />.
- **III** — a large arena, 7 waves, pays **24 ×** <ItemLink id="threehourtour:ash" />. Costs 2 × <ItemLink id="create:iron_sheet" />, 2 × <ItemLink id="minecraft:kelp" />, 1 × <ItemLink id="minecraft:packed_ice" />, 2 × <ItemLink id="minecraft:prismarine_shard" />, 2 × <ItemLink id="threehourtour:ash" />.

The centre of all three recipes is <ItemLink id="minecraft:packed_ice" />, and that is the part that makes the pearl *this* faction rather than another. You will have to go and find one.

## Who turns up

- Draugr
- Royal Draugr
- Elite Draugr
- Aptrgangr

## Its boss

- **Maledictus**, behind its own Named Gate pearl. Drops <ItemLink id="threehourtour:trophy_maledictus" />, which crafts into <ItemLink id="threehourtour:seal_beta" />.

## Farming it without fighting it

Killing a **Draugr** teaches a data model. Once it knows enough, a simulation chamber can run the fight for you.

The same model learns from **Royal Draugr**, **Elite Draugr**, **Aptrgangr**.

A running simulation draws **256 FE/t**, and every one of them returns an Overworld Prediction. Whether you also get a Mob Prediction is a roll against how well the model has learned:

| The model is | After this many kills | Chance of a prediction |
|---|---|---|
| Faulty | 0 | 0% |
| Basic | 6 | 5% |
| Advanced | 54 | 22% |
| Superior | 354 | 65% |
| Self-aware | 1254 | 100% |

Run that prediction through a loot fabricator and it pays:

- **24 ×** <ItemLink id="minecraft:bone" />
- **16 ×** <ItemLink id="minecraft:rotten_flesh" />

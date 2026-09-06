---
navigation:
  title: The sea scoop
  parent: threehourtour:sea.md
  position: 10
item_ids:
  - threehourtour:sea_scoop
  - threehourtour:twine_mesh
  - threehourtour:chain_mesh
  - threehourtour:prismarine_mesh
---

# The sea scoop

<ItemLink id="threehourtour:sea_scoop" /> is a deck block that pulls material
out of the water as you go. Almost everything you own on the first day comes
out of it, and it has one rule that catches everybody:

> **It only works while the ship is moving.**

Sitting at anchor it produces nothing at all. Not slowly — nothing. There is no
message and no progress bar; you simply get no items. If your scoop seems
broken, check that you are actually going somewhere.

## Meshes are the whole story

The scoop holds one **mesh**, and the mesh decides everything: how fast it
yields and how long it lasts. Where you sail changes nothing — there is no
secret rich patch of ocean. What changes your yield is what you have built.

| Mesh | One item every | Total yields | Roughly | Costs |
|---|---|---|---|---|
| <ItemLink id="threehourtour:twine_mesh" /> | 10 seconds | 256 | 43 minutes | 8 string |
| <ItemLink id="threehourtour:chain_mesh" /> | 6 seconds | 1,024 | 102 minutes | about 10 iron |
| <ItemLink id="threehourtour:prismarine_mesh" /> | 3 seconds | 3,072 | 154 minutes | 8 shards and a chain mesh |

Every successful yield wears the mesh a little. When it finally breaks the scoop
just stops, quietly, with no announcement of any kind.

**The twine mesh pays for itself twice over in string.** However thoroughly you
manage to lose everything you own, you can always sail your way back to a
working scoop. Iron nuggets come off the twine tier too, and on day one that is
the only metal there is.

## It needs somewhere to put things

**The scoop empties itself into whatever container is next to it.** A chest, a
barrel, a drawer, a funnel — anything that holds items. You do not open the
scoop and take the catch out; you put a container against it and let it fill.

It will try **any side except the face it is pointing**, and it tries
**underneath first**. A chest directly below the scoop is the arrangement it is
built around, and the one to reach for if you are not sure.

> **A scoop with nowhere to put things produces nothing.**

That is the second reason a scoop looks broken, and it looks exactly like the
first: no message, no items. If the container beside it is full, or there is no
container at all, the scoop simply stops and waits — it does not drop the catch
on the deck and it does not wear the mesh out doing nothing. Empty the chest and
it picks straight back up.

## It only goes down on a ship

The scoop is one of the blocks that **refuses to place anywhere but aboard a
vessel** — see *What goes where*. On a raft you have not assembled yet, it will
not go down at all.

That catches people building a raft out before pulling the lever. Build the
hull, pull the lever, and fit the scoop once the raft is a real vessel.

## Two things worth knowing

**A machine can load a mesh but never take one out.** If you automate mesh
replacement, the worn one is gone. Swap by hand and you get the old mesh back
with its wear intact.

**Swapping is unconditional.** Hand the scoop a worse mesh and it takes it,
silently, and downgrades. Nothing stops you.

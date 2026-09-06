// Day-one propulsion: make the fallbacks the guidebook promises actually exist.
//
// Both docs described a ladder where the hand crank and the wooden propeller
// are available "always", and the andesite propeller arrives "once you have
// andesite". Read against the recipes, that was false in every part:
//
//   create:hand_crank              3 planks + 1 andesite alloy
//   aeronautics:wooden_propeller   shapeless FROM aeronautics:andesite_propeller
//   aeronautics:andesite_propeller wooden slab + create:propeller + create:shaft
//   create:propeller               4 iron plates + 1 andesite alloy
//   create:andesite_alloy          andesite + iron nugget
//
// Andesite needs stone, and stone is the first research node. So every rung of
// that ladder sat behind research, and the wooden propeller was not the bottom
// rung at all -- it was a reskin of the top one, craftable only after it. The
// line under the table, "if your engine goes over the side you are not
// stranded, you are rowing", was therefore untrue on day one: lose your
// propellers before andesite and the run simply stops, because the scoop only
// yields under way.
//
// Nothing gates either item by stage. It was only ever the recipe chain.
//
// This costs nothing in the opening -- the raft starts with two andesite
// propellers already assembled. It is a repair path, not a shortcut.

ServerEvents.recipes(event => {

  // --- the improvised propeller ---------------------------------------------
  // Deliberately the same plus as create:propeller, one tier down in material:
  // four plates around an andesite alloy becomes four slabs around a stick. It
  // reads as the poor cousin of the real thing because it is literally the same
  // pattern in wood, and everything in it comes off the deck or the bonsai pot.
  event.shaped('aeronautics:wooden_propeller', [
    ' S ',
    'SKS',
    ' S '
  ], {
    S: '#minecraft:wooden_slabs',
    K: 'minecraft:stick'
  })

  // The conversion the other way has to go with it. Aeronautics ships a
  // shapeless wooden -> andesite, which existed to let you reskin a propeller
  // you had already earned. With a craftable wooden propeller in front of it,
  // that same recipe becomes four slabs and a stick into an andesite propeller,
  // skipping the press, the alloy and the research entirely.
  //
  // The andesite -> wooden direction is left alone: it is a cosmetic downgrade
  // of something already earned, and it takes nothing out of the tree.
  event.remove({ id: 'aeronautics:andesite_propeller_from_andesite' })

  // --- the hand crank -------------------------------------------------------
  // Same shape, same three planks, iron nugget instead of the andesite alloy.
  // Nuggets come up in the twine mesh from the first minute, so this is the one
  // that makes "you are rowing, not stranded" true.
  // Create files its crafting recipes in subfolders, so the id is not
  // 'create:hand_crank'. A removal aimed at an id nothing ships is a silent
  // no-op -- caught here by tests/test_kubejs_removals.py before it shipped.
  event.remove({ id: 'create:crafting/kinetics/hand_crank' })
  event.shaped('create:hand_crank', [
    'CCC',
    '  A'
  ], {
    C: '#minecraft:planks',
    A: '#c:nuggets/iron'
  })
})

// Pillar enforcement: the ship is where you heal.
//
// Vanilla natural regeneration heals 1 HP every 50 ticks whenever the food bar
// is high enough, anywhere in the world. While that is on, "the ship is your
// home and islands are places you visit" is a story rather than a mechanic:
// nothing about being aboard matters except cargo space, and the companion
// mod's aboard trickle (1 HP every 100 ticks, AboardRegeneration) is invisible
// underneath something twice as fast that runs everywhere.
//
// With it off, healing has three tiers and every one of them already shipped:
//
//   the ship      free, reliable, slow            AboardRegeneration
//   Comfort       portable, consumable, cooked    Farmer's Delight drinks
//   potions       the emergency                   brewing, golden apples
//
// Farmer's Delight's Comfort effect is what makes the middle tier work, and it
// is unaffected by this gamerule: ComfortEffect calls LivingEntity.heal()
// directly rather than going through vanilla's natural-regeneration path.
// Verified in the 1.3.2 jar's bytecode, not assumed -- the whole design rests
// on it. Note it is Comfort and NOT Nourishment: Nourishment stops the food
// bar draining and heals nothing.
//
// It also gives food a better job than healing. Spice of Life: Carrot Edition
// is live (10 hearts at the start, +2 per milestone at 5/10/15/20/25 distinct
// foods, so 20 at the ceiling), so eating widely raises the ceiling while
// cooking produces the portable healing -- and the galley, the botany pots and
// Slice and Dice all feed one survival economy instead of only feeding
// research packs.
//
// **Why this is a script and not a config file.** naturalRegeneration is a
// gamerule, which lives in the world's data and in nothing the pack ships, so
// there is no override file that could carry it to a player who creates a new
// world. Setting it on every load is the only way it reaches an installed
// pack, and it has the useful side effect of restoring the design if somebody
// turns it back on by hand. AboardRegeneration.onServerStarted logs a warning
// if this has not taken effect, because both halves failing quietly would read
// as "the ship bonus does nothing".

ServerEvents.loaded(event => {
  event.server.runCommandSilent('gamerule naturalRegeneration false')
})

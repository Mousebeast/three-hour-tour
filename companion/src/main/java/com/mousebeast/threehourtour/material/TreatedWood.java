package com.mousebeast.threehourtour.material;

/**
 * The treated wood variants and what each weighs.
 *
 * Three blocks, and the number is deliberate. A treated variant exists to be an
 * upgrade from a heavy one; ordinary doors, trapdoors, fences and fence gates
 * are never made heavy (spec 4.2), so a treated one would improve on nothing.
 * Nobody builds a ship out of doors.
 *
 * Masses are spec 4.2. What makes them matter is HSFloodingSystem's
 * WATER_DENSITY = 1.0: mass is per block, so 1.0 IS water. Treated planks at
 * 0.5 float a hull with half its depth clear; ordinary wood at 2.0 is twice as
 * dense as water and cannot float a solid hull at all.
 *
 * Single source of truth: TreatedWoodRegistry registers from this,
 * CreativeTabRegistry displays from it, the asset and data tests assert a file
 * per constant, and tools/build-block-mass.py mirrors these paths and masses.
 */
public enum TreatedWood {
    PLANKS("treated_planks", 0.5),
    SLAB("treated_slab", 0.25),
    STAIRS("treated_stairs", 0.25);

    private final String itemPath;
    private final double mass;

    TreatedWood(String itemPath, double mass) {
        this.itemPath = itemPath;
        this.mass = mass;
    }

    /** Registry path and the stem of every asset and data file for this variant. */
    public String itemPath() { return itemPath; }

    /** The sable:mass value our datapack must assert for this block. */
    public double mass() { return mass; }
}

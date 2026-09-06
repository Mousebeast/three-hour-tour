package com.mousebeast.threehourtour.scoop;

import com.mousebeast.threehourtour.ThreeHourTour;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * The sea scoop's upgrade axis, and the ONLY thing that varies its yield.
 *
 * L1-Q6, answered 2026-08-23: the sea is uniform. Where you sail does not
 * change what you scoop - what you have built does. Biome keying (L1-24) was
 * retired because nearly all the water is ocean, so it was never a real axis.
 *
 * Declared in ascending order. MeshTierTest enforces that each tier is
 * strictly faster and tougher than the one before, so the curve cannot silently
 * invert during tuning.
 */
public enum MeshTier {

    /** Craftable on the starting raft. Slow, fragile, common pulls. */
    TWINE("twine_mesh", 200, 256, "scoop/twine"),

    /** Mid-game. Needs metal, so it waits on a working Create setup. */
    CHAIN("chain_mesh", 120, 1024, "scoop/chain"),

    /** Research-gated. Needs prismarine, so it waits on a monument. */
    PRISMARINE("prismarine_mesh", 60, 3072, "scoop/prismarine");

    private final String itemPath;
    private final int ticksPerYield;
    private final int durability;
    private final ResourceLocation lootTable;

    MeshTier(String itemPath, int ticksPerYield, int durability, String lootPath) {
        this.itemPath = itemPath;
        this.ticksPerYield = ticksPerYield;
        this.durability = durability;
        this.lootTable = ResourceLocation.fromNamespaceAndPath(ThreeHourTour.MOD_ID, lootPath);
    }

    public String itemPath() { return itemPath; }

    /** Ticks of being under way between emissions. Lower is better. */
    public int ticksPerYield() { return ticksPerYield; }

    /** Emissions before the mesh is consumed. Higher is better. */
    public int durability() { return durability; }

    public ResourceLocation lootTable() { return lootTable; }

    public static Optional<MeshTier> byItemPath(String path) {
        if (path == null) {
            return Optional.empty();
        }
        for (MeshTier t : values()) {
            if (t.itemPath.equals(path)) {
                return Optional.of(t);
            }
        }
        return Optional.empty();
    }
}

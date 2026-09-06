package com.mousebeast.threehourtour.scoop;

import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class MeshTierTest {

    @Test
    void everyTierHasADistinctItemPath() {
        long distinct = java.util.Arrays.stream(MeshTier.values())
                .map(MeshTier::itemPath).distinct().count();
        assertEquals(MeshTier.values().length, distinct);
    }

    @Test
    void higherTiersEmitFasterAndLastLonger() {
        // The progression curve is the design. If a later tier is ever slower
        // or more fragile than an earlier one, the upgrade is a downgrade.
        MeshTier[] tiers = MeshTier.values();
        for (int i = 1; i < tiers.length; i++) {
            assertTrue(tiers[i].ticksPerYield() < tiers[i - 1].ticksPerYield(),
                    tiers[i] + " must emit faster than " + tiers[i - 1]);
            assertTrue(tiers[i].durability() > tiers[i - 1].durability(),
                    tiers[i] + " must last longer than " + tiers[i - 1]);
        }
    }

    @Test
    void everyTierHasItsOwnLootTable() {
        long distinct = java.util.Arrays.stream(MeshTier.values())
                .map(t -> t.lootTable().toString()).distinct().count();
        assertEquals(MeshTier.values().length, distinct);
    }

    @Test
    void lootTablesLiveUnderTheModNamespace() {
        for (MeshTier t : MeshTier.values()) {
            assertEquals("threehourtour", t.lootTable().getNamespace());
            assertTrue(t.lootTable().getPath().startsWith("scoop/"),
                    t + " loot table must live under scoop/");
        }
    }

    @Test
    void tierItemAndLootPathsAreExactlyThese() {
        // These strings are a contract with hand-authored recipe and loot-table JSON files.
        // Task 7 hand-authors JSON against these exact paths with no reference back to this enum.
        // A silent rename here breaks crafting months later. Pin them.
        assertEquals("twine_mesh", MeshTier.TWINE.itemPath());
        assertEquals("chain_mesh", MeshTier.CHAIN.itemPath());
        assertEquals("prismarine_mesh", MeshTier.PRISMARINE.itemPath());

        assertEquals("threehourtour:scoop/twine", MeshTier.TWINE.lootTable().toString());
        assertEquals("threehourtour:scoop/chain", MeshTier.CHAIN.lootTable().toString());
        assertEquals("threehourtour:scoop/prismarine", MeshTier.PRISMARINE.lootTable().toString());
    }

    @Test
    void thereAreExactlyThreeTiers() {
        assertEquals(3, MeshTier.values().length);
    }

    @Test
    void tiersAreFoundByItemPath() {
        assertEquals(Optional.of(MeshTier.TWINE), MeshTier.byItemPath("twine_mesh"));
        assertEquals(Optional.empty(), MeshTier.byItemPath("not_a_mesh"));
        assertEquals(Optional.empty(), MeshTier.byItemPath(null));
    }

    @Test
    void everyTierEmitsAtLeastOncePerMinute() {
        // A tier slower than this reads as broken to a player watching the
        // output chest, which is the whole point of physical yield (spec §8).
        for (MeshTier t : MeshTier.values()) {
            assertTrue(t.ticksPerYield() <= 1200, t + " is slower than one minute");
            assertTrue(t.ticksPerYield() > 0, t + " must have a positive period");
        }
    }
}

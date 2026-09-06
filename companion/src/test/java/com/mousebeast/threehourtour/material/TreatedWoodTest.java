package com.mousebeast.threehourtour.material;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TreatedWoodTest {

    @Test void theFamilyIsExactlyThreeHullBlocks() {
        // Spec 4.2: no treated door, trapdoor, fence or fence gate. Ordinary
        // fittings are never made heavy, so a treated fitting would improve on
        // nothing. If someone adds one, this test is where the argument lives.
        assertEquals(3, TreatedWood.values().length);
    }

    @Test void everyVariantHasAUniqueItemPathPrefixedTreated() {
        Set<String> seen = new HashSet<>();
        for (TreatedWood v : TreatedWood.values()) {
            assertTrue(seen.add(v.itemPath()), "duplicate item path: " + v.itemPath());
            // The datapack selects our blocks by id. A variant that forgot the
            // prefix would silently keep mass 2.0 from the tag-wide rule, which
            // looks exactly like the feature working.
            assertTrue(v.itemPath().startsWith("treated_"), v + ": " + v.itemPath());
        }
    }

    @Test void planksAreHalfAndTheThinnerTwoAreAQuarter() {
        assertEquals(0.5, TreatedWood.PLANKS.mass());
        assertEquals(0.25, TreatedWood.SLAB.mass());
        assertEquals(0.25, TreatedWood.STAIRS.mass());
    }

    @Test void everyVariantIsLighterThanWater() {
        // HSFloodingSystem.WATER_DENSITY = 1.0. A treated block at or above 1.0
        // could not float a hull built from it, which would defeat the point of
        // the material existing.
        for (TreatedWood v : TreatedWood.values()) {
            assertTrue(v.mass() < 1.0, v + " is not lighter than water: " + v.mass());
        }
    }

    @Test void everyVariantHasTheSameMassToVolumeRatio() {
        // The real invariant that makes shape conversion (plank <-> slab <->
        // stairs) safe - NOT "no recipe converts treated wood back into
        // planks". A treated plank is mass 0.5 at volume 1.0; two treated
        // slabs are mass 0.5 at volume 1.0 combined. Round-tripping between
        // shapes is mass- AND volume-neutral by construction, so a shape
        // recipe cannot create or destroy buoyancy no matter where it comes
        // from - including a KubeJS script or a third-party mod recipe that
        // a test scanning data/threehourtour/recipe could never see. Density
        // (mass/volume) being constant across the family is what actually
        // guards against a mass exploit; a recipe-directory scan only guards
        // against one recipe author being careless in one namespace.
        //
        // Volumes come from Sable's #sable:half_volume tag, which contains
        // #minecraft:slabs and #minecraft:stairs: those shapes displace half
        // a full block, so planks are volume 1.0 and slabs/stairs are 0.5.
        Map<TreatedWood, Double> volume = new EnumMap<>(TreatedWood.class);
        volume.put(TreatedWood.PLANKS, 1.0);
        volume.put(TreatedWood.SLAB, 0.5);
        volume.put(TreatedWood.STAIRS, 0.5);

        double expectedDensity = TreatedWood.PLANKS.mass() / volume.get(TreatedWood.PLANKS);
        for (TreatedWood v : TreatedWood.values()) {
            double density = v.mass() / volume.get(v);
            assertEquals(expectedDensity, density,
                    v + " density (mass/volume) diverges from the rest of the family - "
                    + "a shape conversion involving it could create or destroy buoyancy");
        }
    }
}

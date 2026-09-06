package com.mousebeast.threehourtour.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mousebeast.threehourtour.material.TreatedWood;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The generator mirrors the TreatedWood enum in Python. Nothing but this test
 * keeps the two in step, and a drifted entry is invisible: the block still
 * exists, still crafts, still floats - it just quietly weighs 2.0.
 */
class BlockMassDatapackTest {

    private static final Path PACK = Path.of(
            "../pack/overrides/config/paxi/datapacks/3ht-blockmass/data/threehourtour/physics_block_properties");

    private static JsonObject read(Path p) throws IOException {
        return JsonParser.parseString(new String(Files.readAllBytes(p))).getAsJsonObject();
    }

    @Test void everyTreatedVariantHasAMassOverrideMatchingTheEnum() throws IOException {
        for (TreatedWood v : TreatedWood.values()) {
            Path p = PACK.resolve(v.itemPath() + ".json");
            assertTrue(Files.exists(p), "no mass override for " + v
                    + " - it would weigh 2.0 from the tag rule. Re-run tools/build-block-mass.py");
            JsonObject o = read(p);
            assertEquals(v.mass(),
                    o.getAsJsonObject("properties").get("sable:mass").getAsDouble(),
                    "mass mismatch for " + v);
            assertEquals("threehourtour:" + v.itemPath(), o.get("selector").getAsString());
        }
    }

    @Test void treatedOverridesOutrankTheOrdinaryWoodRule() throws IOException {
        // The entire mechanism. If ordinary ever outranks treated, every treated
        // block silently weighs 2.0 and the layer does nothing at all.
        assertTrue(read(PACK.resolve("treated_planks.json")).get("priority").getAsInt()
                 > read(PACK.resolve("ordinary_planks.json")).get("priority").getAsInt(),
                "treated priority must exceed ordinary priority");
    }

    @Test void everyOrdinaryHullShapeLandsAtDensityTwo() throws IOException {
        // WATER_DENSITY is 1.0, and density is mass/volume. Sable's
        // #sable:half_volume already gives slabs and stairs volume 0.5, so they
        // need HALF the mass of planks to reach the same density. Asserting the
        // density rather than the mass is what keeps someone from "fixing" the
        // slab values up to 2.0 and quietly making slab hulls twice as punishing
        // as plank hulls.
        Map<String, Double> volume = Map.of(
                "planks", 1.0, "logs", 1.0, "wooden_slabs", 0.5, "wooden_stairs", 0.5,
                // L3-Q7: not wood, but Sable left all three at or below treated
                // wood's density, so each bypassed the treating chain entirely.
                // None is in a Sable volume tag, so all are volume 1.0.
                "wool", 1.0, "leaves", 1.0, "bamboo_blocks", 1.0);
        for (Map.Entry<String, Double> e : volume.entrySet()) {
            double m = read(PACK.resolve("ordinary_" + e.getKey() + ".json"))
                    .getAsJsonObject("properties").get("sable:mass").getAsDouble();
            double density = m / e.getValue();
            assertEquals(2.0, density,
                    "ordinary_" + e.getKey() + " density should be 2.0 (twice water), was " + density);
        }
    }

    @Test void doubleOverrideIsExactlyTwiceTheBaseMass() throws IOException {
        // A double slab is twice the material AND twice the volume, so doubling
        // mass (not leaving it alone, not tripling it) is what keeps density
        // constant. The arithmetic (mass * 2) is easy to get right by
        // construction and just as easy to silently break in a future edit -
        // nothing else in this test class would notice a wrong multiplier or a
        // dropped override key, so it gets its own assertion across every
        // generated file, not just the ones the other tests already touch.
        try (var files = Files.list(PACK)) {
            for (Path p : (Iterable<Path>) files.filter(f -> f.toString().endsWith(".json"))::iterator) {
                JsonObject o = read(p);
                double base = o.getAsJsonObject("properties").get("sable:mass").getAsDouble();
                JsonObject overrides = o.getAsJsonObject("overrides");
                assertNotNull(overrides, "missing overrides block in " + p.getFileName());
                assertTrue(overrides.has("type=double"),
                        "missing type=double override in " + p.getFileName());
                double doubled = overrides.getAsJsonObject("type=double")
                        .get("sable:mass").getAsDouble();
                assertEquals(base * 2, doubled,
                        "type=double mass should be exactly twice the base mass in " + p.getFileName());
            }
        }
    }

    @Test void fittingsAreLeftAlone() throws IOException {
        // L3-02a, a negative requirement. Nobody builds a ship out of doors, and
        // taxing fittings is a tax with no design purpose behind it.
        //
        // Checking four specific fitting filenames don't exist only catches a
        // fitting tag added under one of those exact names. A fitting rule
        // added under any other name - or any other extra rule entirely -
        // would slip through silently. Asserting the directory contains
        // EXACTLY the seven expected files by name is strictly stronger and
        // no harder to write: it catches every accidental extra rule, not
        // just the four fitting names anticipated up front.
        Set<String> expected = Set.of(
                "ordinary_planks.json", "ordinary_logs.json",
                "ordinary_wooden_slabs.json", "ordinary_wooden_stairs.json",
                "ordinary_wool.json", "ordinary_leaves.json", "ordinary_bamboo_blocks.json",
                "treated_planks.json", "treated_slab.json", "treated_stairs.json");

        Set<String> actual;
        try (var files = Files.list(PACK)) {
            actual = files.map(p -> p.getFileName().toString())
                    .collect(java.util.stream.Collectors.toSet());
        }

        assertEquals(expected, actual,
                "physics_block_properties must contain exactly the seven expected files - "
                + "found an unexpected extra or missing file");
    }
}

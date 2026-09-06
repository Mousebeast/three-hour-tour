package com.mousebeast.threehourtour.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mousebeast.threehourtour.material.TreatedWood;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TreatedWoodDataFilesTest {

    private static Path res(String rel) {
        return Path.of("src/main/resources", rel);
    }

    @Test void everyVariantDropsSomethingWhenBroken() throws IOException {
        // Refit is break-and-replace. A block with no loot table destroys the
        // player's material every time they move a wall.
        for (TreatedWood v : TreatedWood.values()) {
            Path p = res("data/threehourtour/loot_table/blocks/" + v.itemPath() + ".json");
            assertTrue(Files.exists(p), "missing loot table for " + v + " - it would drop NOTHING");

            // Parse JSON and assert every item entry actually drops the correct block.
            // (For slabs, the block name also appears in conditions; substring match would miss
            // a copy-paste error changing the drop to the wrong item.)
            JsonObject loot = JsonParser.parseString(new String(Files.readAllBytes(p))).getAsJsonObject();
            String expectedName = "threehourtour:" + v.itemPath();
            boolean foundCorrectDrop = false;

            if (loot.has("pools")) {
                var pools = loot.getAsJsonArray("pools");
                for (var pool : pools) {
                    if (pool.getAsJsonObject().has("entries")) {
                        var entries = pool.getAsJsonObject().getAsJsonArray("entries");
                        for (var entry : entries) {
                            JsonObject e = entry.getAsJsonObject();
                            if ("minecraft:item".equals(e.get("type").getAsString())) {
                                if (expectedName.equals(e.get("name").getAsString())) {
                                    foundCorrectDrop = true;
                                }
                            }
                        }
                    }
                }
            }
            assertTrue(foundCorrectDrop, v + "'s loot table does not drop " + expectedName);
        }
    }

    @Test void treatedPlanksAreInTheVanillaPlanksTag() throws IOException {
        // L3-08. Without this, pack-wide plank recipes reject treated wood.
        JsonObject tag = JsonParser.parseString(new String(Files.readAllBytes(
                res("data/minecraft/tags/block/planks.json")))).getAsJsonObject();
        assertTrue(tag.get("values").toString().contains("threehourtour:treated_planks"));
    }

    @Test void itemTagFollowsSingularPathConvention() throws IOException {
        // The dual-path convention (block/ and blocks/) applies to BLOCK tags only.
        // Other mods in this pack read the plural block path; no mod reads a plural item path.
        // Singular item tag must exist and contain treated planks.
        Path singular = res("data/minecraft/tags/item/planks.json");
        assertTrue(Files.exists(singular), "singular item tag missing - recipes taking #minecraft:planks items would fail");
        JsonObject tag = JsonParser.parseString(new String(Files.readAllBytes(singular))).getAsJsonObject();
        assertTrue(tag.get("values").toString().contains("threehourtour:treated_planks"),
                "singular item tag does not contain treated planks");

        // Plural item-tag path must NOT exist (unlike block tags).
        Path plural = res("data/minecraft/tags/items/planks.json");
        assertFalse(Files.exists(plural),
                "plural item tag should not exist; only block tags use the dual-path convention");
    }

    @Test void singularAndPluralTagPathsAreByteIdentical() throws IOException {
        // Same reasoning as ShipOnlyTagFilesTest: two readers, one tag, and a
        // drift between them is silent.
        String[] names = {"planks", "wooden_slabs", "wooden_stairs", "mineable/axe"};
        for (String n : names) {
            String singular = new String(Files.readAllBytes(res("data/minecraft/tags/block/" + n + ".json")));
            String plural = new String(Files.readAllBytes(res("data/minecraft/tags/blocks/" + n + ".json")));
            assertEquals(singular, plural, n + " has drifted between block/ and blocks/");
        }
    }

    @Test void allThreeTreatingRecipesExistAndProduceTreatedPlanks() throws IOException {
        // L3-10..L3-12. These are the ONLY way to obtain treated planks. A typo
        // in a fluid id fails silently at load and the block is uncraftable with
        // no error a player would ever see. This test must parse and validate the
        // actual JSON structure, not just substring-match; a typo'd type (e.g.
        // fluid_stack instead of neoforge:single) would still pass a contains() check
        // while the recipe fails to parse at load.

        // Tier 1: shapeless craft with result field
        {
            Path p = res("data/threehourtour/recipe/treating_fish_oil.json");
            assertTrue(Files.exists(p), "missing treating recipe: " + p);
            JsonObject r = JsonParser.parseString(new String(Files.readAllBytes(p))).getAsJsonObject();
            String resultId = r.getAsJsonObject("result").get("id").getAsString();
            assertEquals("threehourtour:treated_planks", resultId,
                    "treating_fish_oil result id must be threehourtour:treated_planks, got " + resultId);
        }

        // Tier 2 and 3: create:filling with results array and fluid ingredient validation
        for (String[] expected : new String[][]{
                {"treating_plant_oil", "createdieselgenerators:plant_oil", "32"},
                {"treating_diesel", "createdieselgenerators:diesel", "8"}
        }) {
            String filename = expected[0];
            String expectedFluid = expected[1];
            String expectedAmount = expected[2];

            Path p = res("data/threehourtour/recipe/" + filename + ".json");
            assertTrue(Files.exists(p), "missing treating recipe: " + p);
            JsonObject r = JsonParser.parseString(new String(Files.readAllBytes(p))).getAsJsonObject();

            // Assert result id
            String resultId = r.getAsJsonArray("results").get(0).getAsJsonObject().get("id").getAsString();
            assertEquals("threehourtour:treated_planks", resultId,
                    filename + " result id must be threehourtour:treated_planks, got " + resultId);

            // Assert fluid ingredient has type neoforge:single (not fluid_stack or other)
            var ingredients = r.getAsJsonArray("ingredients");
            var fluidIngredient = ingredients.asList().stream()
                    .map(e -> e.getAsJsonObject())
                    .filter(e -> e.has("type") && "neoforge:single".equals(e.get("type").getAsString()))
                    .findFirst();
            assertTrue(fluidIngredient.isPresent(),
                    filename + " must have a fluid ingredient with type \"neoforge:single\"");

            var fluid = fluidIngredient.get();
            String actualFluid = fluid.get("fluid").getAsString();
            assertEquals(expectedFluid, actualFluid,
                    filename + " fluid must be " + expectedFluid + ", got " + actualFluid);

            int actualAmount = fluid.get("amount").getAsInt();
            assertEquals(Integer.parseInt(expectedAmount), actualAmount,
                    filename + " fluid amount must be " + expectedAmount + ", got " + actualAmount);
        }
    }

    @Test void tierOneTreatingIsOneToOne() throws IOException {
        // L3-13. If this yield ever rises, treating starts CREATING wood and the
        // density economy stops meaning anything.
        JsonObject r = JsonParser.parseString(new String(Files.readAllBytes(
                res("data/threehourtour/recipe/treating_fish_oil.json")))).getAsJsonObject();
        int out = r.getAsJsonObject("result").get("count").getAsInt();
        long planksIn = r.getAsJsonArray("ingredients").asList().stream()
                .filter(e -> e.getAsJsonObject().has("tag")).count();
        assertEquals(planksIn, out, "tier 1 must be 1:1 - planks in must equal planks out");
    }
}

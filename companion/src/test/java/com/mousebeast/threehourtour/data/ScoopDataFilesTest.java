package com.mousebeast.threehourtour.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mousebeast.threehourtour.scoop.MeshTier;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ScoopDataFilesTest {

    private static Path resource(String rel) {
        return Path.of("src/main/resources", rel);
    }

    @Test
    void everyMeshTierHasALootTableFileOnDisk() {
        // A tier pointing at a missing table throws at runtime, on a ship, in
        // the middle of a voyage. Catch it at build time instead.
        for (MeshTier tier : MeshTier.values()) {
            var id = tier.lootTable();
            Path p = resource("data/" + id.getNamespace() + "/loot_table/" + id.getPath() + ".json");
            assertTrue(Files.exists(p), "missing loot table for " + tier + ": " + p);
        }
    }

    @Test
    void everyMeshTierLootTableHasAtLeastOnePopulatedPool() throws IOException {
        // Existing-on-disk isn't enough: a table with zero pools, or a pool
        // with zero entries, parses fine and rolls an empty list at runtime -
        // the exact condition that made emitYield() report success for a
        // deposit that never happened (the second whole-branch Critical).
        for (MeshTier tier : MeshTier.values()) {
            var id = tier.lootTable();
            Path p = resource("data/" + id.getNamespace() + "/loot_table/" + id.getPath() + ".json");
            String json = new String(Files.readAllBytes(p));
            JsonObject table = JsonParser.parseString(json).getAsJsonObject();
            JsonArray pools = table.getAsJsonArray("pools");
            assertNotNull(pools, "loot table for " + tier + " has no pools array: " + p);
            assertTrue(pools.size() >= 1, "loot table for " + tier + " has no pools: " + p);

            boolean anyPoolHasEntries = false;
            for (var poolEl : pools) {
                JsonArray entries = poolEl.getAsJsonObject().getAsJsonArray("entries");
                if (entries != null && entries.size() >= 1) {
                    anyPoolHasEntries = true;
                    break;
                }
            }
            assertTrue(anyPoolHasEntries,
                "loot table for " + tier + " has no pool with at least one entry: " + p);
        }
    }

    @Test
    void everyMeshTierHasACraftingRecipe() {
        for (MeshTier tier : MeshTier.values()) {
            Path p = resource("data/threehourtour/recipe/" + tier.itemPath() + ".json");
            assertTrue(Files.exists(p), "missing recipe for " + tier + ": " + p);
        }
    }

    @Test
    void theScoopItselfHasARecipe() {
        assertTrue(Files.exists(resource("data/threehourtour/recipe/sea_scoop.json")));
    }

    @Test
    void theScoopItselfHasABlockLootTable() {
        // Without this, breaking a placed scoop resolves to LootTable.EMPTY and
        // the crafted block is destroyed on every break. Regression guard.
        assertTrue(Files.exists(resource("data/threehourtour/loot_table/blocks/sea_scoop.json")));
    }

    @Test
    void everyMeshRecipeProducesTheItemItsTierNames() throws IOException {
        // The recipe JSON's result.id is a hand-authored literal with no reference
        // back to the MeshTier enum. The other tests key off the enum, so they would
        // all still pass while the recipe produced an item that no longer exists.
        // This test catches that silent failure.
        for (MeshTier tier : MeshTier.values()) {
            Path p = resource("data/threehourtour/recipe/" + tier.itemPath() + ".json");
            assertTrue(Files.exists(p), "missing recipe for " + tier + ": " + p);

            String recipeJson = new String(Files.readAllBytes(p));
            JsonObject recipe = JsonParser.parseString(recipeJson).getAsJsonObject();
            String resultId = recipe.getAsJsonObject("result").get("id").getAsString();
            String expectedId = "threehourtour:" + tier.itemPath();
            assertEquals(expectedId, resultId,
                "recipe for " + tier + " does not produce " + expectedId + "; file: " + p);
        }
    }
}

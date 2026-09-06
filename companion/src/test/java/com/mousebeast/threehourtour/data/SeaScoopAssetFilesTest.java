package com.mousebeast.threehourtour.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mousebeast.threehourtour.scoop.MeshTier;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the client-side assets a whole-branch review found missing: no
 * blockstate or model for the sea scoop, no item models for it or the three
 * meshes, no lang keys for any of them. A missing model shows the
 * black-and-magenta placeholder cube; a missing lang key shows the raw
 * translation key. Both are invisible until someone opens the game, so catch
 * them at build time instead - same reasoning as ScoopDataFilesTest, applied
 * to assets rather than data.
 */
class SeaScoopAssetFilesTest {

    private static Path resource(String rel) {
        return Path.of("src/main/resources", rel);
    }

    private static JsonObject lang() throws IOException {
        Path p = resource("assets/threehourtour/lang/en_us.json");
        String json = new String(Files.readAllBytes(p));
        return JsonParser.parseString(json).getAsJsonObject();
    }

    @Test
    void theScoopHasABlockstate() {
        Path p = resource("assets/threehourtour/blockstates/sea_scoop.json");
        assertTrue(Files.exists(p), "missing blockstate for sea_scoop: " + p);
    }

    @Test
    void theScoopHasABlockModel() {
        Path p = resource("assets/threehourtour/models/block/sea_scoop.json");
        assertTrue(Files.exists(p), "missing block model for sea_scoop: " + p);
    }

    @Test
    void theScoopHasAnItemModel() {
        Path p = resource("assets/threehourtour/models/item/sea_scoop.json");
        assertTrue(Files.exists(p), "missing item model for sea_scoop: " + p);
    }

    @Test
    void theScoopHasALangKey() throws IOException {
        JsonObject lang = lang();
        assertTrue(lang.has("block.threehourtour.sea_scoop"),
            "missing lang key block.threehourtour.sea_scoop");
        assertFalse(lang.get("block.threehourtour.sea_scoop").getAsString().isBlank(),
            "lang key block.threehourtour.sea_scoop is blank");
    }

    @Test
    void everyMeshTierHasAnItemModelFileOnDisk() {
        // A tier with no item model renders the missing-model cube in hand
        // and in the inventory. Catch it at build time instead.
        for (MeshTier tier : MeshTier.values()) {
            Path p = resource("assets/threehourtour/models/item/" + tier.itemPath() + ".json");
            assertTrue(Files.exists(p), "missing item model for " + tier + ": " + p);
        }
    }

    @Test
    void everyMeshTierHasALangKey() throws IOException {
        // A missing lang key reads as the raw translation key in every
        // tooltip. Invisible until someone opens the game - fail the build.
        JsonObject lang = lang();
        for (MeshTier tier : MeshTier.values()) {
            String key = "item.threehourtour." + tier.itemPath();
            assertTrue(lang.has(key), "missing lang key for " + tier + ": " + key);
            assertFalse(lang.get(key).getAsString().isBlank(),
                "lang key for " + tier + " is blank: " + key);
        }
    }

    @Test
    void theCreativeTabHasALangKey() throws IOException {
        // Without a tab, items belonging to none are excluded from JEI's
        // ingredient list - the whole reason this task exists.
        JsonObject lang = lang();
        assertTrue(lang.has("itemGroup.threehourtour.main"),
            "missing lang key itemGroup.threehourtour.main");
        assertFalse(lang.get("itemGroup.threehourtour.main").getAsString().isBlank(),
            "lang key itemGroup.threehourtour.main is blank");
    }
}

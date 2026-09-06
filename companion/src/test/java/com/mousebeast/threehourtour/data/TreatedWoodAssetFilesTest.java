package com.mousebeast.threehourtour.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mousebeast.threehourtour.material.TreatedWood;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Owns L3-07. A missing model renders the black-and-magenta placeholder and a
 * missing lang key shows the raw translation key - both invisible until someone
 * opens the game. Same reasoning as SeaScoopAssetFilesTest.
 */
class TreatedWoodAssetFilesTest {

    private static Path asset(String rel) {
        return Path.of("src/main/resources/assets/threehourtour", rel);
    }

    @Test void everyVariantHasABlockstateAndAnItemModel() {
        for (TreatedWood v : TreatedWood.values()) {
            assertTrue(Files.exists(asset("blockstates/" + v.itemPath() + ".json")),
                    "missing blockstate for " + v);
            assertTrue(Files.exists(asset("models/item/" + v.itemPath() + ".json")),
                    "missing item model for " + v);
        }
    }

    @Test void everyVariantHasANonBlankLangKey() throws IOException {
        JsonObject lang = JsonParser.parseString(
                new String(Files.readAllBytes(asset("lang/en_us.json")))).getAsJsonObject();
        for (TreatedWood v : TreatedWood.values()) {
            String key = "block.threehourtour." + v.itemPath();
            assertTrue(lang.has(key), "missing lang key " + key);
            assertFalse(lang.get(key).getAsString().isBlank(), "blank lang key " + key);
        }
    }

    /**
     * Until 2026-09-03 this asserted that every block model referenced a
     * VANILLA texture, because the mod shipped none of its own. That is no
     * longer true: treated planks now have their own texture, precisely so a
     * hull cannot be mistaken for ordinary dark oak.
     *
     * The risk the old assertion guarded is unchanged though -- a model naming
     * a `threehourtour:` texture that does not exist renders the black-and-
     * magenta placeholder, and nothing says so until someone opens the game.
     * So this checks the real thing instead of a proxy for it: every reference
     * resolves, whichever namespace it is in.
     */
    @Test void everyBlockModelTextureResolves() throws IOException {
        for (String m : new String[]{"treated_planks", "treated_slab", "treated_slab_top",
                                     "treated_stairs", "treated_stairs_inner",
                                     "treated_stairs_outer"}) {
            Path p = asset("models/block/" + m + ".json");
            assertTrue(Files.exists(p), "missing block model " + m);
            JsonObject textures = JsonParser.parseString(new String(Files.readAllBytes(p)))
                    .getAsJsonObject().getAsJsonObject("textures");
            assertNotNull(textures, m + " declares no textures");
            for (String key : textures.keySet()) {
                String ref = textures.get(key).getAsString();
                if (ref.startsWith("threehourtour:")) {
                    String path = ref.substring("threehourtour:".length());
                    assertTrue(Files.exists(asset("textures/" + path + ".png")),
                            m + "." + key + " names " + ref
                            + ", which has no PNG -- it will render as the missing-texture"
                            + " placeholder and nothing else will say so");
                } else {
                    assertTrue(ref.startsWith("minecraft:"),
                            m + "." + key + " references neither us nor vanilla: " + ref);
                }
            }
        }
    }

    /**
     * The other half: art we ship that nothing points at. An orphan PNG is dead
     * weight in the jar and usually means a model was meant to be repointed and
     * was not -- the same mistake as above, seen from the other side.
     */
    @Test void everyShippedTextureIsReferencedByAModel() throws IOException {
        Path textures = asset("textures");
        if (!Files.exists(textures)) return;          // shipping none is still allowed
        StringBuilder allModels = new StringBuilder();
        for (String dir : new String[]{"models/item", "models/block"}) {
            Path d = asset(dir);
            if (!Files.exists(d)) continue;
            try (var walk = Files.walk(d)) {
                for (Path f : walk.filter(Files::isRegularFile).toList()) {
                    allModels.append(new String(Files.readAllBytes(f)));
                }
            }
        }
        try (var walk = Files.walk(textures)) {
            for (Path png : walk.filter(f -> f.toString().endsWith(".png")).toList()) {
                String rel = textures.relativize(png).toString().replace(".png", "");
                String ref = "threehourtour:" + rel.replace('\\', '/');
                assertTrue(allModels.toString().contains(ref),
                        "orphan texture " + png.getFileName() + ": no model references " + ref);
            }
        }
    }

    @Test void theStairsBlockstateHasAllFortyVariants() {
        // Hand-editing this file is how a facing goes missing; a missing variant
        // renders as the placeholder cube for that one orientation only.
        JsonObject bs;
        try {
            bs = JsonParser.parseString(new String(Files.readAllBytes(
                    asset("blockstates/treated_stairs.json")))).getAsJsonObject();
        } catch (IOException e) {
            throw new AssertionError("missing treated_stairs blockstate", e);
        }
        assertEquals(40, bs.getAsJsonObject("variants").size());
    }
}

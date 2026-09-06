package com.mousebeast.threehourtour.data;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The twine table is the entire day-one economy, and its weights are a
 * denominator problem: adding an entry silently dilutes every other share.
 * On 2026-08-29 iron nuggets were tuned to 16.7% and that number is
 * ring-fenced, so this test pins the total as well as the entries.
 */
class TwineLootTableTest {

    private static Path resource(String rel) {
        return Path.of("src/main/resources", rel);
    }

    private static final Path TABLE =
            resource("data/threehourtour/loot_table/scoop/twine.json");

    private Map<String, Integer> weights() throws Exception {
        JsonObject root = JsonParser.parseString(Files.readString(TABLE))
                .getAsJsonObject();
        Map<String, Integer> out = new HashMap<>();
        root.getAsJsonArray("pools").forEach(pool ->
                pool.getAsJsonObject().getAsJsonArray("entries").forEach(e -> {
                    JsonObject o = e.getAsJsonObject();
                    out.merge(o.get("name").getAsString(),
                              o.has("weight") ? o.get("weight").getAsInt() : 1,
                              Integer::sum);
                }));
        return out;
    }

    @Test
    void totalWeightIsOneHundredAndTwenty() throws Exception {
        assertEquals(120, weights().values().stream().mapToInt(Integer::intValue).sum());
    }

    @Test
    void ironNuggetShareIsUnchanged() throws Exception {
        assertEquals(20, weights().get("minecraft:iron_nugget"));
    }

    @Test
    void clayIsNoLongerTheBindingConstraint() throws Exception {
        assertEquals(11, weights().get("minecraft:clay_ball"));
    }

    @Test
    void inkSacsComeFromTheScoopBecauseFishingYieldsNone() throws Exception {
        assertEquals(8, weights().get("minecraft:ink_sac"));
    }

    @Test
    void stringIsLeftToBind() throws Exception {
        // Something has to bind, and a currency that also builds the mesh makes
        // the speed-versus-progress trade visible. Spec 7.1.
        assertEquals(8, weights().get("minecraft:string"));
    }
}

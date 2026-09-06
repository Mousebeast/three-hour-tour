package com.mousebeast.threehourtour.research;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * ResearchRegistry.TROPHIES and .SEALS cannot be exercised directly under
 * the plain `test` task, for the same reason AshItemTest documents: merely
 * loading ResearchRegistry touches BuiltInRegistries.ITEM, and
 * BuiltInRegistries.<clinit> refuses to run without a full vanilla
 * Bootstrap.bootStrap() -- already confirmed unworkable for this registry
 * (see AshItemTest's own note on NeoForge's FeatureFlagLoader failing a
 * step further in). So the id assertions below read the source's own
 * registerAll("trophy_", ...) / registerAll("seal_", ...) calls instead of
 * loading the class -- the same technique
 * faction_checks._string_list_after uses on the Python side to see the
 * same two calls.
 */
class TrophyItemTest {

    private static final Path REGISTRY_SOURCE = Path.of(
        "src/main/java/com/mousebeast/threehourtour/research/ResearchRegistry.java");

    private static final Path MODELS =
        Path.of("src/main/resources/assets/threehourtour/models/item");

    private static final List<String> BOSS_KEYS = List.of(
        "wither", "ignis", "monstrosity",
        "dead_king", "fire_boss", "harbinger",
        "ancient_remnant", "ender_guardian", "leviathan",
        "maledictus", "scylla", "ender_dragon");

    private static final List<String> POOLS =
        List.of("alpha", "beta", "gamma", "delta");

    private static Set<String> keysAfter(String source, String anchor) {
        Matcher call = Pattern.compile(
                Pattern.quote(anchor) + "(.*?)\\);", Pattern.DOTALL)
                .matcher(source);
        assertTrue(call.find(),
                   "expected to find " + anchor + " in " + REGISTRY_SOURCE);
        Set<String> keys = new TreeSet<>();
        Matcher key = Pattern.compile("\"([a-z0-9_]+)\"").matcher(call.group(1));
        while (key.find()) {
            keys.add(key.group(1));
        }
        return keys;
    }

    @Test
    void everyBossHasATrophy() throws Exception {
        assertEquals(12, BOSS_KEYS.size());
        String source = Files.readString(REGISTRY_SOURCE);
        Set<String> registered = keysAfter(source, "registerAll(\"trophy_\",");
        for (String key : BOSS_KEYS) {
            assertTrue(registered.contains(key),
                       "no trophy registered for " + key);
        }
        assertEquals(new TreeSet<>(BOSS_KEYS), registered,
                     "trophy_ registerAll call should register exactly the "
                     + "twelve boss keys, no more, no fewer");
    }

    @Test
    void everyPoolHasASeal() throws Exception {
        String source = Files.readString(REGISTRY_SOURCE);
        Set<String> registered = keysAfter(source, "registerAll(\"seal_\",");
        for (String pool : POOLS) {
            assertTrue(registered.contains(pool),
                       "no seal registered for " + pool);
        }
        assertEquals(new TreeSet<>(POOLS), registered,
                     "seal_ registerAll call should register exactly the "
                     + "four pools, no more, no fewer");
    }

    @Test
    void everyTrophyAndSealHasAModel() throws Exception {
        for (String key : BOSS_KEYS) {
            assertTrue(Files.exists(MODELS.resolve("trophy_" + key + ".json")),
                       "no model for trophy_" + key);
        }
        for (String pool : POOLS) {
            assertTrue(Files.exists(MODELS.resolve("seal_" + pool + ".json")),
                       "no model for seal_" + pool);
        }
    }
}

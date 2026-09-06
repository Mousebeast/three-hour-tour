package com.mousebeast.threehourtour.research;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mousebeast.threehourtour.ThreeHourTour;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * ResearchRegistry.ASH.getId() cannot be exercised directly under the plain
 * `test` task: merely loading ResearchRegistry touches BuiltInRegistries.ITEM,
 * and BuiltInRegistries.<clinit> refuses to run without a full vanilla
 * Bootstrap.bootStrap() - confirmed by trying it, which then fails a step
 * further in, in NeoForge's FeatureFlagLoader, because LoadingModList.get()
 * is null outside FML's real mod-loading context. Nothing in this module
 * loads a DeferredRegister-backed class from a unit test for exactly this
 * reason (see VesselLookupTest's own note on running "with no [Sable]
 * infrastructure on the classpath"); registered-object behaviour that needs
 * a running game belongs in the gameTestServer run, not here.
 *
 * So this proves the same two facts a live getId() call would - the id
 * "ash" resolves under the mod's own namespace, and ASH is declared as a
 * proper DeferredHolder<Item, Item> registered through ITEMS - by reading
 * the source instead of loading the class. ITEMS is
 * DeferredRegister.create(BuiltInRegistries.ITEM, ThreeHourTour.MOD_ID), so
 * ITEMS.register("ash", ...) resolving under MOD_ID ("threehourtour") is
 * exactly what makes the id threehourtour:ash.
 */
class AshItemTest {

    private static final Path REGISTRY_SOURCE = Path.of(
        "src/main/java/com/mousebeast/threehourtour/research/ResearchRegistry.java");
    private static final Path LANG = Path.of(
        "src/main/resources/assets/threehourtour/lang/en_us.json");
    private static final Path MODEL = Path.of(
        "src/main/resources/assets/threehourtour/models/item/ash.json");

    @Test
    void ashIsRegisteredUnderTheModsOwnNamespace() throws Exception {
        assertEquals("threehourtour", ThreeHourTour.MOD_ID);

        String source = Files.readString(REGISTRY_SOURCE);
        assertTrue(
            source.matches("(?s).*DeferredHolder<Item, Item> ASH\\s*=\\s*"
                + "ITEMS\\.register\\(\\s*\"ash\"\\s*,.*"),
            "expected ResearchRegistry to declare "
                + "\"DeferredHolder<Item, Item> ASH = ITEMS.register(\"ash\", ...)\"");
    }

    @Test
    void ashHasANameAndAModel() throws Exception {
        assertTrue(Files.readString(LANG).contains("item.threehourtour.ash"));
        assertTrue(Files.exists(MODEL));
    }
}

package com.mousebeast.threehourtour.data;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * data/threehourtour/tags/block/ship_only.json (1.21's singular path) and
 * data/threehourtour/tags/blocks/ship_only.json (the plural path other mods
 * in the pack read) must stay byte-identical - they are the same tag declared
 * twice for two different readers. Nothing else enforces that; an edit to one
 * that misses the other silently drifts, and the first sign would be a mod
 * that reads the stale copy behaving differently from the game itself. This
 * test loads both off the classpath and fails the build the moment they
 * diverge.
 */
class ShipOnlyTagFilesTest {

    private static final String SINGULAR = "data/threehourtour/tags/block/ship_only.json";
    private static final String PLURAL = "data/threehourtour/tags/blocks/ship_only.json";

    @Test void theSingularAndPluralShipOnlyTagFilesAreByteIdentical() {
        String singular = read(SINGULAR);
        String plural = read(PLURAL);
        assertEquals(singular, plural,
                SINGULAR + " and " + PLURAL + " have drifted apart - keep them identical.");
    }

    private static String read(String resourcePath) {
        try (InputStream in = ShipOnlyTagFilesTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            assertNotNull(in, "missing classpath resource: " + resourcePath);
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

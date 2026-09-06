package com.mousebeast.threehourtour.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Carry On's blacklist decides whether a player can pick our blocks up.
 *
 * This tag shipped for a year at the PLURAL path only, unlike every
 * {@code minecraft:} block tag in this mod, which ships at both. That is not a
 * harmless inconsistency: 1.21 moved registry tags to the singular directory,
 * Sophisticated Backpacks and ExtraStorage both write their Carry On
 * blacklists to {@code tags/block/}, and Carry On's own copies at the plural
 * path are empty defaults that prove nothing about whether it is still read.
 * A blacklist that silently does not apply looks exactly like one that does
 * until a player sneak-clicks and walks off with the block.
 *
 * <p>Both entries matter for a concrete reason. The ship core must never be
 * portable - a carried core reopens the exploits {@code ShipOnlyPlacement}
 * exists to close. The research terminal must not be, because its bind action
 * is sneak + right-click, which is exactly the gesture Carry On claims: with
 * the terminal off this list, adopting a pinned research node is unreachable.
 */
class CarryOnBlacklistFilesTest {

    private static final String SINGULAR = "data/carryon/tags/block/block_blacklist.json";
    private static final String PLURAL = "data/carryon/tags/blocks/block_blacklist.json";

    @Test
    @DisplayName("both tag paths exist and are byte-identical")
    void bothPathsAgree() {
        assertEquals(read(SINGULAR), read(PLURAL),
                SINGULAR + " and " + PLURAL + " have drifted apart - keep them identical.");
    }

    @Test
    @DisplayName("the ship core is blacklisted at both paths")
    void shipCoreIsBlacklisted() {
        for (String path : new String[]{SINGULAR, PLURAL}) {
            assertTrue(read(path).contains("threehourtour:ship_core"),
                    path + " does not blacklist the ship core - a carried core is a placement exploit");
        }
    }

    @Test
    @DisplayName("the research terminal is blacklisted at both paths")
    void researchTerminalIsBlacklisted() {
        for (String path : new String[]{SINGULAR, PLURAL}) {
            assertTrue(read(path).contains("threehourtour:research_terminal"),
                    path + " does not blacklist the research terminal - Carry On would eat the "
                            + "sneak + right-click that binds a research node");
        }
    }

    private static String read(String resourcePath) {
        try (InputStream in = CarryOnBlacklistFilesTest.class.getClassLoader()
                .getResourceAsStream(resourcePath)) {
            assertNotNull(in, "missing classpath resource: " + resourcePath);
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

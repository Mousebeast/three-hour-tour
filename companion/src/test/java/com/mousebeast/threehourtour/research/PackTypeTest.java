package com.mousebeast.threehourtour.research;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PackTypeTest {

    @Test
    void thereAreExactlySixPackTypes() {
        assertEquals(6, PackType.values().length);
    }

    @Test
    void itemPathsMatchTheGeneratorsExpectation() {
        assertEquals("pack_trawl", PackType.TRAWL.itemPath());
        assertEquals("pack_abyssal", PackType.ABYSSAL.itemPath());
    }

    @Test
    void itemIdsUseTheModsOwnNamespaceNotTheDirectoryName() {
        // The deployed tree demanded "3ht:pack_trawl", a namespace no mod owns,
        // which made all 206 quest tasks uncompletable. This is that regression.
        for (PackType type : PackType.values()) {
            assertTrue(type.itemId().startsWith("threehourtour:"),
                    type + " must use the mod id, got " + type.itemId());
        }
    }

    @Test
    void everyPackTypeHasADistinctPath() {
        Set<String> paths = new HashSet<>();
        for (PackType type : PackType.values()) paths.add(type.itemPath());
        assertEquals(PackType.values().length, paths.size());
    }

    @Test
    void typesAreNamedForTheTreesPackIds() {
        Set<String> expected = new HashSet<>(Arrays.asList(
                "trawl", "foundry", "farm", "husbandry", "arcane", "abyssal"));
        Set<String> actual = new HashSet<>();
        for (PackType type : PackType.values()) actual.add(type.treeId());
        assertEquals(expected, actual);
    }

    @Test
    void everyPackTypeHasABundle() {
        for (PackType type : PackType.values()) {
            assertEquals("bundle_" + type.treeId(), type.bundlePath());
            assertTrue(type.bundleId().startsWith("threehourtour:"));
        }
    }

    @Test
    void everyPackTypeHasACore() {
        for (PackType type : PackType.values()) {
            assertEquals("core_" + type.treeId(), type.corePath());
            assertTrue(type.coreId().startsWith("threehourtour:"));
        }
    }

    @Test
    void bundleCoreAndPackIdsNeverCollide() {
        Set<String> ids = new HashSet<>();
        for (PackType type : PackType.values()) {
            ids.add(type.itemId());
            ids.add(type.bundleId());
            ids.add(type.coreId());
        }
        assertEquals(PackType.values().length * 3, ids.size());
    }
}

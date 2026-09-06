package com.mousebeast.threehourtour.research;

import com.mousebeast.threehourtour.ThreeHourTour;

/**
 * The six research currencies.
 *
 * <p>These ids are load-bearing in two places at once: the generator emits an
 * FTB Quests ItemTask naming {@link #itemId()}, and the research terminal pays
 * that task with whatever it is handed. Until 2026-08-29 the generator emitted
 * the {@code 3ht} namespace — a directory name that no mod owns — so all 206
 * emitted tasks demanded an item that could not exist. The mod id is the only
 * correct namespace, and {@code PackTypeTest} pins it.
 */
public enum PackType {
    TRAWL("trawl", "minecraft:item/dried_kelp"),
    FOUNDRY("foundry", "minecraft:item/brick"),
    FARM("farm", "minecraft:item/wheat"),
    HUSBANDRY("husbandry", "minecraft:item/leather"),
    ARCANE("arcane", "minecraft:item/amethyst_shard"),
    ABYSSAL("abyssal", "minecraft:item/prismarine_shard");

    private final String treeId;
    private final String texture;

    PackType(String treeId, String texture) {
        this.treeId = treeId;
        this.texture = texture;
    }

    /** The pack id as written in {@code docs/research-tree.json}. */
    public String treeId() {
        return treeId;
    }

    /** Registry path, e.g. {@code pack_trawl}. */
    public String itemPath() {
        return "pack_" + treeId;
    }

    /** Fully qualified item id the generator must emit. */
    public String itemId() {
        return ThreeHourTour.MOD_ID + ":" + itemPath();
    }

    /** Vanilla texture this pack borrows; this mod ships no textures of its own. */
    public String texture() {
        return texture;
    }

    /** Registry path of the stage-1 intermediate, e.g. {@code bundle_trawl}. */
    public String bundlePath() {
        return "bundle_" + treeId;
    }

    /** Fully qualified id of the stage-1 intermediate. */
    public String bundleId() {
        return ThreeHourTour.MOD_ID + ":" + bundlePath();
    }

    /** Registry path of the stage-2 intermediate, e.g. {@code core_trawl}. */
    public String corePath() {
        return "core_" + treeId;
    }

    /** Fully qualified id of the stage-2 intermediate. */
    public String coreId() {
        return ThreeHourTour.MOD_ID + ":" + corePath();
    }
}

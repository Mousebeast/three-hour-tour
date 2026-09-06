package com.mousebeast.threehourtour.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Who placed which Physics Assembler, keyed by the WORLD position it was
 * placed at.
 *
 * This is how a ship learns its owner. Sable's assembly hook knows which block
 * moved, never which player pulled the lever - but the placement event does
 * know who put the assembler down, and that is the same person. Recording it
 * here removes the need for any claim step in normal play.
 *
 * Persisted rather than held in memory: a restart between placing an assembler
 * and pulling the lever must not cost the player their ownership.
 */
public class AssemblerOwners extends SavedData {

    public static final String FILE_ID = "threehourtour_assembler_owners";

    private final Map<Long, UUID> byPos = new HashMap<>();

    public static SavedData.Factory<AssemblerOwners> factory() {
        return new SavedData.Factory<>(AssemblerOwners::new, AssemblerOwners::load, null);
    }

    public static AssemblerOwners get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(factory(), FILE_ID);
    }

    public void record(BlockPos pos, UUID owner) {
        byPos.put(pos.asLong(), owner);
        setDirty();
    }

    public UUID ownerOf(BlockPos pos) {
        return byPos.get(pos.asLong());
    }

    public void forget(BlockPos pos) {
        if (byPos.remove(pos.asLong()) != null) setDirty();
    }

    /** Every recorded placement, as world positions. */
    public java.util.Set<BlockPos> positions() {
        java.util.Set<BlockPos> out = new java.util.HashSet<>();
        for (Long packed : byPos.keySet()) out.add(BlockPos.of(packed));
        return out;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        byPos.forEach((pos, owner) -> {
            CompoundTag entry = new CompoundTag();
            entry.putLong("Pos", pos);
            entry.putUUID("Owner", owner);
            list.add(entry);
        });
        tag.put("Assemblers", list);
        return tag;
    }

    public static AssemblerOwners load(CompoundTag tag, HolderLookup.Provider registries) {
        AssemblerOwners owners = new AssemblerOwners();
        ListTag list = tag.getList("Assemblers", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            owners.byPos.put(entry.getLong("Pos"), entry.getUUID("Owner"));
        }
        return owners;
    }
}

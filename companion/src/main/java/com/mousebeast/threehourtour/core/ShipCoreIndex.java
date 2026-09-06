package com.mousebeast.threehourtour.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Player-to-core index, so "does this player have a core?" has an answer.
 *
 * hasCore(owner) means A CORE BLOCK EXISTS for this player. A record whose
 * vesselId is null is a core sitting on something that is not a vessel - a raft
 * not yet assembled, most often, and NEVER a core in vessel plot space
 * (PlotBindingRule is what keeps that pair from being written).
 *
 * A record is only ever forgotten at the moment an operator proves the core is
 * gone, in `/3ht core reissue` - never by a background pass. Proof at the
 * command, rather than a sweep guessing between polls, is what makes "provably
 * has none" a fact rather than an inference.
 */
public class ShipCoreIndex extends SavedData {

    public static final String FILE_ID = "threehourtour_ship_cores";

    private final Map<UUID, CoreRecord> byOwner = new HashMap<>();

    public static SavedData.Factory<ShipCoreIndex> factory() {
        return new SavedData.Factory<>(ShipCoreIndex::new, ShipCoreIndex::load, null);
    }

    /** Always stored on the overworld, so every level sees one index. */
    public static ShipCoreIndex get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(factory(), FILE_ID);
    }

    public void put(CoreRecord record) {
        byOwner.put(record.owner(), record);
        setDirty();
    }

    /**
     * Re-observe where this player's core is in the world, if it can be seen
     * right now. A no-op when the vessel is unloaded - the old snapshot is
     * better than none, and is still correct, because a ship nobody is near has
     * not moved.
     *
     * Goes through CoreRespawn.resolveLive rather than VesselPosition.toWorld
     * directly - a grounded core's position is already a world position, not a
     * plot position, and toWorld would always return empty for it. See
     * resolveLive's doc for why that branch has to live in one shared place.
     */
    public void refreshWorldPos(ServerLevel level, UUID owner) {
        CoreRecord record = byOwner.get(owner);
        if (record == null) return;
        CoreRespawn.resolveLive(level, record)
                .ifPresent(world -> {
                    if (!world.equals(record.lastWorldPos())) {
                        byOwner.put(owner, record.withWorldPos(world));
                        setDirty();
                    }
                });
    }

    public Optional<CoreRecord> forPlayer(UUID owner) {
        return Optional.ofNullable(byOwner.get(owner));
    }

    public boolean hasCore(UUID owner) {
        return byOwner.containsKey(owner);
    }

    public void forget(UUID owner) {
        if (byOwner.remove(owner) != null) setDirty();
    }

    public Collection<CoreRecord> all() {
        return java.util.List.copyOf(byOwner.values());
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (CoreRecord record : byOwner.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Owner", record.owner());
            entry.putString("Dimension", record.dimension().toString());
            entry.putLong("Pos", record.pos().asLong());
            if (record.vesselId() != null) entry.putUUID("Vessel", record.vesselId());
            if (record.lastWorldPos() != null) {
                entry.putDouble("WorldX", record.lastWorldPos().x);
                entry.putDouble("WorldY", record.lastWorldPos().y);
                entry.putDouble("WorldZ", record.lastWorldPos().z);
            }
            list.add(entry);
        }
        tag.put("Cores", list);
        return tag;
    }

    public static ShipCoreIndex load(CompoundTag tag, HolderLookup.Provider registries) {
        ShipCoreIndex index = new ShipCoreIndex();
        ListTag list = tag.getList("Cores", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            net.minecraft.world.phys.Vec3 worldPos = entry.contains("WorldX")
                    ? new net.minecraft.world.phys.Vec3(entry.getDouble("WorldX"),
                            entry.getDouble("WorldY"), entry.getDouble("WorldZ"))
                    : null;
            index.byOwner.put(entry.getUUID("Owner"), new CoreRecord(
                    entry.getUUID("Owner"),
                    ResourceLocation.parse(entry.getString("Dimension")),
                    BlockPos.of(entry.getLong("Pos")),
                    entry.hasUUID("Vessel") ? entry.getUUID("Vessel") : null,
                    worldPos));
        }
        return index;
    }
}

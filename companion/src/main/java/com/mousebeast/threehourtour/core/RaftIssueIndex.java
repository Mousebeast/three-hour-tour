package com.mousebeast.threehourtour.core;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Who has already been given a starting raft.
 *
 * <p><b>This answers "have we issued a raft", never "do they have a ship".</b>
 * Those are different questions and the gap between them is wide: a player who
 * lands on a strange raft and looks around before right-clicking anything is in
 * it, and so is anyone who logs out, crashes, or wanders off for an hour. Ask
 * whether they have a vessel and every relog before assembly places another
 * raft. So the entry is written the moment a raft lands, long before a core
 * exists, and nothing here is ever derived from {@link ShipCoreIndex}.
 *
 * <p><b>World saved-data rather than the player's persisted tag</b>, which is
 * where the welcome-message flag lives. The difference matters because the
 * consequences differ: seeing four lines of chat twice is nothing, while a
 * second raft is a second indestructible core. Keeping the flag in world data
 * means it and the raft blocks are written by the same save, so a crash cannot
 * easily leave one without the other, and an operator clearing playerdata does
 * not hand somebody a second ship.
 */
public class RaftIssueIndex extends SavedData {

    public static final String FILE_ID = "threehourtour_raft_issued";

    private final Set<UUID> issued = new HashSet<>();

    public static SavedData.Factory<RaftIssueIndex> factory() {
        return new SavedData.Factory<>(RaftIssueIndex::new, RaftIssueIndex::load, null);
    }

    /** Overworld-scoped, like the other two indexes: one answer per save. */
    public static RaftIssueIndex get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(factory(), FILE_ID);
    }

    public boolean wasIssued(UUID player) {
        return issued.contains(player);
    }

    /** Called only after a raft has actually landed. See RaftPlacement. */
    public void markIssued(UUID player) {
        if (issued.add(player)) setDirty();
    }

    /**
     * Makes the player eligible again. The operator route for someone whose
     * raft went before they ever assembled it -- there is no core to reason
     * about in that case, so `/3ht core reissue` has nothing to restore.
     */
    public void clear(UUID player) {
        if (issued.remove(player)) setDirty();
    }

    public Set<UUID> all() {
        return Set.copyOf(issued);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (UUID player : issued) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", player);
            list.add(entry);
        }
        tag.put("Issued", list);
        return tag;
    }

    public static RaftIssueIndex load(CompoundTag tag, HolderLookup.Provider registries) {
        RaftIssueIndex index = new RaftIssueIndex();
        ListTag list = tag.getList("Issued", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            index.issued.add(list.getCompound(i).getUUID("Player"));
        }
        return index;
    }
}

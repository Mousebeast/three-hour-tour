package com.mousebeast.threehourtour.core;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ShipCoreIndex.save/load is the branch's only persistence path and its only
 * irreversible failure mode: a bug here does not throw, it quietly loses a
 * player's core on the next server restart. Neither method dereferences its
 * HolderLookup.Provider argument, so these tests pass null for it rather than
 * bootstrapping Minecraft's registry access - the same approach CoreRespawnTest
 * already relies on for ServerLevel.
 */
class ShipCoreIndexTest {

    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID VESSEL = UUID.fromString("00000000-0000-0000-0000-0000000000bb");
    private static final ResourceLocation DIM = ResourceLocation.withDefaultNamespace("overworld");

    /** Round-trips a record through save/load via a fresh index, same as a server restart would. */
    private static CoreRecord roundTrip(CoreRecord record) {
        ShipCoreIndex before = new ShipCoreIndex();
        before.put(record);
        CompoundTag tag = before.save(new CompoundTag(), null);
        ShipCoreIndex after = ShipCoreIndex.load(tag, null);
        return after.forPlayer(record.owner()).orElseThrow(
                () -> new AssertionError("record vanished across save/load"));
    }

    @Test void aBoundCoreWithAWorldPosSurvivesTheRoundTripInFull() {
        CoreRecord record = new CoreRecord(OWNER, DIM, new BlockPos(1, 2, 3), VESSEL,
                new Vec3(10.5, 20.5, -30.5));

        CoreRecord loaded = roundTrip(record);

        assertEquals(OWNER, loaded.owner());
        assertEquals(DIM, loaded.dimension());
        assertEquals(new BlockPos(1, 2, 3), loaded.pos());
        assertEquals(VESSEL, loaded.vesselId());
        assertEquals(new Vec3(10.5, 20.5, -30.5), loaded.lastWorldPos());
        assertTrue(loaded.isBound());
    }

    @Test void aNullWorldPosStaysNullRatherThanBecomingTheOrigin() {
        // The real-world failure mode this guards against: if load ever fell
        // back to reading absent WorldX/Y/Z as 0.0, a core that has never been
        // observed would silently look like it was observed at (0,0,0),
        // corrupting CoreRespawn's LIVE vs SNAPSHOT vs WORLD_SPAWN choice.
        CoreRecord record = new CoreRecord(OWNER, DIM, new BlockPos(5, 6, 7), VESSEL);

        CoreRecord loaded = roundTrip(record);

        assertNull(loaded.lastWorldPos());
        assertFalse(loaded.hasWorldPos());
    }

    @Test void aGroundedCoreRoundTripsWithNoVesselId() {
        CoreRecord record = new CoreRecord(OWNER, DIM, new BlockPos(8, 9, 10), null);

        CoreRecord loaded = roundTrip(record);

        assertNull(loaded.vesselId());
        assertFalse(loaded.isBound());
    }

    /**
     * The case that matters: a real save file written before this branch
     * added WorldX/Y/Z. Hand-build the OLD format - Owner, Dimension, Pos, and
     * optionally Vessel, with no WorldX/WorldY/WorldZ keys at all - and confirm
     * it loads cleanly with lastWorldPos() null rather than throwing or
     * fabricating a position from missing NBT keys.
     */
    @Test void anOldFormatEntryWithNoWorldPosKeysLoadsCleanlyAsUnobserved() {
        CompoundTag entry = new CompoundTag();
        entry.putUUID("Owner", OWNER);
        entry.putString("Dimension", DIM.toString());
        entry.putLong("Pos", new BlockPos(11, 12, 13).asLong());
        entry.putUUID("Vessel", VESSEL);
        // Deliberately no WorldX/WorldY/WorldZ - this is what every save file
        // written before this branch actually contains.

        ListTag list = new ListTag();
        list.add(entry);
        CompoundTag tag = new CompoundTag();
        tag.put("Cores", list);

        ShipCoreIndex index = ShipCoreIndex.load(tag, null);
        CoreRecord loaded = index.forPlayer(OWNER).orElseThrow();

        assertEquals(new BlockPos(11, 12, 13), loaded.pos());
        assertEquals(VESSEL, loaded.vesselId());
        assertNull(loaded.lastWorldPos());
        assertFalse(loaded.hasWorldPos());
    }

    /** Same old-format case, but grounded (no Vessel key either) - the oldest possible save shape. */
    @Test void anOldFormatGroundedEntryWithNoVesselOrWorldPosKeysLoadsCleanly() {
        CompoundTag entry = new CompoundTag();
        entry.putUUID("Owner", OWNER);
        entry.putString("Dimension", DIM.toString());
        entry.putLong("Pos", new BlockPos(14, 15, 16).asLong());

        ListTag list = new ListTag();
        list.add(entry);
        CompoundTag tag = new CompoundTag();
        tag.put("Cores", list);

        ShipCoreIndex index = ShipCoreIndex.load(tag, null);
        CoreRecord loaded = index.forPlayer(OWNER).orElseThrow();

        assertNull(loaded.vesselId());
        assertNull(loaded.lastWorldPos());
    }
}

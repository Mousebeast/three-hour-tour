package com.mousebeast.threehourtour.core;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CoreRespawnTest {

    @Test void aLivePoseWins() {
        assertEquals(CoreRespawn.Source.LIVE, CoreRespawn.chooseSource(true, true));
    }

    @Test void aLivePoseWinsEvenWithNoSnapshot() {
        assertEquals(CoreRespawn.Source.LIVE, CoreRespawn.chooseSource(true, false));
    }

    @Test void theSnapshotIsUsedWhenTheVesselCannotBeResolved() {
        assertEquals(CoreRespawn.Source.SNAPSHOT, CoreRespawn.chooseSource(false, true));
    }

    @Test void worldSpawnIsTheLastResort() {
        // Only reachable when the vessel is unresolvable AND was never once
        // observed - a corrupt save. Spec L1-13 says the player is told.
        assertEquals(CoreRespawn.Source.WORLD_SPAWN, CoreRespawn.chooseSource(false, false));
    }

    @Test void aGroundedCoreResolvesFromItsOwnPositionWithoutTheVesselTransform() {
        // Regression for fix round 1, finding 2: a grounded core's stored pos
        // IS a world position already, so it must never be handed to
        // VesselPosition.toWorld (that always answers empty for it, stranding
        // the owner at world spawn forever). `level` is passed null here and
        // must never be dereferenced on the grounded branch - if it ever is,
        // this test fails with an NPE instead of quietly passing.
        UUID owner = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
        ResourceLocation dim = ResourceLocation.withDefaultNamespace("overworld");
        BlockPos pos = new BlockPos(100, 64, -200);
        CoreRecord grounded = new CoreRecord(owner, dim, pos, null);

        Optional<Vec3> live = CoreRespawn.resolveLive(null, grounded);

        assertTrue(live.isPresent(), "a grounded core must always resolve a live position");
        assertEquals(Vec3.atBottomCenterOf(pos.above()), live.get());
    }

    @Test void aBedDoesNotTakeTheSpawnPointFromACoreOwner() {
        assertTrue(CoreRespawn.shouldOverrideBedSpawn(true, false));
    }

    @Test void aBedWorksNormallyForAPlayerWithNoCore() {
        // Before activation the raft is not a ship yet and there is no core to
        // respawn at. Taking beds away then would just be cruel.
        assertFalse(CoreRespawn.shouldOverrideBedSpawn(false, false));
    }

    @Test void anOperatorForcedSpawnAlwaysWins() {
        // /spawnpoint and respawn anchors are how a stuck player gets rescued.
        assertFalse(CoreRespawn.shouldOverrideBedSpawn(true, true));
    }

    @Test void anOperatorForcedSpawnWinsWithNoCoreToo() {
        assertFalse(CoreRespawn.shouldOverrideBedSpawn(false, true));
    }
}

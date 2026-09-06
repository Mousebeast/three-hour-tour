package com.mousebeast.threehourtour.scoop;

import com.mousebeast.threehourtour.ThreeHourTour;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MeshSlotTest {

    @Test
    void everyMeshTierIsAcceptedByItsOwnItemPath() {
        for (MeshTier tier : MeshTier.values()) {
            assertTrue(MeshSlot.isMesh(ThreeHourTour.MOD_ID, tier.itemPath()),
                    tier + " must be installable in the mesh slot");
        }
    }

    @Test
    void ourOwnNonMeshItemsAreRejected() {
        // The scoop's own BlockItem shares our namespace. If the namespace
        // check alone were the rule, a player could install a scoop inside a
        // scoop and the tier lookup would return nothing at emit time.
        assertFalse(MeshSlot.isMesh(ThreeHourTour.MOD_ID, "sea_scoop"));
        assertFalse(MeshSlot.isMesh(ThreeHourTour.MOD_ID, "ship_core"));
    }

    @Test
    void anotherModsItemNamedLikeAMeshIsRejected() {
        // Path-only matching would let any mod ship a "twine_mesh" that runs
        // our scoop for free.
        assertFalse(MeshSlot.isMesh("minecraft", "twine_mesh"));
        assertFalse(MeshSlot.isMesh("create", "chain_mesh"));
    }

    @Test
    void nullsAndBlanksAreRejectedRatherThanThrowing() {
        assertFalse(MeshSlot.isMesh(null, "twine_mesh"));
        assertFalse(MeshSlot.isMesh(ThreeHourTour.MOD_ID, null));
        assertFalse(MeshSlot.isMesh(null, null));
        assertFalse(MeshSlot.isMesh("", ""));
    }
}

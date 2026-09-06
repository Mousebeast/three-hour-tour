package com.mousebeast.threehourtour;

import com.mousebeast.threehourtour.vessel.VesselLookup;
import com.mousebeast.threehourtour.vessel.VesselRef;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ThreeHourTour.MOD_ID)
@PrefixGameTestTemplate(false)
public class VesselLookupGameTest {

    /** Ordinary world positions must never report a vessel. */
    @GameTest(template = "empty")
    public static void groundIsNotAVessel(GameTestHelper helper) {
        BlockPos abs = helper.absolutePos(BlockPos.ZERO);
        if (VesselLookup.isOnVessel(helper.getLevel(), abs)) {
            helper.fail("plain ground reported as a vessel at " + abs);
        }
        helper.succeed();
    }

    /** Null arguments must be handled, not thrown. */
    @GameTest(template = "empty")
    public static void nullsAreSafe(GameTestHelper helper) {
        if (VesselLookup.vesselAt(null, BlockPos.ZERO) != null) helper.fail("null level returned a vessel");
        if (VesselLookup.vesselAt(helper.getLevel(), null) != null) helper.fail("null pos returned a vessel");
        helper.succeed();
    }

    /**
     * A position inside Sable's plot region must resolve to a vessel when one
     * exists there, and to null when it does not. We cannot assemble a real
     * vessel from GameTest, so this asserts the negative case at plot-region
     * coordinates - which is the case that would wrongly pass if someone
     * reimplemented this as a dimension comparison.
     */
    @GameTest(template = "empty")
    public static void plotRegionWithoutVesselIsNull(GameTestHelper helper) {
        BlockPos farOut = new BlockPos(20_481_000, 129, 20_481_000);
        VesselRef ref = VesselLookup.vesselAt(helper.getLevel(), farOut);
        if (ref != null) {
            helper.fail("empty plot coordinate reported vessel " + ref);
        }
        helper.succeed();
    }
}

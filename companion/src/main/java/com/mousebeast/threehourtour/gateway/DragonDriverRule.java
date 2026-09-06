package com.mousebeast.threehourtour.gateway;

/**
 * The decision half of the Ender Dragon driver, kept free of Minecraft types
 * so it can be tested without a level.
 *
 * A dragon summoned outside the End initialises to HOVERING and never leaves
 * it -- EnderDragonPhaseManager's constructor sets it and nothing off-End
 * moves it on. The obvious fix, setting HOLDING_PATTERN, is wrong:
 * EnderDragon.findClosestNode() builds the 24-point flight ring around world
 * coordinates (0,0), with the fight origin nowhere in the expression, so a
 * dragon in HOLDING_PATTERN or STRAFE_PLAYER flies toward world origin and
 * Gateways fails the gate when it passes the leash.
 *
 * CHARGING_PLAYER is the one flying phase that touches no node -- it flies at
 * a Vec3 and reverts to HOLDING_PATTERN ten ticks after arriving. So the
 * driver re-issues a charge whenever the dragon is not charging, and also
 * whenever it has drifted past the leash while charging.
 */
public final class DragonDriverRule {

    /** Persistent-data key marking a dragon as belonging to one of our gates. */
    public static final String MARKER = "3ht_gate_dragon";

    private DragonDriverRule() {
    }

    public static boolean shouldRecharge(boolean charging, double distToGateSqr,
                                         double leashSqr) {
        return !charging || distToGateSqr > leashSqr;
    }
}

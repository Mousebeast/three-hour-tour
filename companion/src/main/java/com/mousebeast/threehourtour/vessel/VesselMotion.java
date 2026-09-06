package com.mousebeast.threehourtour.vessel;

import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import org.joml.Vector3dc;

/**
 * Vessel movement, derived rather than read.
 *
 * Sable publishes no velocity accessor. It does keep the current pose and the
 * previous tick's pose, so speed is the distance between them. Sable calls
 * SubLevel.updateLastPose() itself each tick; we only read.
 *
 * Pose3dc lives in the nested sable-companion-common jar, NOT the outer Sable
 * jar - see lessons-learned.md, 2026-08-20.
 */
public final class VesselMotion {

    private VesselMotion() {}

    /** Blocks travelled in one tick. Null poses read as stationary. */
    public static double speedBetween(Vector3dc current, Vector3dc last) {
        if (current == null || last == null) {
            return 0.0;
        }
        double dx = current.x() - last.x();
        double dy = current.y() - last.y();
        double dz = current.z() - last.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Blocks travelled in one tick by this vessel. */
    public static double speedOf(SubLevel sub) {
        if (sub == null || sub.isRemoved()) {
            return 0.0;
        }
        Pose3dc now = sub.logicalPose();
        Pose3dc then = sub.lastPose();
        if (now == null || then == null) {
            return 0.0;
        }
        return speedBetween(now.position(), then.position());
    }
}

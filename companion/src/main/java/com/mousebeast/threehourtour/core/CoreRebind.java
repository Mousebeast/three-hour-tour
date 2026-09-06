package com.mousebeast.threehourtour.core;

import java.util.UUID;

/**
 * What a core's binding becomes after Sable has moved it.
 *
 * The core is the source of truth for "your ship"; the vessel id is a cache.
 * Whichever fragment of a split carries the core IS the ship, by definition -
 * which is why this is a plain overwrite and not a comparison against some
 * remembered "real" vessel. See the spec, §3.
 */
public final class CoreRebind {
    private CoreRebind() {}

    /**
     * @param current   the binding before the move, or null for an unclaimed core
     * @param vesselNow the vessel at the core's new position, or null if none
     */
    public static CoreBinding after(CoreBinding current, UUID vesselNow) {
        if (current == null) return null;
        return vesselNow == null ? current.grounded() : current.boundTo(vesselNow);
    }
}

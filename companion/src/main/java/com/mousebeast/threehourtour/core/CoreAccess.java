package com.mousebeast.threehourtour.core;

import java.util.UUID;

/** Who may use a core, and by extension who gets a vessel's on-ship bonuses. */
public final class CoreAccess {
    private CoreAccess() {}

    public static boolean mayUse(CoreBinding binding, UUID player, TeamResolver teams) {
        if (binding == null || player == null) return false;
        if (player.equals(binding.owner())) return true;
        return teams != null && teams.sameTeam(binding.owner(), player);
    }
}

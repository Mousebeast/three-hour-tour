package com.mousebeast.threehourtour.core;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;

import java.util.UUID;

/**
 * FTB Teams backing for {@link TeamResolver}.
 *
 * FTB Teams is an OPTIONAL dependency: if it is absent or its manager has not
 * loaded yet, nobody is anybody's teammate, which fails closed - a stranger is
 * never mistaken for crew.
 */
public final class FtbTeamResolver implements TeamResolver {

    public static final FtbTeamResolver INSTANCE = new FtbTeamResolver();

    private FtbTeamResolver() {}

    @Override
    public boolean sameTeam(UUID a, UUID b) {
        if (a == null || b == null) return false;
        if (a.equals(b)) return true;
        try {
            if (!FTBTeamsAPI.api().isManagerLoaded()) return false;
            return FTBTeamsAPI.api().getManager().arePlayersInSameTeam(a, b);
        } catch (Throwable ignored) {
            // FTB Teams absent at runtime. Fail closed, never loud-crash a
            // predicate that runs on every placement.
            return false;
        }
    }
}

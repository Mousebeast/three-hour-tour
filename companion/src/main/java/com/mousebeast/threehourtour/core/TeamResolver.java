package com.mousebeast.threehourtour.core;

import java.util.UUID;

/**
 * "Are these two players on the same team?"
 *
 * One method, so the access rule is testable without a server and a change of
 * team mod lands in exactly one class.
 */
@FunctionalInterface
public interface TeamResolver {
    boolean sameTeam(UUID a, UUID b);
}

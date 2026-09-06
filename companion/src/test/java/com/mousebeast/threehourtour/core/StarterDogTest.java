package com.mousebeast.threehourtour.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The dog's id is written as two string literals so that
 * {@code tools/audit-entity-ids.py} can read it out of the source and check it
 * against the staged jars' class files. That audit is the only thing standing
 * between a mod update renaming the entity and every new player quietly
 * arriving without a dog.
 *
 * <p>The cost of writing the namespace out rather than reusing {@code MOD_ID}
 * is that the two can drift. This is that cost paid.
 */
class StarterDogTest {

    @Test void theIdsNamespaceIsTheModItIsGuardedBy() {
        assertEquals(StarterDog.MOD_ID, StarterDog.DOG.getNamespace(),
                "StarterDog checks ModList for one mod id and spawns an entity from another; "
                + "the ModList guard would then be checking the wrong mod");
    }

    @Test void theIdIsTheOneTheAuditCanSee() {
        // Belt and braces on the audit's own regex: if this ever stops being a
        // plain lowercase path, the scanner silently stops matching it and the
        // check goes quiet rather than failing.
        assertTrue(StarterDog.DOG.getPath().matches("[a-z0-9_./-]+"),
                "an id the audit's scanner cannot match is an unchecked id");
    }
}

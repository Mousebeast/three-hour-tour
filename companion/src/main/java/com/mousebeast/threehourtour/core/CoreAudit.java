package com.mousebeast.threehourtour.core;

import com.mousebeast.threehourtour.ThreeHourTour;
import com.mousebeast.threehourtour.core.CoreAuditRule.Owner;
import com.mousebeast.threehourtour.core.CoreAuditRule.Verdict;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the ship-core index against the world and, on request, reconciles it.
 *
 * <p><b>Why this exists.</b> The index is a cache; the core block carries its
 * own binding and is the truth. A restore from backup, a crash between placing
 * a core and saving, or the activation overwrite fixed on 2026-09-04 having
 * already fired can leave the two disagreeing. The activation gate reads only
 * the index and refuses on doubt, so the failure is a player locked out of
 * assembling a ship they are standing on - the right way round, and until now
 * with no operator tool behind it.
 *
 * <p><b>What it can and cannot see.</b> It walks the index and checks each
 * record against the world. It does <b>not</b> discover cores the index has
 * never heard of: nothing in the mod can enumerate every vessel, and a core on
 * an unloaded ship twenty million blocks out is not findable by any sweep we
 * could write. A core the index has lost entirely is recovered by standing on
 * it and running {@code /3ht core claim}, which is why that command exists and
 * why this one reports rather than hunts.
 *
 * <p><b>Why it is a command and not a background pass.</b> The same reason
 * {@link ShipCoreIndex} gives for never forgetting a record on a timer: a
 * position that cannot be read proves nothing, and a sweep polling between
 * chunk loads would eventually read "no core" for a ship that simply was not
 * loaded, and delete the record of it. Every repair here happens at an
 * operator's keystroke, against a position that was readable at that moment.
 *
 * <p>The decision itself is in {@link CoreAuditRule}, pure and exhaustively
 * tested. This file only supplies the readings and performs what the rule
 * allows.
 */
public final class CoreAudit {
    private CoreAudit() {}

    /** One record, what the world said about it, and what that means. */
    public record Finding(CoreRecord record, Verdict verdict, String detail) {}

    /**
     * Every record, read against the world. Read-only: nothing here writes.
     *
     * <p>Findings come back in a fixed order - worst first - so an operator
     * reading a wall of chat sees the conflicts before the drift.
     */
    public static List<Finding> inspect(MinecraftServer server) {
        List<Finding> findings = new ArrayList<>();
        ShipCoreIndex index = ShipCoreIndex.get(server.overworld());

        for (CoreRecord record : index.all()) {
            ServerLevel level = server.getLevel(
                    ResourceKey.create(Registries.DIMENSION, record.dimension()));
            if (level == null) {
                findings.add(new Finding(record, Verdict.UNKNOWN,
                        "its dimension " + record.dimension() + " is not loaded"));
                continue;
            }

            boolean verifiable = CoreEvidence.verifiable(level, record.pos());
            boolean stands = CoreEvidence.coreStandsAt(level, record.pos());
            CoreBinding binding = ShipCoreBlockEntity.at(level, record.pos())
                    .map(ShipCoreBlockEntity::getBinding).orElse(null);

            Owner owner;
            if (binding == null || binding.owner() == null) {
                owner = Owner.NONE;
            } else if (binding.owner().equals(record.owner())) {
                owner = Owner.SAME;
            } else {
                owner = Owner.OTHER;
            }
            boolean vesselAgrees = binding != null
                    && Objects.equals(record.vesselId(), binding.vesselId());

            Verdict verdict = CoreAuditRule.decide(verifiable, stands, owner, vesselAgrees);
            findings.add(new Finding(record, verdict, describe(verdict, record, binding)));
        }

        findings.sort((a, b) -> Integer.compare(severity(a.verdict()), severity(b.verdict())));
        return findings;
    }

    /**
     * Apply the repairs the rule allows, and report how many were made.
     *
     * <p>Every finding is re-read against the world before it is acted on. The
     * list may have been produced a moment earlier and a ship can move, unload
     * or be assembled between the two calls; acting on a stale reading is
     * exactly the failure a background sweep would have.
     */
    public static int repair(MinecraftServer server, List<Finding> stale) {
        ShipCoreIndex index = ShipCoreIndex.get(server.overworld());
        int repaired = 0;

        for (Finding old : stale) {
            if (!old.verdict().repairable()) continue;

            Finding now = reinspect(server, old.record());
            if (now == null || now.verdict() != old.verdict()) {
                ThreeHourTour.LOG.warn(
                        "core audit: {} changed between the report and the fix ({} -> {}); "
                        + "left alone", old.record().owner(), old.verdict(),
                        now == null ? "gone from the index" : now.verdict());
                continue;
            }

            CoreRecord record = now.record();
            ServerLevel level = server.getLevel(
                    ResourceKey.create(Registries.DIMENSION, record.dimension()));
            if (level == null) continue;

            switch (now.verdict()) {
                case STALE_RECORD -> {
                    index.forget(record.owner());
                    ThreeHourTour.LOG.info("core audit: forgot the ghost record for {} at {} in {} "
                            + "-- the position was readable and held no core",
                            record.owner(), record.pos(), record.dimension());
                    repaired++;
                }
                case UNCLAIMED_CORE -> {
                    ShipCoreBlockEntity core = ShipCoreBlockEntity.at(level, record.pos())
                            .orElse(null);
                    if (core == null) continue;
                    core.setBinding(new CoreBinding(record.owner(), record.vesselId()));
                    ThreeHourTour.LOG.info("core audit: stamped {} onto the unbound core at {} in {}",
                            record.owner(), record.pos(), record.dimension());
                    repaired++;
                }
                case VESSEL_DRIFT -> {
                    CoreBinding binding = ShipCoreBlockEntity.at(level, record.pos())
                            .map(ShipCoreBlockEntity::getBinding).orElse(null);
                    if (binding == null) continue;
                    index.put(new CoreRecord(record.owner(), record.dimension(), record.pos(),
                            binding.vesselId(), record.lastWorldPos()));
                    ThreeHourTour.LOG.info("core audit: {}'s record now names vessel {}, "
                            + "which is what the block says (was {})",
                            record.owner(), binding.vesselId(), record.vesselId());
                    repaired++;
                }
                default -> { }
            }
        }
        return repaired;
    }

    /** The same reading, taken again for one owner. Null if the record is gone. */
    private static Finding reinspect(MinecraftServer server, CoreRecord record) {
        return inspect(server).stream()
                .filter(f -> f.record().owner().equals(record.owner()))
                .findFirst().orElse(null);
    }

    private static int severity(Verdict verdict) {
        return switch (verdict) {
            case OWNER_CONFLICT -> 0;
            case STALE_RECORD -> 1;
            case UNCLAIMED_CORE -> 2;
            case VESSEL_DRIFT -> 3;
            case UNKNOWN -> 4;
            case AGREES -> 5;
        };
    }

    private static String describe(Verdict verdict, CoreRecord record, CoreBinding binding) {
        return switch (verdict) {
            case UNKNOWN -> "the position could not be read -- an unloaded ship has not "
                    + "stopped existing, so nothing was changed";
            case STALE_RECORD -> "the position was readable and held no core. This record is "
                    + "what the activation gate is refusing on";
            case UNCLAIMED_CORE -> "a core stands there carrying no owner at all. The index is "
                    + "the only surviving evidence of whose it is";
            case OWNER_CONFLICT -> "the core there belongs to " + binding.owner()
                    + ". Two claims on one block that cannot be broken -- decide this by hand";
            case VESSEL_DRIFT -> "the core says vessel " + binding.vesselId()
                    + ", the index says " + record.vesselId() + ". Ordinary drift after a split";
            case AGREES -> "agrees with the world";
        };
    }

    /** A player's name if the server still remembers it, else their uuid. */
    public static String nameOf(MinecraftServer server, UUID owner) {
        try {
            return Optional.ofNullable(server.getProfileCache())
                    .flatMap(cache -> cache.get(owner))
                    .map(profile -> profile.getName())
                    .orElse(owner.toString());
        } catch (Exception e) {
            return owner.toString();
        }
    }
}

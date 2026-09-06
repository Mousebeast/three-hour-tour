package com.mousebeast.threehourtour.core;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mousebeast.threehourtour.ThreeHourTour;
import com.mousebeast.threehourtour.vessel.VesselLookup;
import com.mousebeast.threehourtour.vessel.VesselPosition;
import com.mousebeast.threehourtour.vessel.VesselRef;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Optional;
import java.util.UUID;

/**
 * `/3ht core status|claim|reissue|where`, plus the operator override form
 * `/3ht core reissue <player> here`.
 *
 * There is deliberately no in-fiction route to a second core. Re-issue is an
 * operator command and it refuses any player the index still knows about, so
 * the one-vessel pillar holds even when a save goes wrong.
 *
 * The `here` override exists because creative mode and operator action can
 * put a save in a state the invariants do not model: a vessel deleted
 * outright leaves a recorded core position that can never be proven gone, so
 * the ordinary path refuses forever. `here` lifts only the "prove it's gone"
 * requirement and re-issues at the operator's position instead - it never
 * lifts the one-core rule, and still refuses if a ship core is found standing
 * at the recorded position.
 *
 * The two decisions worth getting right are not made in this file. Whether to
 * place a core at all lives in {@link ReissueRule}, and whether a position and
 * a vessel reference may be written together lives in {@link PlotBindingRule} -
 * both pure, both unit-tested, because a duplicate core is permanent and an
 * inconsistent record strands its owner 20.4M blocks out.
 */
@EventBusSubscriber(modid = ThreeHourTour.MOD_ID)
public final class CoreCommands {
    private CoreCommands() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> core = Commands.literal("core")
                .then(Commands.literal("status")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> status(ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("claim")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> claim(ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("reissue")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> reissue(ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player"), false))
                                .then(Commands.literal("here")
                                        .executes(ctx -> reissue(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"), true)))))
                .then(Commands.literal("where")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> where(ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("audit")
                        .executes(ctx -> audit(ctx.getSource(), false))
                        .then(Commands.literal("fix")
                                .executes(ctx -> audit(ctx.getSource(), true))));

        event.getDispatcher().register(Commands.literal("3ht")
                .requires(source -> source.hasPermission(2))
                .then(core));
    }

    private static int status(CommandSourceStack source, ServerPlayer player) {
        ShipCoreIndex index = ShipCoreIndex.get(source.getServer().overworld());
        CoreRecord record = index.forPlayer(player.getUUID()).orElse(null);
        if (record == null) {
            source.sendSuccess(() -> Component.literal(
                    player.getGameProfile().getName() + " has no ship core."), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                player.getGameProfile().getName() + "'s core is at " + record.pos()
                        + " in " + record.dimension()
                        + (record.isBound() ? ", vessel " + record.vesselId()
                                            : ", not on a vessel (grounded)")), false);
        return 1;
    }

    /**
     * `/3ht core audit` reads every index record against the world; `fix`
     * applies the repairs {@link CoreAuditRule} allows.
     *
     * <p>Read-only by default because three of the six verdicts are things an
     * operator must decide rather than be told about, and because the one that
     * deletes a record deletes the only evidence a player ever had a ship.
     *
     * <p>It reports on the index it has. It cannot find a core the index has
     * never heard of -- nothing in the mod can enumerate every vessel, and a
     * core on an unloaded ship 20.4M blocks out is beyond any sweep we could
     * write. That case is recovered by standing on the core and running
     * `/3ht core claim`.
     */
    private static int audit(CommandSourceStack source, boolean fix) {
        var server = source.getServer();
        var findings = CoreAudit.inspect(server);

        if (findings.isEmpty()) {
            source.sendSuccess(() -> Component.literal(
                    "The ship-core index is empty. Nothing to reconcile."), false);
            return 0;
        }

        long problems = findings.stream().filter(f -> f.verdict().isFinding()).count();
        if (problems == 0) {
            source.sendSuccess(() -> Component.literal(
                    findings.size() + " core record(s), all agreeing with the world."), false);
            return 1;
        }

        StringBuilder out = new StringBuilder(
                findings.size() + " core record(s), " + problems + " needing a look:");
        for (CoreAudit.Finding f : findings) {
            if (!f.verdict().isFinding()) continue;
            String who = CoreAudit.nameOf(server, f.record().owner());
            out.append("\n  ").append(who).append(" -- ").append(f.detail());
            // The operator cannot copy text out of Minecraft chat, so the log
            // is the only channel they can read from during a session. Same
            // argument as `where`.
            ThreeHourTour.LOG.info("core audit: player={} verdict={} pos={} dim={} detail={}",
                    who, f.verdict(), f.record().pos(), f.record().dimension(), f.detail());
        }

        if (!fix) {
            long repairable = findings.stream().filter(f -> f.verdict().repairable()).count();
            out.append(repairable > 0
                    ? "\n\n" + repairable + " of these can be repaired: run `/3ht core audit fix`."
                    : "\n\nNone of these can be repaired automatically.");
            source.sendSuccess(() -> Component.literal(out.toString()), false);
            return (int) problems;
        }

        int repaired = CoreAudit.repair(server, findings);
        source.sendSuccess(() -> Component.literal(out
                + "\n\nRepaired " + repaired + ". Anything left is a decision, not a defect."), true);
        return repaired;
    }

    /**
     * Prints both coordinate spaces for a player's core, and the caller's own
     * position, so the transform can be checked by eye against the ship the
     * operator is standing on.
     */
    private static int where(CommandSourceStack source, ServerPlayer player) {
        ShipCoreIndex index = ShipCoreIndex.get(source.getServer().overworld());
        CoreRecord record = index.forPlayer(player.getUUID()).orElse(null);
        if (record == null) {
            source.sendFailure(Component.literal(
                    player.getGameProfile().getName() + " has no ship core."));
            return 0;
        }

        ServerLevel level = source.getServer().getLevel(
                ResourceKey.create(Registries.DIMENSION, record.dimension()));
        if (level == null) {
            source.sendFailure(Component.literal(
                    "The core's dimension " + record.dimension() + " is not loaded."));
            return 0;
        }

        BlockPos pos = record.pos();
        // CoreRespawn.resolveLive, not VesselPosition.toWorld directly: a
        // grounded core's stored pos is already a world position, and toWorld
        // would always answer empty for it, printing UNRESOLVED for a core that
        // respawn resolves fine. resolveLive also applies the .above() offset
        // respawn actually teleports to, which a direct toWorld call would
        // omit. See resolveLive's doc for why the branch lives in one place.
        String resolved = CoreRespawn.resolveLive(level, record)
                .map(v -> String.format("%.2f, %.2f, %.2f", v.x, v.y, v.z))
                .orElse("UNRESOLVED (vessel not loaded or gone)");

        String vesselLabel = record.isBound() ? record.vesselId().toString() : "none (grounded)";
        Vec3 you = source.getPosition();

        source.sendSuccess(() -> Component.literal(
                player.getGameProfile().getName() + "'s core"
                        + "\n  plot pos : " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()
                        + "\n  world pos: " + resolved
                        + "\n  vessel   : " + vesselLabel
                        + "\n  you are  : " + you), false);

        // Same values as the chat message above, on one greppable line: the
        // operator running this cannot copy text out of Minecraft chat, so
        // the log is the only channel they can actually read from during a
        // verification session.
        ThreeHourTour.LOG.info(
                "core where: player={} plotPos=[{}, {}, {}] worldPos={} vessel={} operatorPos={}",
                player.getGameProfile().getName(), pos.getX(), pos.getY(), pos.getZ(),
                resolved, vesselLabel, you);
        return 1;
    }

    /** Stamps ownership on the core block the operator is standing on top of. */
    private static int claim(CommandSourceStack source, ServerPlayer player) {
        ServerLevel level = source.getLevel();

        // The operator's own position is world space. On a vessel, the core
        // underfoot lives in plot space, so it has to be resolved through the
        // vessel transform first. On land there is no vessel to resolve into,
        // and the operator's world-space position IS the position to check -
        // that fallback is what makes claiming a grounded core still work.
        Optional<BlockPos> plot = VesselPosition.toPlot(level, source.getPosition());
        boolean fromPlot = plot.isPresent();
        BlockPos pos = plot.map(BlockPos::below)
                .orElseGet(() -> BlockPos.containing(source.getPosition()).below());

        // Level.getBlockEntity goes through the generating getChunkAt, and pos
        // can be a plot coordinate ~20.4M out. Low risk here - it came from a
        // toPlot that just resolved, so the plot is live - but it was the last
        // unguarded chunk fetch on this path, and the cost of being wrong is
        // vanilla generating terrain in the plot region on the main thread.
        if (!chunkIsAvailable(level, pos)) {
            source.sendFailure(Component.literal("The chunk at " + pos + " in "
                    + level.dimension().location() + " is not loaded, and neither vanilla nor "
                    + "Sable resolves it. This command will not generate terrain there. "
                    + "Nothing was recorded."));
            return 0;
        }

        ShipCoreBlockEntity core = ShipCoreBlockEntity.at(level, pos).orElse(null);
        if (core == null) {
            source.sendFailure(Component.literal("No ship core at " + pos
                    + ". Stand on the core and run this again."));
            return 0;
        }
        ShipCoreIndex index = ShipCoreIndex.get(source.getServer().overworld());
        if (index.hasCore(player.getUUID())) {
            source.sendFailure(Component.literal(player.getGameProfile().getName()
                    + " already has a ship core. One core, one ship."));
            return 0;
        }

        VesselRef vessel = VesselLookup.vesselAt(level, pos);
        // toPlot and vesselAt do not ask the same question - see PlotBindingRule -
        // and the .below() above can step a position out of the plot that
        // toPlot just matched. Writing the disagreement would produce a record
        // that reads as grounded and teleports its owner 20.4M blocks out.
        if (PlotBindingRule.mustRefuse(fromPlot, vessel != null)) {
            source.sendFailure(Component.literal("The core at " + pos
                    + " resolved to vessel plot space, but no vessel could be identified there. "
                    + "Recording that pair would strand " + player.getGameProfile().getName()
                    + " ~20.4M blocks out on their next death. Nothing was recorded. "
                    + "Stand squarely on the core (not on its edge) and run this again."));
            return 0;
        }

        CoreBinding binding = new CoreBinding(player.getUUID(),
                vessel == null ? null : vessel.id());
        core.setBinding(binding);
        index.put(new CoreRecord(player.getUUID(), level.dimension().location(), pos,
                binding.vesselId()));

        source.sendSuccess(() -> Component.literal("Bound the core at " + pos + " to "
                + player.getGameProfile().getName()), true);
        return 1;
    }

    /**
     * Whether the chunk containing {@code pos} can be read and written without
     * vanilla generating terrain for it.
     *
     * Never uses the blocking, generating {@code getChunk(pos)} form. A core
     * position can be a stale plot coordinate ~20.4M blocks out, and asking
     * vanilla for a FULL chunk there makes it GENERATE one, on the main thread
     * - which the watchdog, not a catch block, is what notices. So: the
     * non-generating form first.
     *
     * If vanilla says no, Sable gets a second say. Plot chunks are reached
     * through SubLevelContainer/LevelPlot rather than the parent level's chunk
     * map, so vanilla can report "not loaded" for a plot chunk the mod resolves
     * perfectly - that mismatch is what used to make these commands refuse on a
     * live ship. isOnVessel resolving means the plot really is there and a read
     * through the parent level will find it without generating anything.
     *
     * Both answers false means the position genuinely is not available: an
     * abandoned plot region, or a stale record. That is a refusal, not a
     * silent placement.
     */
    private static boolean chunkIsAvailable(ServerLevel level, BlockPos pos) {
        try {
            ChunkAccess chunk = level.getChunk(
                    SectionPos.blockToSectionCoord(pos.getX()),
                    SectionPos.blockToSectionCoord(pos.getZ()),
                    ChunkStatus.FULL, false);
            if (chunk != null) return true;
            return VesselLookup.isOnVessel(level, pos);
        } catch (Exception e) {
            ThreeHourTour.LOG.error("core placement: chunk availability check at {} in {} failed: {}",
                    pos, level.dimension().location(), e.toString());
            return false;
        }
    }

    /**
     * Whether a ship core block is definitely standing at {@code pos}.
     *
     * Positive observation only: false means "no core seen", which covers both
     * "read it, nothing there" and "could not read it". ReissueRule's
     * {@code verifiable} input is what separates those two, so this method does
     * not have to.
     */
    private static boolean shipCoreStandsAt(ServerLevel level, BlockPos pos) {
        try {
            if (!chunkIsAvailable(level, pos)) return false;
            return level.getBlockState(pos).is(CoreRegistry.SHIP_CORE.get());
        } catch (Exception e) {
            ThreeHourTour.LOG.error("core reissue: reading the recorded position {} in {} failed: {}",
                    pos, level.dimension().location(), e.toString());
            return false;
        }
    }

    /**
     * Places a core for a player who provably has none. When a record exists,
     * "provably has none" means the recorded core is verifiably gone, and the
     * replacement is restored at the recorded position - that is what "my
     * core was destroyed, put it back" means, and it needs no coordinate
     * guessing. When there is no record at all, this is a coreless player on
     * land, and the core is issued fresh at the operator's position.
     *
     * {@code here} is the operator override: a recorded position that can
     * never be verified (its vessel was deleted outright, not just unloaded)
     * would otherwise refuse this command forever. With {@code here}, an
     * unverifiable position no longer blocks re-issue - the one guard that
     * survives is a ship core still standing at the recorded position, which
     * still refuses unconditionally. The replacement core is placed at the
     * operator's own position instead of the unrecoverable recorded one.
     *
     * Every refusal happens before anything is mutated. The record is only
     * forgotten, and the block only written, once the decision is final -
     * a refusal after {@code forget()} would delete the record and place
     * nothing.
     */
    private static int reissue(CommandSourceStack source, ServerPlayer player, boolean here) {
        ShipCoreIndex index = ShipCoreIndex.get(source.getServer().overworld());

        // "Provably has none" is proved here, at the moment it matters, by
        // looking at the world - never inferred by a background pass. An
        // unverifiable position proves nothing, so the non-override path
        // refuses rather than guesses: handing out a second core is the one
        // mistake this command must not make.
        CoreRecord existing = index.forPlayer(player.getUUID()).orElse(null);

        ServerLevel home = existing == null ? null : source.getServer().getLevel(
                ResourceKey.create(Registries.DIMENSION, existing.dimension()));

        // A recorded core's position can be verified two ways: vanilla's chunk
        // map for a grounded core, or Sable's plot lookup for one on a vessel.
        // Sable plot chunks are reached through SubLevelContainer/LevelPlot,
        // not the parent level's chunk map, so home.isLoaded alone reports
        // "not loaded" for a plot the mod resolves fine - that is what made
        // this command refuse on a live ship. Either signal being true is
        // enough to trust what the world says at that position.
        boolean verifiable = home != null
                && (home.isLoaded(existing.pos()) || VesselLookup.isOnVessel(home, existing.pos()));

        // Attempted whenever there is a dimension to read, NOT only when
        // `verifiable` is true. Tying the "is a core still there?" read to the
        // verifiable flag meant a real core on a merely unloaded vessel was
        // never looked for, and `here` would forget the record and place a
        // SECOND core beside the first. Ship cores are indestructible, so that
        // duplicate would be permanent.
        boolean coreStillStanding = home != null && shipCoreStandsAt(home, existing.pos());

        ReissueRule.Decision decision =
                ReissueRule.decide(existing != null, verifiable, coreStillStanding, here);

        if (decision == ReissueRule.Decision.REFUSE_HAS_CORE) {
            source.sendFailure(Component.literal(player.getGameProfile().getName()
                    + " still has a ship core, at " + existing.pos()
                    + ". Re-issue is only for a player who has none."));
            return 0;
        }
        // A player with no core who was already issued a raft has not lost a
        // ship -- they have lost a raft they never assembled, and there is no
        // core to restore. Placing a bare core at the operator's feet would
        // give them a core with no vessel under it, which is a worse state than
        // the one they are in. Clearing the issue flag re-places the raft on
        // their next join, through the same path that gave them the first one.
        if (existing == null) {
            RaftIssueIndex issued = RaftIssueIndex.get(source.getServer().overworld());
            if (issued.wasIssued(player.getUUID())) {
                issued.clear(player.getUUID());
                source.sendSuccess(() -> Component.literal(
                        player.getGameProfile().getName() + " has no core but was already issued a "
                        + "raft, so there is nothing to restore. Cleared their raft record -- a new "
                        + "raft is placed the next time they log in."), true);
                return 1;
            }
        }

        if (decision == ReissueRule.Decision.REFUSE_UNVERIFIABLE) {
            source.sendFailure(Component.literal("A core is recorded for "
                    + player.getGameProfile().getName() + " at " + existing.pos() + " in "
                    + existing.dimension() + ", but that position cannot be verified gone. "
                    + "Load it and run this again, or use 'reissue " + player.getGameProfile().getName()
                    + " here' to override."));
            return 0;
        }

        ServerLevel targetLevel;
        BlockPos targetPos;

        // Whether targetPos came out of the plot transform, and so must be
        // paired with a resolved vessel before it can be written - see
        // PlotBindingRule. A recorded position is plot space exactly when the
        // record it came from was bound.
        boolean targetFromPlot;

        // Fed into the "core placement" log line below, so a failure report
        // says which coordinate space the target actually came from: a plot
        // resolution that resolved, one that fell back to the raw world
        // position because the operator wasn't standing on a vessel, or a
        // branch that never attempts the plot transform at all.
        String plotStatus;

        if (decision == ReissueRule.Decision.RESTORE) {
            targetLevel = home;
            targetPos = existing.pos();
            targetFromPlot = existing.isBound();
            plotStatus = "not attempted (using recorded position)";
        } else if (here) {
            // The operator's own position, not the unrecoverable recorded one.
            // Routed through the plot transform so standing on a vessel deck
            // behaves the same whether or not a record existed.
            targetLevel = source.getLevel();
            Optional<BlockPos> plot = VesselPosition.toPlot(targetLevel, source.getPosition());
            targetFromPlot = plot.isPresent();
            plotStatus = targetFromPlot ? "resolved" : "fell back to world pos (not on a vessel)";
            targetPos = plot.orElseGet(() -> BlockPos.containing(source.getPosition()));
        } else {
            targetLevel = source.getLevel();
            targetPos = BlockPos.containing(source.getPosition());
            targetFromPlot = false;
            plotStatus = "not attempted (grounded issue, world pos)";
        }

        // Single greppable line: the operator cannot copy text out of
        // Minecraft chat, so the log is the only channel that can carry which
        // coordinate space was actually written into. Logged unconditionally,
        // before the write is attempted, so it is present whether the write
        // below succeeds or fails.
        ThreeHourTour.LOG.info(
                "core placement: decision={} plotResolution=[{}] operatorPos=[{}] targetPos=[{}] dimension={}",
                decision, plotStatus, source.getPosition(), targetPos,
                targetLevel.dimension().location());

        if (!chunkIsAvailable(targetLevel, targetPos)) {
            source.sendFailure(Component.literal("The chunk at " + targetPos + " in "
                    + targetLevel.dimension().location() + " is not loaded, and neither vanilla "
                    + "nor Sable resolves it. This command will not generate terrain there - "
                    + "at plot coordinates that stalls the server. Load that position and run "
                    + "this again. Nothing was recorded."));
            return 0;
        }

        // setBlockAndUpdate overwrites whatever is there, silently. Ship cores
        // are indestructible, so overwriting another player's core would
        // destroy something the game itself offers no way to remove.
        BlockState occupant = targetLevel.getBlockState(targetPos);
        if (occupant.is(CoreRegistry.SHIP_CORE.get())) {
            source.sendFailure(Component.literal("There is already a ship core at " + targetPos
                    + " in " + targetLevel.dimension().location()
                    + ". Placing here would overwrite it, and a ship core cannot be replaced. "
                    + "Move and run this again. Nothing was recorded."));
            return 0;
        }

        // A restored core (including an override) reclaims whatever vessel
        // actually sits at the placed position now; a freshly issued core
        // with no override starts grounded, same as before - that path is
        // for a coreless player on land.
        VesselRef vessel = (decision == ReissueRule.Decision.RESTORE || here)
                ? VesselLookup.vesselAt(targetLevel, targetPos)
                : null;
        UUID vesselId = vessel == null ? null : vessel.id();

        if (PlotBindingRule.mustRefuse(targetFromPlot, vessel != null)) {
            source.sendFailure(Component.literal("The target " + targetPos
                    + " in " + targetLevel.dimension().location() + " is a vessel plot position, "
                    + "but no vessel could be identified there. Recording that pair would strand "
                    + player.getGameProfile().getName() + " ~20.4M blocks out on their next death. "
                    + "Nothing was recorded. Stand squarely on the deck and run this again."));
            return 0;
        }

        // Past every refusal: from here the command mutates.
        if (existing != null) {
            if (here) {
                ThreeHourTour.LOG.warn(
                        "Operator override: reissuing {}'s core with 'here', discarding recorded "
                                + "core at {} in {} (vessel {})",
                        player.getUUID(), existing.pos(), existing.dimension(),
                        existing.isBound() ? existing.vesselId() : "none");
            } else {
                ThreeHourTour.LOG.warn("Core recorded for {} at {} is gone; re-issuing",
                        player.getUUID(), existing.pos());
            }
            index.forget(player.getUUID());
        }

        targetLevel.setBlockAndUpdate(targetPos, CoreRegistry.SHIP_CORE.get().defaultBlockState());

        BlockState placedState = targetLevel.getBlockState(targetPos);
        if (!placedState.is(CoreRegistry.SHIP_CORE.get())) {
            // The write itself never took - most likely the chunk was never
            // truly loaded, so setBlockAndUpdate no-op'd. Distinct from the
            // "block entity missing" case below: this one means no ship core
            // block exists at all.
            ThreeHourTour.LOG.error(
                    "core placement: write failed: player={} targetPos=[{}] dimension={} actualBlock={}",
                    player.getGameProfile().getName(), targetPos,
                    targetLevel.dimension().location(), placedState.getBlock());
            source.sendFailure(Component.literal("Tried to place the core at " + targetPos
                    + " in " + targetLevel.dimension().location() + " but the block there is "
                    + placedState.getBlock() + ", not a ship core. The write itself failed - "
                    + "likely the chunk never actually loaded. Nothing was recorded."));
            return 0;
        }

        ShipCoreBlockEntity core = ShipCoreBlockEntity.at(targetLevel, targetPos).orElse(null);
        if (core == null) {
            // The block itself is correct - the write took - but no block
            // entity came with it. A different fault than the write failing
            // outright, and it needs a different fix.
            ThreeHourTour.LOG.error(
                    "core placement: no block entity: player={} targetPos=[{}] dimension={}",
                    player.getGameProfile().getName(), targetPos, targetLevel.dimension().location());
            source.sendFailure(Component.literal("Placed the ship core block at " + targetPos
                    + " in " + targetLevel.dimension().location()
                    + " but it has no block entity. Nothing was recorded."));
            return 0;
        }

        core.setBinding(new CoreBinding(player.getUUID(), vesselId));
        index.put(new CoreRecord(player.getUUID(), targetLevel.dimension().location(), targetPos, vesselId));

        // Same values as the chat message in each branch below, logged at INFO
        // so the outcome is readable from the server log: the operator running
        // these commands cannot copy text out of Minecraft chat.
        String vesselLabel = vesselId != null ? vesselId.toString() : "grounded";
        if (here) {
            source.sendSuccess(() -> Component.literal("Operator override: "
                    + player.getGameProfile().getName() + "'s core (re)issued at " + targetPos
                    + (vesselId != null ? ", bound to vessel " + vesselId : ", grounded")), true);
            ThreeHourTour.LOG.info("core reissue here: player={} pos={} vessel={}",
                    player.getGameProfile().getName(), targetPos, vesselLabel);
        } else if (decision == ReissueRule.Decision.RESTORE) {
            source.sendSuccess(() -> Component.literal("Restored " + player.getGameProfile().getName()
                    + "'s ship core at the recorded position " + targetPos), true);
            ThreeHourTour.LOG.info("core reissue restored: player={} pos={} vessel={}",
                    player.getGameProfile().getName(), targetPos, vesselLabel);
        } else {
            source.sendSuccess(() -> Component.literal("Issued a new ship core at " + targetPos
                    + " to " + player.getGameProfile().getName()), true);
            ThreeHourTour.LOG.info("core reissue issued: player={} pos={} vessel={}",
                    player.getGameProfile().getName(), targetPos, vesselLabel);
        }
        return 1;
    }
}

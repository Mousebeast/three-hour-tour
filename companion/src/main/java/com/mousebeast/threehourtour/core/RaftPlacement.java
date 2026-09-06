package com.mousebeast.threehourtour.core;

import com.mousebeast.threehourtour.ThreeHourTour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The starting raft, placed on a player's first join.
 *
 * <p><b>Keyed on "have we issued a raft", never on "do they have a ship."</b>
 * A player who lands on a strange raft and looks around before right-clicking
 * anything has no vessel and must not get a second raft; nor must anyone who
 * logs out, crashes, or wanders off for an hour first. {@link RaftIssueIndex}
 * answers the right question and is written the moment a raft lands.
 *
 * <p><b>The flag is written only after a structure actually lands.</b> If no
 * suitable water is found inside the search radius, nothing is recorded and the
 * player is eligible again next join -- a failed search must not cost somebody
 * their ship permanently.
 *
 * <p><b>The assembler's position is recorded against the player.</b>
 * {@code ActivationWatcher} resolves a new vessel's owner from
 * {@link AssemblerOwners}, which is normally filled in by the block-placement
 * event -- and nobody places a block when a structure is stamped into the
 * world. Without this the raft would assemble into a ship with no core at all,
 * logging "no recorded placer", which would have happened to every new player.
 */
@EventBusSubscriber(modid = ThreeHourTour.MOD_ID)
public final class RaftPlacement {

    public static final ResourceLocation RAFT =
            ResourceLocation.fromNamespaceAndPath(ThreeHourTour.MOD_ID, "raft");

    private static final ResourceKey<Biome> FROZEN_OCEAN = Biomes.FROZEN_OCEAN;
    private static final ResourceKey<Biome> DEEP_FROZEN_OCEAN = Biomes.DEEP_FROZEN_OCEAN;

    /** The deck is whichever layer carries the most of these. */
    public static final ResourceLocation TREATED_PLANKS =
            ResourceLocation.fromNamespaceAndPath(ThreeHourTour.MOD_ID, "treated_planks");

    /** How far out to look for water, in blocks, before giving up for now. */
    public static final int SEARCH_RADIUS = 640;
    /** Spacing of candidate sites. Coarse: any ocean is a big target. */
    public static final int SEARCH_STEP = 32;
    /**
     * No land, and no frozen ocean, within this of the site.
     *
     * <p>Widened from 96 on 2026-09-04 after a test spawn landed in cold ocean
     * ringed by icebergs — close enough to be walled in, far enough that the
     * old radius never looked.
     */
    public static final int LAND_CLEARANCE = 160;
    /**
     * Grid spacing for the land sweep. Land is never one column.
     *
     * <p>Widened from 8 on 2026-09-05. At 8 the sweep was 41x41 = 1681 points
     * per candidate site, each asking two separate noise questions, so a single
     * candidate cost 3363 noise queries and the full spiral could reach 5.65
     * million — <b>on the server thread, inside the login event</b>. The player
     * fell through the air for ten to fifteen seconds, predicting locally,
     * until the server caught up and teleported them onto the raft.
     *
     * <p>16 is safe against what it is looking for: the world's islands run a
     * median 27 blocks across, so nothing this sweep exists to find can hide
     * between two samples.
     */
    public static final int LAND_SAMPLE = 16;

    private RaftPlacement() {}

    @SubscribeEvent
    public static void onJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.getServer() == null) return;

        ServerLevel level = player.getServer().overworld();
        UUID id = player.getUUID();
        RaftIssueIndex issued = RaftIssueIndex.get(level);

        if (issued.wasIssued(id)) return;

        // Someone who already has a core predates this feature, or was set up
        // by hand. Record them as issued so the question is never asked again.
        if (ShipCoreIndex.get(level).hasCore(id)) {
            issued.markIssued(id);
            return;
        }

        Optional<StructureTemplate> template = level.getStructureManager().get(RAFT);
        if (template.isEmpty()) {
            ThreeHourTour.LOG.error("No raft template at {} -- no raft placed for {}", RAFT, id);
            return;
        }
        StructureTemplate raft = template.get();
        Vec3i size = raft.getSize();
        int deck = deckLayer(raft);

        long searchStart = System.nanoTime();
        BlockPos origin = findSite(level, size, deck);
        long searchMs = (System.nanoTime() - searchStart) / 1_000_000L;
        if (origin == null) {
            ThreeHourTour.LOG.warn(
                    "No open ocean within {} blocks of spawn for {}'s raft. Not marking them "
                    + "issued -- they get another attempt next join.", SEARCH_RADIUS, id);
            return;
        }

        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(Rotation.NONE)
                // The states in the template are already right, and saying so
                // skips placeInWorld's updateShape pass over everything it
                // placed. Kept because it is correct and cheaper.
                //
                // It is NOT what saved the drivetrain wiring, and this comment
                // used to claim it was. The two dust over the inverted clutch
                // and the encased chain drive were lost because loadPalette
                // places every block carrying a block entity LAST -- so they
                // were stamped into open air a whole group before their
                // supports arrived. No placement setting can reach that; the
                // raft was re-laid out on 2026-09-05 so nothing rests on a
                // machine, and tests/test_raft_structure.py holds it there.
                .setKnownShape(true)
                // The honey glue is an entity, and without it the raft arrives
                // unglued and cannot be assembled at all.
                .setIgnoreEntities(false);
        // UPDATE_CLIENTS, not UPDATE_ALL: tell clients, do not fire a neighbour
        // update per block. Correct and cheap for a structure whose states are
        // already final.
        //
        // Also not the drivetrain fix, though it was shipped as one. Those
        // blocks were never illegal -- dust sits happily on a chain drive, a
        // clutch and a gearbox, verified on the live server -- they were just
        // placed before the thing they stand on existed. See setKnownShape
        // above.
        if (!raft.placeInWorld(level, origin, origin, settings, level.getRandom(),
                               Block.UPDATE_CLIENTS)) {
            ThreeHourTour.LOG.error("Raft template refused to place at {} for {}", origin, id);
            return;
        }

        BlockPos assembler = findAssembler(level, origin, size);
        if (assembler != null) {
            AssemblerOwners.get(level).record(assembler, id);
        } else {
            ThreeHourTour.LOG.error(
                    "Placed a raft at {} for {} but found no Physics Assembler in it. The raft "
                    + "will assemble into a ship with no core -- check the template.", origin, id);
        }

        issued.markIssued(id);

        BlockPos stand = origin.offset(size.getX() / 2, deck + 1, size.getZ() / 2);
        player.teleportTo(level, stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5,
                          player.getYRot(), player.getXRot());

        // After the teleport, so the dog is tamed to a player who is already
        // standing where it spawns. Inside the same issued-once flow, so it
        // cannot arrive twice; see StarterDog for why it is not in the template.
        StarterDog.placeFor(level, player, stand);

        // The search time is logged because it is the only thing here a player
        // can feel. It runs on the server thread inside the login event, so
        // every millisecond of it is a millisecond the client spends falling
        // through the air predicting locally. It reached ten to fifteen seconds
        // before 2026-09-05. If this line ever reads in the thousands again,
        // that is the bug, not a slow disk.
        ThreeHourTour.LOG.info("Placed {}'s raft at {}, assembler at {} (site search {} ms)",
                id, origin, assembler, searchMs);
    }

    /**
     * The first acceptable site on a coarse spiral out from world spawn.
     *
     * <p>Spawn on an ocean world is usually water, but "usually" is not a
     * guarantee and an island at spawn is exactly the case that would strand
     * someone on land, so the search starts there and walks outward rather
     * than assuming.
     */
    private static BlockPos findSite(ServerLevel level, Vec3i size, int deck) {
        BlockPos spawn = level.getSharedSpawnPos();
        // The DECK goes at sea level, not the template's bottom. Anything the
        // raft hangs below its hull -- a boarding rope, most obviously -- is
        // part of the template and must end up in the water, not hold the deck
        // up out of it.
        int y = level.getSeaLevel() - deck;
        // One memo for the whole search. Candidate sites are 32 blocks apart
        // and each sweeps 160 blocks in every direction, so neighbouring
        // candidates re-ask the same noise question five or six times over.
        // The y never changes -- it is sea level minus the deck layer -- so the
        // key is the column, and an answer stays true for the whole search.
        Map<Long, Boolean> seen = new HashMap<>();
        for (int radius = 0; radius <= SEARCH_RADIUS; radius += SEARCH_STEP) {
            for (int dx = -radius; dx <= radius; dx += SEARCH_STEP) {
                for (int dz = -radius; dz <= radius; dz += SEARCH_STEP) {
                    // Ring only: the interior was covered by a smaller radius.
                    if (radius != 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                    BlockPos origin = new BlockPos(spawn.getX() + dx, y, spawn.getZ() + dz);
                    if (acceptable(level, origin, size, deck, seen)) return origin;
                }
            }
        }
        return null;
    }

    /**
     * Open, unfrozen water at one column, remembered.
     *
     * <p>Both questions are noise reads and neither is cheap; the memo is what
     * makes a 640-block spiral affordable inside a login event. Only the column
     * is keyed, because the y this is asked at is fixed for an entire search.
     */
    private static boolean clearWater(ServerLevel level, ChunkGenerator generator,
                                      RandomState random, Map<Long, Boolean> seen,
                                      int x, int y, int z) {
        long key = BlockPos.asLong(x, 0, z);
        Boolean known = seen.get(key);
        if (known != null) return known;
        boolean clear = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG,
                                                level, random) <= level.getSeaLevel()
                && openOcean(generator, random, x, y, z);
        seen.put(key, clear);
        return clear;
    }

    /**
     * Whether a site will do, asked in an order that does not generate the
     * world to find out.
     *
     * <p><b>The cheap tests are noise queries, not block reads.</b>
     * {@code level.getBiome} and {@code level.getHeight} both go through the
     * chunk, which means a probe at an ungenerated position GENERATES it. The
     * first version of this asked both, up to 1600 candidate sites, each with a
     * 625-point land sweep -- on a spawn that happened to be an island, that is
     * hundreds of thousands of chunk generations on the login thread. It would
     * have looked exactly like the server hanging, and only for the unlucky.
     *
     * <p>The generator's biome source and base-height sampler answer the same
     * two questions from noise alone, without building anything. Only a
     * candidate that survives both is worth reading real blocks for, and by
     * then there is one of them.
     */
    private static boolean acceptable(ServerLevel level, BlockPos origin, Vec3i size,
                                      int deck, Map<Long, Boolean> seen) {
        BlockPos centre = origin.offset(size.getX() / 2, 0, size.getZ() / 2);
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        RandomState random = level.getChunkSource().randomState();

        // One sweep, two questions, both from noise, asked from the centre
        // outward.
        //
        // The height test alone is not enough, and that is not a tuning
        // problem. **Icebergs are features, placed after the noise terrain, so
        // getBaseHeight cannot see them at any radius** -- a test spawn on
        // 2026-09-04 landed in cold ocean walled in by bergs while every height
        // probe read open water. What does see them is the biome they belong
        // to, so the sweep asks that too and rejects frozen ocean along with
        // anything that is not ocean at all.
        //
        // Plain cold ocean stays acceptable (owner, 2026-09-04): it grows no
        // ice of its own, and a cold ocean beside a frozen one is rejected by
        // this sweep anyway.
        //
        // **Ring order is the point, not tidiness.** The sweep returns on its
        // first hit, and it used to start at the far corner and walk in -- so a
        // site with a beach twenty blocks away still scanned most of a 41x41
        // grid before finding it. Nearest-first means the sites that deserve to
        // be rejected are rejected almost immediately, which is most of them.
        for (int ring = 0; ring <= LAND_CLEARANCE; ring += LAND_SAMPLE) {
            for (int dx = -ring; dx <= ring; dx += LAND_SAMPLE) {
                for (int dz = -ring; dz <= ring; dz += LAND_SAMPLE) {
                    // Perimeter only; the interior belonged to a smaller ring.
                    if (ring != 0 && Math.abs(dx) != ring && Math.abs(dz) != ring) continue;
                    if (!clearWater(level, generator, random, seen,
                                    centre.getX() + dx, centre.getY(), centre.getZ() + dz)) {
                        return false;
                    }
                }
            }
        }

        boolean restsOnWater = true;
        boolean volumeClear = true;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = 0; x < size.getX() && restsOnWater && volumeClear; x++) {
            for (int z = 0; z < size.getZ() && restsOnWater && volumeClear; z++) {
                cursor.set(origin.getX() + x, origin.getY() + deck - 1, origin.getZ() + z);
                if (!level.getFluidState(cursor).isSource()) restsOnWater = false;
                for (int y = 0; y < size.getY(); y++) {
                    cursor.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    if (!level.getBlockState(cursor).canBeReplaced()) {
                        volumeClear = false;
                        break;
                    }
                }
            }
        }

        // Land was ruled out from noise above, so zero here is a statement of
        // what was checked rather than a second sweep.
        // Biome and land were both ruled out from noise above, so the first
        // and last arguments state what was checked rather than re-checking it.
        return RaftSiteRule.acceptable(true, restsOnWater, volumeClear, 0);
    }

    /**
     * Which layer of the template is the deck, as an offset from its bottom.
     *
     * <p>Found rather than assumed, because the template is captured by hand in
     * game and can grow downward without warning -- hanging a boarding rope
     * over the side does exactly that. Assuming the hull is layer zero would
     * then float the deck one block higher for every block of rope, which is
     * the opposite of what the rope was added to fix.
     *
     * <p>The deck is the layer carrying the most treated planks: the hull is
     * seventy-odd of them and nothing else in the raft comes close, so this is
     * a landslide rather than a judgement call.
     */
    static int deckLayer(StructureTemplate raft) {
        Block planks = BuiltInRegistries.BLOCK.getOptional(TREATED_PLANKS).orElse(null);
        if (planks == null) return 0;

        Map<Integer, Integer> perLayer = new HashMap<>();
        for (StructureTemplate.StructureBlockInfo info
                : raft.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), planks, false)) {
            perLayer.merge(info.pos().getY(), 1, Integer::sum);
        }
        return perLayer.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(0);
    }

    /** Ocean, and not the kind that grows icebergs. */
    private static boolean openOcean(ChunkGenerator generator, RandomState random,
                                     int x, int y, int z) {
        Holder<Biome> biome = generator.getBiomeSource().getNoiseBiome(
                QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z),
                random.sampler());
        if (!biome.is(BiomeTags.IS_OCEAN)) return false;
        return !biome.is(FROZEN_OCEAN) && !biome.is(DEEP_FROZEN_OCEAN);
    }

    /** The assembler inside the volume we just stamped down. */
    private static BlockPos findAssembler(ServerLevel level, BlockPos origin, Vec3i size) {
        Optional<Block> assembler =
                BuiltInRegistries.BLOCK.getOptional(AssemblerConsumer.PHYSICS_ASSEMBLER);
        if (assembler.isEmpty()) return null;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = 0; x < size.getX(); x++) {
            for (int y = 0; y < size.getY(); y++) {
                for (int z = 0; z < size.getZ(); z++) {
                    cursor.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    if (level.getBlockState(cursor).is(assembler.get())) return cursor.immutable();
                }
            }
        }
        return null;
    }
}

package com.mousebeast.threehourtour.core;

import com.mousebeast.threehourtour.ThreeHourTour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.neoforged.fml.ModList;

/**
 * The dog every player wakes up with.
 *
 * <p>Placed by {@link RaftPlacement} at the moment the raft is issued, so it
 * inherits that routine's one-per-player guarantee for free: the raft flag is
 * the only thing either of them is keyed on, and a player who relogs before
 * touching anything gets neither a second raft nor a second dog.
 *
 * <p><b>Why it is not in the raft template.</b> A tamed animal stores its
 * owner's UUID, and the template is the same bytes for every player. A dog
 * baked into the capture would arrive belonging to whoever the capture was
 * taken as, or to nobody.
 *
 * <p><b>Why there is no compile dependency on Doggy Talents.</b> Its
 * {@code Dog} extends {@code AbstractDog} extends vanilla
 * {@link TamableAnimal}, and it overrides {@code tame(Player)} - so spawning
 * by registry id and taming through the vanilla type calls the mod's own
 * implementation and does whatever it needs done. Nothing here names a
 * Doggy Talents class, and the mod stays a soft dependency.
 *
 * <p>Failure is loud but never fatal. The pack ships this mod, so a missing id
 * means something is wrong and the log has to say so - but a player arriving
 * without a dog still has a working raft, and a thrown exception here would
 * take the whole opening down with it.
 */
public final class StarterDog {
    private StarterDog() {}

    static final String MOD_ID = "doggytalents";

    /**
     * The dog's entity id.
     *
     * <p>Verified against the staged jars' <b>class files</b> by
     * {@code tools/audit-entity-ids.py}, not against a lang file - Doggy
     * Talents ships {@code entity.doggytalents.dog}, so a lang check would
     * pass for an id the registry has never heard of. That is the trap that
     * broke the L4-P6 deploy and cost the whole research tree twice.
     */
    // The namespace is written out rather than reusing MOD_ID on purpose:
    // tools/audit-entity-ids.py finds ids by reading string literals out of
    // this source, and an id assembled from a constant is invisible to it.
    // StarterDogTest asserts the two cannot drift apart.
    static final ResourceLocation DOG =
            ResourceLocation.fromNamespaceAndPath("doggytalents", "dog");

    /**
     * Put a tamed dog on the deck beside a player who has just been given
     * their raft.
     *
     * @param stand the deck position the player was teleported to. Spawning on
     *              exactly that block rather than beside it is deliberate: it
     *              is the one position on the raft already proven to be a legal
     *              place to stand, and two entities may share a block while a
     *              guessed offset could be a mast.
     */
    public static void placeFor(ServerLevel level, ServerPlayer player, BlockPos stand) {
        try {
            if (!ModList.get().isLoaded(MOD_ID)) {
                ThreeHourTour.LOG.error(
                        "{} is not loaded, so {} arrives without a dog. The pack pins this mod; "
                        + "if it has been removed on purpose, remove this call too.",
                        MOD_ID, player.getGameProfile().getName());
                return;
            }

            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(DOG).orElse(null);
            if (type == null) {
                ThreeHourTour.LOG.error(
                        "{} is loaded but registers no entity {} -- {} arrives without a dog. "
                        + "The id has been renamed by a mod update.",
                        MOD_ID, DOG, player.getGameProfile().getName());
                return;
            }

            Entity entity = type.create(level);
            if (!(entity instanceof TamableAnimal dog)) {
                ThreeHourTour.LOG.error(
                        "{} is not a TamableAnimal ({}), so it cannot be tamed through the vanilla "
                        + "type and {} arrives without a dog.",
                        DOG, entity == null ? "create() returned null"
                                            : entity.getClass().getName(),
                        player.getGameProfile().getName());
                return;
            }

            dog.moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5,
                       player.getYRot(), 0.0F);
            // Into the world first, then tamed: the mod's own tame() writes
            // synced entity data, and an entity that is not in a level yet has
            // nowhere to sync it to.
            if (!level.addFreshEntity(dog)) {
                ThreeHourTour.LOG.error("The level refused {}'s dog at {}",
                        player.getGameProfile().getName(), stand);
                return;
            }
            dog.tame(player);

            ThreeHourTour.LOG.info("Gave {} a dog at {}",
                    player.getGameProfile().getName(), stand);
        } catch (Exception e) {
            // A dog is a nice-to-have; the raft, the assembler owner and the
            // teleport are not. Nothing about this may take the opening down.
            ThreeHourTour.LOG.error("Placing {}'s dog failed: {}",
                    player.getGameProfile().getName(), e.toString(), e);
        }
    }
}

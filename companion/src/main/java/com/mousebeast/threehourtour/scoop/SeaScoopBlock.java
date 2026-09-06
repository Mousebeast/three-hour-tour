package com.mousebeast.threehourtour.scoop;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The sea scoop. Harvests only while its vessel is under way (L1-23).
 *
 * Unlike the ship core this DOES get a BlockItem - it is ordinary equipment a
 * player crafts, carries and places. Placement is restricted to vessels through
 * the same path as the Botany and Bonsai pots (L1-11/L1-12), which states its
 * reason rather than cancelling silently (L1-45).
 *
 * Loot asymmetry, accepted rather than fixed: the scoop's own loot table
 * (data/threehourtour/loot_table/blocks/sea_scoop.json) carries a
 * minecraft:survives_explosion condition, so an explosion that destroys the
 * scoop drops nothing from that table. The installed mesh's drop, in
 * onRemove below, carries no such condition, so the same explosion can still
 * drop the mesh even though the scoop itself is lost. That errs in the
 * player's favour and is not worth a second code path.
 */
public class SeaScoopBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    /**
     * Any wrench, not specifically Create's.
     *
     * Create populates this common tag with its own wrench, so reading the tag
     * costs nothing and catches every other wrench in the pack for free. The
     * alternative - implementing Create's IWrenchable - would require a
     * compile-time dependency on Create, and this mod does not declare Create
     * at all: the block class would fail to load under runGameTestServer with
     * NoClassDefFoundError before a single test ran.
     */
    public static final TagKey<Item> WRENCH =
            TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "tools/wrench"));

    public static Properties scoopProperties() {
        return Properties.of()
                .mapColor(MapColor.COLOR_BROWN)
                .strength(2.0F)
                .sound(SoundType.WOOD);
    }

    public SeaScoopBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** Front faces the player who placed it, the same idiom as a furnace. */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        if (held.is(WRENCH)) {
            if (!level.isClientSide()) {
                level.setBlockAndUpdate(pos, state.setValue(FACING,
                        state.getValue(FACING).getClockWise()));
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM,
                        SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }

        if (!(level.getBlockEntity(pos) instanceof SeaScoopBlockEntity scoop)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        // Empty hand: take the mesh back.
        //
        // The block entity never syncs its mesh slot to the client (no
        // getUpdateTag override), so the client's copy of meshHandler() is
        // always empty regardless of what the server actually holds.
        // simulate=level.isClientSide() keeps this call from mutating that
        // always-empty client-side copy - harmless today only because it is
        // always empty, but a real desync risk the moment anything starts
        // syncing. Deciding the result from installed.isEmpty() would itself
        // desync client from server (the client would always read empty and
        // fall through to PASS while the server actually succeeded, meaning
        // no arm swing and a default-interaction fallthrough the server
        // never ran), so the client branch below predicts success
        // unconditionally instead and lets the server stay authoritative on
        // what really happened.
        if (held.isEmpty()) {
            ItemStack installed = scoop.meshHandler().extractItem(0, 1, level.isClientSide());
            if (level.isClientSide()) {
                return ItemInteractionResult.SUCCESS;
            }
            if (installed.isEmpty()) {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
            if (!player.getInventory().add(installed)) {
                player.drop(installed, false);
            }
            return ItemInteractionResult.CONSUME;
        }

        // A mesh: install it, swapping out whatever is already in the slot.
        //
        // Same unsynced-slot problem as above: the client cannot see whether
        // the server's slot is empty or occupied, so it cannot tell an
        // install from a swap in advance. Short-circuit on the client rather
        // than run a check that would sometimes be wrong, so this branch's
        // client-side outcome (arm swing, no sound) at least agrees with the
        // extract branch's above - and matches the server, since a valid
        // mesh now always succeeds one way or the other.
        if (scoop.meshHandler().isItemValid(0, held)) {
            if (level.isClientSide()) {
                return ItemInteractionResult.SUCCESS;
            }
            // Extract first so the slot is genuinely empty before inserting -
            // ItemStack.EMPTY when the slot was already free, in which case
            // this is a plain install. Insert before consuming from the held
            // stack: if insertItem somehow refuses the now-empty slot, held
            // stays untouched and the extracted mesh goes straight back. The
            // put-back can itself fail - a hand-edited or foreign save can
            // leave more than getSlotLimit(0) in the slot via
            // deserializeNBT, which writes NBT stacks in without clamping
            // (installedTier()'s comment names this same gate-bypassing
            // route) - so a failed put-back delivers the mesh to the player
            // below instead of letting it fall out of scope, the same
            // delivery the successful swap uses. Either way this returns
            // CONSUME, not PASS: the client already predicted SUCCESS above
            // and cannot see any of this, so a server-side PASS here would
            // contradict that prediction instead of agreeing with it.
            ItemStack installed = scoop.meshHandler().extractItem(0, 1, false);
            ItemStack one = held.copyWithCount(1);
            if (!scoop.meshHandler().insertItem(0, one, false).isEmpty()) {
                if (!installed.isEmpty()) {
                    ItemStack leftover = scoop.meshHandler().insertItem(0, installed, false);
                    if (!leftover.isEmpty()) {
                        if (!player.getInventory().add(leftover)) {
                            player.drop(leftover, false);
                        }
                    }
                }
                return ItemInteractionResult.CONSUME;
            }
            // consume() handles creative internally (LivingEntity.hasInfiniteMaterials),
            // the same unguarded call FlowerPotBlock.useItemOn makes - no need to
            // check getAbilities().instabuild ourselves.
            held.consume(1, player);
            if (!installed.isEmpty()) {
                // Same delivery the empty-hand extract path uses above: add
                // to the inventory, drop on the ground if it's full, so a
                // full inventory never destroys the swapped-out mesh.
                if (!player.getInventory().add(installed)) {
                    player.drop(installed, false);
                }
            }
            return ItemInteractionResult.CONSUME;
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /**
     * Drops the installed mesh, exactly once, when the block itself goes
     * away - not on every blockstate change. onRemove fires on every state
     * change including the wrench turning FACING; the block-identity guard
     * below (not state equality) is what stops a wrench turn from dropping
     * the mesh on the floor while leaving it installed in the slot, a
     * duplication bug that a player could farm once noticed.
     *
     * Deliberately not done through the block's own loot table
     * (data/threehourtour/loot_table/blocks/sea_scoop.json): that table has
     * no way to know whether a mesh is installed, so it would hand one out
     * on every break, including from a scoop that never had one.
     *
     * Asymmetry accepted, not fixed: the loot table carries a
     * minecraft:survives_explosion condition, so an explosion that destroys
     * the scoop drops nothing from the table - but this onRemove drop has no
     * such condition, so the same explosion can still drop the mesh. That
     * favours the player and is not worth a second code path.
     */
    @Override
    protected void onRemove(BlockState oldState, Level level, BlockPos pos,
                            BlockState newState, boolean movedByPiston) {
        if (!oldState.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof SeaScoopBlockEntity scoop) {
                ItemStack installed = scoop.meshHandler().getStackInSlot(0);
                if (!installed.isEmpty()) {
                    // The live stack already carries its DAMAGE component from
                    // hurtAndBreak, so a part-worn mesh drops part-worn.
                    Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), installed);
                    scoop.meshHandler().setStackInSlot(0, ItemStack.EMPTY);
                }
            }
        }
        super.onRemove(oldState, level, pos, newState, movedByPiston);
    }

    // rotate/mirror default to returning the state unchanged, which silently
    // breaks structure blocks and any mod that rotates a schematic. Cheap to
    // get right, invisible when wrong.
    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SeaScoopBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (lvl, pos, st, be) -> {
            if (be instanceof SeaScoopBlockEntity scoop) {
                scoop.serverTick();
            }
        };
    }
}

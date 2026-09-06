package com.mousebeast.threehourtour.research;

import com.mousebeast.threehourtour.core.FtbTeamResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.BlockHitResult;
import com.mojang.serialization.MapCodec;

import java.util.OptionalLong;
import java.util.UUID;

/**
 * The research terminal block.
 *
 * Two interactions, and deliberately no picker of its own:
 *
 * <ul>
 *   <li><b>Right-click</b> opens the quest book. The book already draws the
 *       tree with its prerequisites, descriptions and per-task progress, so a
 *       second list here would duplicate it badly and would have to invent its
 *       own answer to how much of the tree to reveal.</li>
 *   <li><b>Sneak + right-click</b> adopts whatever node the player has pinned
 *       in that book. Pinning is an FTB Quests feature that already syncs to
 *       the server, so "choose in the book, bind at the block" needs no mixin
 *       and no new GUI.</li>
 * </ul>
 *
 * Sneak-to-bind is the same idiom FTB Quests' own Task Screen Configurator
 * uses, so it should read as native rather than as ours.
 */
public class ResearchTerminalBlock extends BaseEntityBlock {

    public static final MapCodec<ResearchTerminalBlock> CODEC = simpleCodec(ResearchTerminalBlock::new);

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public ResearchTerminalBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    public static Properties terminalProperties() {
        return BlockBehaviour.Properties.of()
                .strength(2.5F)
                .sound(SoundType.WOOD)
                .requiresCorrectToolForDrops();
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ResearchTerminalBlockEntity(pos, state);
    }

    /**
     * Binds the terminal to the placer's team.
     *
     * Resolved once, here, because a pipe is not a player: when a pack arrives
     * there is nobody left to ask whose research it should pay for.
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide() || !(placer instanceof Player player)) return;
        if (level.getBlockEntity(pos) instanceof ResearchTerminalBlockEntity terminal) {
            QuestBridge.teamIdFor(player).ifPresent(terminal::setTeamId);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof ResearchTerminalBlockEntity terminal)) {
            return InteractionResult.PASS;
        }
        if (!QuestBridge.available()) {
            if (!level.isClientSide()) {
                player.displayClientMessage(
                        Component.translatable("block.threehourtour.research_terminal.no_quests"), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        if (player.isSecondaryUseActive()) {
            if (!level.isClientSide()) adoptPinned(level, pos, player, terminal);
            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        if (level.isClientSide()) {
            // The book is a client screen; opening it is the whole action.
            ResearchTerminalClient.openQuestBook();
        } else {
            reportTarget(player, terminal);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    /** Server-side: state what the terminal is pointed at, and how far along it is. */
    private void reportTarget(Player player, ResearchTerminalBlockEntity terminal) {
        var view = QuestBridge.nodeView(terminal.targetQuestId(), terminal.teamId(), true);
        if (view.isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("block.threehourtour.research_terminal.no_target"), false);
            return;
        }
        QuestBridge.NodeView node = view.get();
        player.displayClientMessage(Component.translatable(
                "block.threehourtour.research_terminal.target", node.title()), false);
        for (QuestBridge.TaskView task : node.tasks()) {
            player.displayClientMessage(Component.translatable(
                    task.complete()
                            ? "block.threehourtour.research_terminal.task_done"
                            : "block.threehourtour.research_terminal.task",
                    task.title(), task.progress(), task.max()), false);
        }
    }

    /**
     * Server-side: take the player's pinned node as the new target.
     *
     * Refuses loudly in the three cases a player can actually hit - nothing
     * pinned, several pinned, or a node with no payable task - because each
     * one otherwise ends as a pipe quietly refusing packs into a block that
     * looks fine.
     */
    private void adoptPinned(Level level, BlockPos pos, Player player, ResearchTerminalBlockEntity terminal) {
        if (!canEdit(player, terminal)) {
            player.displayClientMessage(
                    Component.translatable("block.threehourtour.research_terminal.not_your_team"), true);
            return;
        }

        // Adopting is the act that claims an unowned terminal. Without this a
        // terminal that nobody hand-placed - admin-spawned, or one whose
        // placer could not be resolved - binds happily and then refuses every
        // pack, because pay() needs a team to credit and has none. Claiming
        // here is what makes canEdit's "adoptable by anyone" true rather than
        // aspirational.
        if (terminal.teamId() == null) {
            QuestBridge.teamIdFor(player).ifPresent(terminal::setTeamId);
        }

        OptionalLong pinned = QuestBridge.pinnedQuest(player);
        if (pinned.isEmpty()) {
            int count = QuestBridge.pinnedCount(player);
            player.displayClientMessage(Component.translatable(count > 1
                    ? "block.threehourtour.research_terminal.too_many_pinned"
                    : "block.threehourtour.research_terminal.nothing_pinned", count), true);
            return;
        }

        long questId = pinned.getAsLong();
        if (!QuestBridge.hasPayableTasks(questId, true)) {
            player.displayClientMessage(
                    Component.translatable("block.threehourtour.research_terminal.not_payable"), true);
            return;
        }

        terminal.setTarget(questId);
        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.7F, 1.4F);
        QuestBridge.questTitle(questId, true).ifPresent(title -> player.displayClientMessage(
                Component.translatable("block.threehourtour.research_terminal.bound", title), true));
    }

    /**
     * Mirrors the task screen's rule: the owner, or a teammate.
     *
     * An unbound terminal is adoptable by anyone, which is what makes a
     * pack-placed or admin-placed one usable rather than dead.
     */
    private boolean canEdit(Player player, ResearchTerminalBlockEntity terminal) {
        UUID team = terminal.teamId();
        if (team == null) return true;
        return QuestBridge.teamIdFor(player).map(team::equals).orElse(false)
                || FtbTeamResolver.INSTANCE.sameTeam(player.getUUID(), team);
    }
}

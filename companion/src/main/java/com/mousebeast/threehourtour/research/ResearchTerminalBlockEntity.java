package com.mousebeast.threehourtour.research;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.UUID;

/**
 * The research terminal: one block that accepts every research pack type and
 * pays them into whichever research node it is currently targeting.
 *
 * FTB Quests' own task screen binds to a single task, so paying a six-type
 * node means six screens and six pipes. This binds to a whole node instead
 * and lets an arriving stack find its own task, which is the only real
 * difference between the two - the payment itself goes through the same
 * {@code ItemTask.insert} call.
 *
 * <p><b>It stores no progress and no items.</b> Progress lives on the team's
 * quest data, which is why retargeting mid-node costs nothing: leave a node
 * part-paid, research something else, come back, and it is exactly where it
 * was. The block holds two facts - which node, and whose team.
 */
public class ResearchTerminalBlockEntity extends BlockEntity {

    private long targetQuestId;
    private UUID teamId;

    public ResearchTerminalBlockEntity(BlockPos pos, BlockState state) {
        super(ResearchRegistry.RESEARCH_TERMINAL_BE.get(), pos, state);
    }

    public long targetQuestId() {
        return targetQuestId;
    }

    public UUID teamId() {
        return teamId;
    }

    public void setTarget(long questId) {
        this.targetQuestId = questId;
        setChanged();
        sync();
    }

    public void setTeamId(UUID id) {
        this.teamId = id;
        setChanged();
        sync();
    }

    private void sync() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    /**
     * Insert-only, single virtual slot.
     *
     * Single slot because the terminal is not storage - a stack either becomes
     * research progress on arrival or is handed straight back. Insert-only
     * because there is nothing in here to extract; an extract that appeared to
     * work would be automation quietly undoing paid research.
     */
    public IItemHandler itemHandler() {
        return new IItemHandler() {
            @Override public int getSlots() { return 1; }

            @Override public ItemStack getStackInSlot(int slot) { return ItemStack.EMPTY; }

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                if (stack.isEmpty() || level == null || level.isClientSide()) return stack;
                return QuestBridge.pay(targetQuestId, teamId, stack, simulate);
            }

            @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
                return ItemStack.EMPTY;
            }

            @Override public int getSlotLimit(int slot) { return 64; }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                // Answered by simulating a real payment rather than by guessing
                // at what a research pack looks like: the node's own tasks are
                // the authority on what it will take, and they change as the
                // player retargets.
                if (stack.isEmpty() || level == null || level.isClientSide()) return false;
                ItemStack left = QuestBridge.pay(targetQuestId, teamId, stack, true);
                return left.getCount() < stack.getCount();
            }
        };
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("TargetQuest", targetQuestId);
        if (teamId != null) tag.putUUID("TeamId", teamId);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        targetQuestId = tag.getLong("TargetQuest");
        teamId = tag.hasUUID("TeamId") ? tag.getUUID("TeamId") : null;
    }

    /* The client needs both fields: the screen resolves the node's title and
     * live task progress from its own quest file, so nothing about the readout
     * has to be sent field by field. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}

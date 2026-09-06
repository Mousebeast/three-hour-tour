package com.mousebeast.threehourtour.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;
import java.util.UUID;

/**
 * Holds this core's {@link CoreBinding}.
 *
 * A null binding means the core has been placed but never claimed - the state
 * a raft core is in before its owner is stamped on it. A binding with a null
 * vessel id means the core is grounded: its owner is known, its ship is gone.
 */
public class ShipCoreBlockEntity extends BlockEntity {

    private static final String KEY_OWNER  = "Owner";
    private static final String KEY_VESSEL = "Vessel";

    private CoreBinding binding;

    public ShipCoreBlockEntity(BlockPos pos, BlockState state) {
        super(CoreRegistry.SHIP_CORE_BE.get(), pos, state);
    }

    public CoreBinding getBinding() {
        return binding;
    }

    public void setBinding(CoreBinding binding) {
        this.binding = binding;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public static Optional<ShipCoreBlockEntity> at(LevelAccessor level, BlockPos pos) {
        if (level == null || pos == null) return Optional.empty();
        return level.getBlockEntity(pos) instanceof ShipCoreBlockEntity core
                ? Optional.of(core) : Optional.empty();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (binding == null) return;
        tag.putUUID(KEY_OWNER, binding.owner());
        if (binding.vesselId() != null) {
            tag.putUUID(KEY_VESSEL, binding.vesselId());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (!tag.hasUUID(KEY_OWNER)) {
            binding = null;
            return;
        }
        UUID owner  = tag.getUUID(KEY_OWNER);
        UUID vessel = tag.hasUUID(KEY_VESSEL) ? tag.getUUID(KEY_VESSEL) : null;
        binding = new CoreBinding(owner, vessel);
    }
}

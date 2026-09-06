package com.mousebeast.threehourtour.scoop;

import com.mousebeast.threehourtour.ThreeHourTour;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * The scoop's mesh slot: exactly one slot, meshes only.
 *
 * Replaces L1-P5's "read the container above" design. One slot holds one mesh,
 * so the best-tier-wins scan and the double-chest limitation that came with a
 * neighbouring container both cease to exist rather than being fixed.
 *
 * isItemValid is enforced against automation, not just against a player:
 * ItemStackHandler.insertItem checks it before anything else, and hoppers,
 * chutes and vaults all reach this through that same method.
 */
public class MeshSlot extends ItemStackHandler {

    public MeshSlot() {
        super(1);
    }

    /**
     * Is this item a mesh?
     *
     * Takes two strings rather than a ResourceLocation or an ItemStack so it
     * stays reachable from a plain-JUnit test - this module cannot touch
     * BuiltInRegistries outside a loaded game.
     */
    public static boolean isMesh(String namespace, String path) {
        if (namespace == null || path == null) {
            return false;
        }
        return ThreeHourTour.MOD_ID.equals(namespace) && MeshTier.byItemPath(path).isPresent();
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && isMesh(id.getNamespace(), id.getPath());
    }

    /** Meshes are damageable, so they are max-stack-1 anyway; say so explicitly. */
    @Override
    public int getSlotLimit(int slot) {
        return 1;
    }

    /**
     * A view that can be filled but never emptied through the item-handler
     * capability.
     *
     * This is what gets exposed as a block capability. Exposed unwrapped, a
     * hopper under the scoop would extract the mesh the scoop is running on -
     * automation stealing the machine's own tool. Loading meshes in is useful;
     * pulling them out is not, since a spent mesh is consumed rather than
     * ejected.
     *
     * Scoped to this capability only, not a guarantee against every form of
     * automation: a Create Deployer set to right-click with an empty hand
     * goes through the block's own hand-extract path in
     * SeaScoopBlock.useItemOn - the same path a player uses - and would take
     * the mesh out that way. That is acceptable (it is the same interaction
     * a player could perform), just not what this wrapper is for.
     */
    public static IItemHandler insertOnlyView(IItemHandler delegate) {
        return new IItemHandler() {
            @Override
            public int getSlots() {
                return delegate.getSlots();
            }

            @Override
            public ItemStack getStackInSlot(int slot) {
                return delegate.getStackInSlot(slot);
            }

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                return delegate.insertItem(slot, stack, simulate);
            }

            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                return ItemStack.EMPTY;
            }

            @Override
            public int getSlotLimit(int slot) {
                return delegate.getSlotLimit(slot);
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return delegate.isItemValid(slot, stack);
            }
        };
    }
}

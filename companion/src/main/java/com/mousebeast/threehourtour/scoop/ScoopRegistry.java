package com.mousebeast.threehourtour.scoop;

import com.mousebeast.threehourtour.ThreeHourTour;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.Map;

/**
 * Registration for the sea scoop and its mesh items.
 *
 * This is the mirror image of CoreRegistry, and deliberately so. CoreRegistry
 * registers no ITEMS register and no BlockItem because the ship core is
 * placed by the pack itself (the starting raft, the admin command) and never
 * held - a holdable core would silently reopen every exploit the design
 * closes. The scoop is the opposite case: it is ordinary equipment a player
 * crafts, carries and places, so it gets a BlockItem here, and its mesh tiers
 * get durability rather than being a placed fixture. Read the two registries
 * side by side and the asymmetry is the point, not an oversight.
 */
public final class ScoopRegistry {

    private ScoopRegistry() {}

    public static final ResourceLocation SEA_SCOOP_ID =
            ResourceLocation.fromNamespaceAndPath(ThreeHourTour.MOD_ID, "sea_scoop");

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(BuiltInRegistries.BLOCK, ThreeHourTour.MOD_ID);

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(BuiltInRegistries.ITEM, ThreeHourTour.MOD_ID);

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, ThreeHourTour.MOD_ID);

    public static final DeferredHolder<Block, SeaScoopBlock> SEA_SCOOP =
            BLOCKS.register("sea_scoop", () -> new SeaScoopBlock(SeaScoopBlock.scoopProperties()));

    public static final DeferredHolder<Item, Item> SEA_SCOOP_ITEM =
            ITEMS.register("sea_scoop",
                    () -> new BlockItem(SEA_SCOOP.get(), new Item.Properties()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SeaScoopBlockEntity>>
            SEA_SCOOP_BE = BLOCK_ENTITIES.register("sea_scoop", () ->
                    BlockEntityType.Builder.of(SeaScoopBlockEntity::new, SEA_SCOOP.get()).build(null));

    private static final Map<MeshTier, DeferredHolder<Item, Item>> MESHES =
            new EnumMap<>(MeshTier.class);

    static {
        for (MeshTier tier : MeshTier.values()) {
            MESHES.put(tier, ITEMS.register(tier.itemPath(),
                    () -> new Item(new Item.Properties().durability(tier.durability()))));
        }
    }

    public static DeferredHolder<Item, Item> meshItem(MeshTier tier) {
        return MESHES.get(tier);
    }

    /**
     * Exposes the scoop's mesh slot as an insert-only item handler capability.
     *
     * Lives here rather than in ThreeHourTour: SeaScoopBlockEntity.meshHandler()
     * is package-private, and this is the same package. Insert-only because a
     * hopper or chute under the scoop must be able to LOAD a mesh but must
     * never be able to pull one back out - unwrapped, that would be
     * automation stealing the machine's own tool.
     */
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                SEA_SCOOP_BE.get(),
                (be, side) -> MeshSlot.insertOnlyView(be.meshHandler()));
    }
}

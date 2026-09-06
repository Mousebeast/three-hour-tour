package com.mousebeast.threehourtour.material;

import com.mousebeast.threehourtour.ThreeHourTour;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Registration for the treated wood family.
 *
 * Deliberately boring: ordinary vanilla block types with oak-equivalent
 * properties, EXCEPT flammability. Treated wood's ONLY intended advantage is
 * its sable:mass value, which is datapack-driven and appears nowhere in this
 * file (tools/build-block-mass.py). Spec section 11 is explicit that mass is
 * the only hull property this layer touches - do not add fire, blast or
 * hardness bonuses here. A second hidden benefit would leave the tuning pass
 * unable to attribute what it measures.
 *
 * Vanilla registers plank flammability separately from block properties, via
 * FireBlock.setFlammable(block, encouragement, flammability) - oak planks get
 * setFlammable(5, 20) - not through anything on BlockBehaviour.Properties.
 * This mod registers no FireBlock flammability for any of its blocks,
 * treated wood or the sea scoop included, so plankLike()'s .ignitedByLava()
 * has no matching spread/ignite behavior and treated wood will not catch or
 * spread fire the way vanilla oak planks do. That divergence is a real,
 * known gap - effectively a second hidden benefit of the kind this class
 * otherwise forbids - left in deliberately pending an owner decision, not an
 * oversight. Do not register it without one: fire behavior is a gameplay
 * change beyond this layer's scope.
 */
public final class TreatedWoodRegistry {

    private TreatedWoodRegistry() {}

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(BuiltInRegistries.BLOCK, ThreeHourTour.MOD_ID);

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(BuiltInRegistries.ITEM, ThreeHourTour.MOD_ID);

    private static final Map<TreatedWood, DeferredHolder<Block, Block>> BLOCK_HOLDERS =
            new EnumMap<>(TreatedWood.class);

    private static final Map<TreatedWood, DeferredHolder<Item, Item>> ITEM_HOLDERS =
            new EnumMap<>(TreatedWood.class);

    /** Oak-equivalent. Parity is the point; see the class comment. */
    private static BlockBehaviour.Properties plankLike() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BROWN)
                .instrument(NoteBlockInstrument.BASS)
                .strength(2.0F, 3.0F)
                .sound(SoundType.WOOD)
                .ignitedByLava();
    }

    static {
        // Planks first: StairBlock reads a base state at registration time, so
        // the order in this block is load-bearing.
        register(TreatedWood.PLANKS, () -> new Block(plankLike()));
        register(TreatedWood.SLAB, () -> new SlabBlock(plankLike()));
        register(TreatedWood.STAIRS, () -> new StairBlock(
                BLOCK_HOLDERS.get(TreatedWood.PLANKS).get().defaultBlockState(), plankLike()));
    }

    private static void register(TreatedWood variant, Supplier<Block> factory) {
        DeferredHolder<Block, Block> block = BLOCKS.register(variant.itemPath(), factory);
        BLOCK_HOLDERS.put(variant, block);
        ITEM_HOLDERS.put(variant, ITEMS.register(variant.itemPath(),
                () -> new BlockItem(block.get(), new Item.Properties())));
    }

    public static DeferredHolder<Block, Block> block(TreatedWood variant) {
        return BLOCK_HOLDERS.get(variant);
    }

    public static DeferredHolder<Item, Item> item(TreatedWood variant) {
        return ITEM_HOLDERS.get(variant);
    }
}

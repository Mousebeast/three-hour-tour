package com.mousebeast.threehourtour.research;

import com.mousebeast.threehourtour.ThreeHourTour;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
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

/** Registration for the research terminal. */
public final class ResearchRegistry {

    private ResearchRegistry() {}

    public static final ResourceLocation RESEARCH_TERMINAL_ID =
            ResourceLocation.fromNamespaceAndPath(ThreeHourTour.MOD_ID, "research_terminal");

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(BuiltInRegistries.BLOCK, ThreeHourTour.MOD_ID);

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(BuiltInRegistries.ITEM, ThreeHourTour.MOD_ID);

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, ThreeHourTour.MOD_ID);

    public static final DeferredHolder<Block, ResearchTerminalBlock> RESEARCH_TERMINAL =
            BLOCKS.register("research_terminal",
                    () -> new ResearchTerminalBlock(ResearchTerminalBlock.terminalProperties()));

    public static final DeferredHolder<Item, Item> RESEARCH_TERMINAL_ITEM =
            ITEMS.register("research_terminal",
                    () -> new BlockItem(RESEARCH_TERMINAL.get(), new Item.Properties()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ResearchTerminalBlockEntity>>
            RESEARCH_TERMINAL_BE = BLOCK_ENTITIES.register("research_terminal", () ->
                    BlockEntityType.Builder.of(ResearchTerminalBlockEntity::new, RESEARCH_TERMINAL.get()).build(null));

    private static final Map<PackType, DeferredHolder<Item, Item>> PACKS =
            new EnumMap<>(PackType.class);

    static {
        for (PackType type : PackType.values()) {
            PACKS.put(type, ITEMS.register(type.itemPath(),
                    () -> new Item(borrowedTexture())));
        }
    }

    public static DeferredHolder<Item, Item> packItem(PackType type) {
        return PACKS.get(type);
    }

    private static final Map<PackType, DeferredHolder<Item, Item>> BUNDLES =
            new EnumMap<>(PackType.class);

    static {
        for (PackType type : PackType.values()) {
            BUNDLES.put(type, ITEMS.register(type.bundlePath(),
                    () -> new Item(borrowedTexture())));
        }
    }

    public static DeferredHolder<Item, Item> bundleItem(PackType type) {
        return BUNDLES.get(type);
    }

    private static final Map<PackType, DeferredHolder<Item, Item>> CORES =
            new EnumMap<>(PackType.class);

    static {
        for (PackType type : PackType.values()) {
            CORES.put(type, ITEMS.register(type.corePath(),
                    () -> new Item(borrowedTexture())));
        }
    }

    public static DeferredHolder<Item, Item> coreItem(PackType type) {
        return CORES.get(type);
    }

    /**
     * The gateway currency. Every gateway (Layer 4 Plan 6) pays this out on
     * completion; the datapack files that pay it name the id as a string,
     * never this field.
     */
    public static final DeferredHolder<Item, Item> ASH =
            ITEMS.register("ash", () -> new Item(borrowedTexture()));

    /**
     * Sigil currency for the three factions that live behind closed
     * dimensions (Layer 4 Plan 6, Task 12): a grade-3 Hunting Ground clear
     * pays exactly one of these, and it is the faction key of the one
     * faction that sigil unlocks. The pairing (which faction pays which
     * sigil, which faction keys on it) lives in docs/factions.json, never
     * here -- this is registration only.
     */
    public static final DeferredHolder<Item, Item> FIRE_SIGIL =
            ITEMS.register("fire_sigil", () -> new Item(borrowedTexture()));

    public static final DeferredHolder<Item, Item> SOUL_SIGIL =
            ITEMS.register("soul_sigil", () -> new Item(borrowedTexture()));

    public static final DeferredHolder<Item, Item> VOID_SIGIL =
            ITEMS.register("void_sigil", () -> new Item(borrowedTexture()));

    /**
     * One trophy per boss in the twelve-boss pools (docs/factions.json,
     * namedGates). Ten are paid by their own Named Gate on completion; the
     * Leviathan's and Scylla's come from a loot modifier instead, because
     * those two are killed at sea and have no gate to pay them.
     *
     * Distinct per boss on purpose (L4-51). The pools are made fungible one
     * step later, by the seals below -- FTB Quests cannot demand "one item
     * from a tag" without a filter mod, and this pack has none.
     */
    public static final Map<String, DeferredHolder<Item, Item>> TROPHIES =
            registerAll("trophy_",
                    "wither", "ignis", "monstrosity",
                    "dead_king", "fire_boss", "harbinger",
                    "ancient_remnant", "ender_guardian", "leviathan",
                    "maledictus", "scylla", "ender_dragon");

    /**
     * One seal per trophy pool. Any trophy in a pool crafts one-for-one into
     * that pool's seal, and research nodes demand seals. The four seals are
     * mutually non-substitutable, which is what stops a node that asks for
     * one of each from being satisfied by farming a single easy boss.
     */
    public static final Map<String, DeferredHolder<Item, Item>> SEALS =
            registerAll("seal_", "alpha", "beta", "gamma", "delta");

    /**
     * Item properties for one of ours that wears a vanilla texture.
     *
     * Twenty-two of our items are drawn with a vanilla texture as a
     * placeholder -- the Foundry pack is a brick, its bundle a clay ball, its
     * core a copper ingot -- and in an inventory beside the real thing they
     * are indistinguishable. The permanent glint is what separates them: it
     * says "this looks vanilla and is not" at a glance, with no texture work
     * and no gameplay meaning. It is cosmetic only; the item carries no
     * enchantment and behaves on an anvil exactly as it did.
     *
     * The items that already have a texture of ours -- the trophies, the
     * seals, the meshes -- deliberately do NOT get one. A glint on everything
     * would stop meaning anything. When a placeholder gains a real texture,
     * this call comes off with it.
     */
    private static Item.Properties borrowedTexture() {
        return new Item.Properties()
                .component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
    }

    private static Map<String, DeferredHolder<Item, Item>> registerAll(
            String prefix, String... keys) {
        Map<String, DeferredHolder<Item, Item>> out = new LinkedHashMap<>();
        for (String key : keys) {
            out.put(key, ITEMS.register(prefix + key,
                    () -> new Item(new Item.Properties())));
        }
        // Map.copyOf does not preserve iteration order, and this map is
        // iterated to populate the creative tab -- an unmodifiable view over
        // the LinkedHashMap keeps that order stable across runs instead of
        // Map.copyOf's arbitrary one.
        return Collections.unmodifiableMap(out);
    }

    /**
     * Exposed on every face: a research terminal is fed by whatever the player
     * has to hand - a chute from above, a Create funnel from the side, a
     * hopper from below - and there is no side on which accepting a pack would
     * mean something different.
     */
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                RESEARCH_TERMINAL_BE.get(),
                (be, side) -> be.itemHandler());
    }
}

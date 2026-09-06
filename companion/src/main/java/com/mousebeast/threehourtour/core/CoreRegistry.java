package com.mousebeast.threehourtour.core;

import com.mousebeast.threehourtour.ThreeHourTour;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registers the ship core.
 *
 * NOTE: there is intentionally no ITEMS register and no BlockItem here. The
 * core is placed by the pack (the starting raft, and the admin command), never
 * held. Adding a BlockItem would silently reopen every exploit the design
 * closes - see the spec, §3.
 */
public final class CoreRegistry {
    private CoreRegistry() {}

    public static final ResourceLocation SHIP_CORE_ID =
            ResourceLocation.fromNamespaceAndPath(ThreeHourTour.MOD_ID, "ship_core");

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(BuiltInRegistries.BLOCK, ThreeHourTour.MOD_ID);

    public static final DeferredHolder<Block, ShipCoreBlock> SHIP_CORE =
            BLOCKS.register("ship_core", () -> new ShipCoreBlock(ShipCoreBlock.coreProperties()));

    public static final DeferredRegister<net.minecraft.world.level.block.entity.BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, ThreeHourTour.MOD_ID);

    public static final DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>,
            net.minecraft.world.level.block.entity.BlockEntityType<ShipCoreBlockEntity>> SHIP_CORE_BE =
            BLOCK_ENTITIES.register("ship_core", () ->
                    net.minecraft.world.level.block.entity.BlockEntityType.Builder
                            .of(ShipCoreBlockEntity::new, SHIP_CORE.get())
                            .build(null));
}

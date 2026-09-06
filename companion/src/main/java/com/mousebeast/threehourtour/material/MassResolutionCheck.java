package com.mousebeast.threehourtour.material;

import com.mousebeast.threehourtour.ThreeHourTour;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * L3-16. An inert mass datapack is completely silent.
 *
 * Same reasoning as ShipOnlyPlacement.onServerStarted: if the pack fails to load
 * - misspelled directory, bad namespace, Paxi not injecting it - nothing errors,
 * no ship sinks, and every unit test stays green. The world simply behaves as
 * though Layer 3 was never built, and the first hint would be a player wondering
 * why treating their hull changed nothing.
 *
 * We cannot read sable:mass from here without depending on Sable internals, so
 * this asserts the two things visible cheaply that cover the realistic failures:
 * our blocks exist, and they are in #minecraft:planks as L3-08 requires. A mass
 * value present but wrong is caught by BlockMassDatapackTest at build time and
 * by Step 5 below in game.
 */
@EventBusSubscriber(modid = ThreeHourTour.MOD_ID)
public final class MassResolutionCheck {

    private MassResolutionCheck() {}

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        for (TreatedWood variant : TreatedWood.values()) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                    ThreeHourTour.MOD_ID, variant.itemPath());
            if (!BuiltInRegistries.BLOCK.containsKey(id)) {
                ThreeHourTour.LOG.error("{} is not registered - the treated family is incomplete", id);
            }
        }

        if (!TreatedWoodRegistry.block(TreatedWood.PLANKS).get()
                .defaultBlockState().is(BlockTags.PLANKS)) {
            ThreeHourTour.LOG.error(
                    "treated_planks is NOT in #minecraft:planks - pack-wide plank recipes will "
                    + "reject it (L3-08), and its mass override may not be reversing the heavy rule");
        } else {
            ThreeHourTour.LOG.info(
                    "Treated wood registered and tagged; mass ladder datapack expected active");
        }
    }
}

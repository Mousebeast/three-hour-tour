package com.mousebeast.threehourtour.research;

import com.mousebeast.threehourtour.ThreeHourTour;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Binds the terminal's face renderer, client side only.
 *
 * <p>Separated from {@link ResearchTerminalClient} because that class is a
 * plain helper called behind an {@code isClientSide()} guard, whereas this one
 * subscribes to the mod event bus and so must be prevented from loading on a
 * dedicated server by the annotation rather than by a caller. Same reason
 * {@code GuideKeybind} is structured this way.
 */
@EventBusSubscriber(modid = ThreeHourTour.MOD_ID, value = Dist.CLIENT)
public final class ResearchTerminalClientHooks {

    private ResearchTerminalClientHooks() {}

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ResearchRegistry.RESEARCH_TERMINAL_BE.get(),
                                          ResearchTerminalRenderer::new);
    }
}

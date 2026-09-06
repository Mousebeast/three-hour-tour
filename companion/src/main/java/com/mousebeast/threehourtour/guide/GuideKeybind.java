package com.mousebeast.threehourtour.guide;

import com.mousebeast.threehourtour.ThreeHourTour;

import guideme.Guide;
import guideme.Guides;
import guideme.internal.GuideMEClient;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import org.lwjgl.glfw.GLFW;

/**
 * A key that opens the guidebook, with no book item to carry.
 *
 * <p>GuideME binds {@code G} held over an item tooltip to that item's page,
 * which is the best thing either guidebook library offers — but it only works
 * when something is under the cursor, and a data-driven guide has no field for
 * binding itself to a key. The owner wanted the book reachable from standing in
 * the world with nothing in hand (2026-09-01), and this is the twenty lines that
 * do it.
 *
 * <p><b>Why this is the only client code in the pack.</b> Choosing GuideME over
 * Patchouli was partly because a guide needs no code at all, and the argument
 * against writing client code was that nothing here could verify it. That
 * argument died when we learned a GuideME guide lives entirely in {@code
 * assets/} — a dedicated server never loads it, so the whole book is
 * client-verified regardless. What is left is a keybind, which is small.
 *
 * <p>{@code GuideMEClient} is in an {@code internal} package. It is the only
 * public way to open a guide, {@code Guides} being the API half; if a GuideME
 * update moves it, this class is where the breakage lands and the fix is a
 * one-line retarget.
 */
@EventBusSubscriber(modid = ThreeHourTour.MOD_ID, value = Dist.CLIENT)
public final class GuideKeybind {

    /** The guide declared in {@code assets/threehourtour/guideme_guides/}. */
    public static final ResourceLocation GUIDE_ID =
            ResourceLocation.fromNamespaceAndPath(ThreeHourTour.MOD_ID, "guide");

    /**
     * The page opened when the player has never opened the book. Page ids are
     * the guide's namespace plus the file's path under the guide root, so the
     * index at {@code guides/threehourtour/guide/index.md} is
     * {@code threehourtour:index.md} — the {@code .md} is part of the id.
     */
    public static final ResourceLocation INDEX_PAGE =
            ResourceLocation.fromNamespaceAndPath(ThreeHourTour.MOD_ID, "index.md");

    /**
     * Default {@code H}, for handbook. Unbound in vanilla, and next to
     * GuideME's own {@code G} so the two guide keys sit together. Rebindable
     * from Controls like any other; this only sets where it starts.
     */
    private static final int DEFAULT_KEY = GLFW.GLFW_KEY_H;

    private static KeyMapping openGuide;

    private GuideKeybind() {
    }

    /**
     * NeoForge 21.1 routes a subscriber by the event's own bus, so this and
     * {@link #onClientTick} live in one class even though one is a mod-bus
     * event and the other is a game-bus event. Naming the bus explicitly is
     * deprecated and would warn.
     */
    @SubscribeEvent
    static void register(RegisterKeyMappingsEvent event) {
        openGuide = new KeyMapping(
                "key.threehourtour.open_guide",
                KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM,
                DEFAULT_KEY,
                "key.categories.threehourtour");
        event.register(openGuide);
    }

    /**
     * A tap, not a hold. GuideME's tooltip hotkey is held because the cursor
     * may be over an item by accident; standing in the world and pressing a key
     * is deliberate, so making the player wait for a progress bar would be
     * friction with nothing to prevent.
     */
    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        if (openGuide == null) {
            return;
        }
        // consumeClick drains the queued presses, so holding the key opens the
        // book once rather than every tick.
        boolean pressed = false;
        while (openGuide.consumeClick()) {
            pressed = true;
        }
        if (!pressed) {
            return;
        }
        Guide guide = Guides.getById(GUIDE_ID);
        if (guide == null) {
            // The guide is a resource pack. A resource pack reload that drops
            // it, or a client running without our assets, leaves nothing to
            // open -- say nothing rather than throwing on a keypress.
            return;
        }
        GuideMEClient.openGuideAtPreviousPage(guide, INDEX_PAGE);
    }
}

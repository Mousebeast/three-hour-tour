package com.mousebeast.threehourtour.research;

import com.mousebeast.threehourtour.ThreeHourTour;

import dev.ftb.mods.ftblibrary.ui.ScreenWrapper;
import dev.ftb.mods.ftbquests.client.gui.quests.QuestScreen;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Opens the research tree zoomed out, once per game session.
 *
 * <p>FTB Quests starts at zoom 16 on a 4-to-28 scale, which frames three or
 * four nodes. This tree is a graph whose shape is the point -- what leads to
 * what, and how far the branch you are on runs -- and a first-time player who
 * opens it to a close-up of one node has been shown a list. Zoomed out they see
 * the map.
 *
 * <p><b>There is no setting for this.</b> The 16 is a literal in
 * {@code QuestScreen}'s constructor; it is not in any config file, not on the
 * quest file, and not on a chapter. But it does not need a mixin either:
 * {@code addZoom} is public and clamps to the 4-to-28 range itself, so a large
 * negative simply lands on the minimum.
 *
 * <p><b>Once per session, deliberately.</b> FTB Quests remembers zoom in its
 * own per-session state and restores it every time the screen reopens, so
 * applying this on every open would drag a player back out of a view they had
 * chosen every time they closed and reopened the book. First open sets the
 * frame; after that the zoom is theirs.
 */
@EventBusSubscriber(modid = ThreeHourTour.MOD_ID, value = Dist.CLIENT)
public final class QuestScreenZoom {

    /** Below the 4 that {@code addZoom} clamps to, so it lands on the minimum. */
    private static final double FULLY_OUT = -1000.0;

    private static boolean framed = false;

    private QuestScreenZoom() {}

    @SubscribeEvent
    public static void frame(ScreenEvent.Init.Post event) {
        // FTB's screens are not Minecraft screens. QuestScreen extends FTB
        // Library's own BaseScreen, and what Minecraft is handed -- and what
        // this event reports -- is a ScreenWrapper around it. Testing the
        // event's screen against QuestScreen directly does not compile, and
        // testing it against Screen would have matched nothing at runtime.
        if (framed
                || !(event.getScreen() instanceof ScreenWrapper wrapper)
                || !(wrapper.getGui() instanceof QuestScreen screen)) {
            return;
        }
        framed = true;
        screen.addZoom(FULLY_OUT);
    }
}

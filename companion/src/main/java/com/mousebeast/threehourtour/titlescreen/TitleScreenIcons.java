package com.mousebeast.threehourtour.titlescreen;

import com.mousebeast.threehourtour.ThreeHourTour;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Puts the language and accessibility icons where the title screen wants them.
 *
 * <p><b>Why this is not in the FancyMenu layout, where everything else is.</b>
 * FancyMenu identifies a vanilla widget by a name from a per-screen table, and
 * although its table lists both of these buttons under
 * {@code mc_titlescreen_language_button} and
 * {@code mc_titlescreen_accessibility_button}, neither matches at runtime — the
 * only two widgets on the screen that do not. When it cannot name a widget it
 * falls back to identifying it by position: {@code generateBaseId} concatenates
 * the widget's x and y in GUI coordinates at the moment of discovery. That id is
 * a captured constant, so it is correct on the machine that captured it and
 * matches nothing anywhere else, silently. Every mod that adds a button to this
 * screen was checked (fifteen reference it; three inject into it, and none of
 * those three touch these two), so it is not something in the pack rebuilding
 * them.
 *
 * <p>Doing it here instead is not a workaround, it is the better tool: the
 * positions below are a formula over the live screen size rather than a number
 * captured at one resolution, so they hold at every window size and GUI scale.
 * The two icons sit centred as a pair against the bottom edge, clear of the
 * menu column on the right and of the branding lines on the left.
 *
 * <p>Placement was authored by dragging them in FancyMenu's editor at
 * 640x331 (GUI scale 3) and reading the result out of the saved layout, which
 * is why the offsets are the odd numbers they are: the pair spans the screen
 * centre with a 20-pixel gap, twenty-three pixels up from the bottom.
 */
@EventBusSubscriber(modid = ThreeHourTour.MOD_ID, value = Dist.CLIENT)
public final class TitleScreenIcons {

    private static final String LANGUAGE_KEY = "narrator.button.language";
    private static final String ACCESSIBILITY_KEY = "narrator.button.accessibility";

    /** Both icons are 20x20; nothing else vanilla puts on this row is. */
    private static final int ICON = 20;

    /** Where vanilla puts them, as offsets from the screen centre. */
    private static final int VANILLA_LANGUAGE_X = -124;
    private static final int VANILLA_ACCESSIBILITY_X = 104;

    /** Where we want them, as offsets from the screen centre. */
    private static final int LANGUAGE_X = -30;
    private static final int ACCESSIBILITY_X = 10;
    private static final int Y_FROM_BOTTOM = 23;

    private TitleScreenIcons() {}

    @SubscribeEvent
    public static void place(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof TitleScreen screen)) {
            return;
        }
        int centre = screen.width / 2;
        int y = screen.height - Y_FROM_BOTTOM;

        AbstractWidget language = byKey(event, LANGUAGE_KEY);
        AbstractWidget accessibility = byKey(event, ACCESSIBILITY_KEY);

        // The fallback exists because FancyMenu's key match already fails on
        // these two for reasons we could not read off a dedicated server, so
        // ours might as well. It is a formula over the live width, not a
        // captured coordinate -- vanilla lays the screen out immediately before
        // this event, and re-lays it out on every resize, so the buttons are
        // always back at these positions when we look.
        if (language == null) {
            language = bySize(event, centre + VANILLA_LANGUAGE_X);
        }
        if (accessibility == null) {
            accessibility = bySize(event, centre + VANILLA_ACCESSIBILITY_X);
        }

        if (language != null) {
            language.setX(centre + LANGUAGE_X);
            language.setY(y);
        }
        if (accessibility != null) {
            accessibility.setX(centre + ACCESSIBILITY_X);
            accessibility.setY(y);
        }
    }

    private static AbstractWidget byKey(ScreenEvent.Init event, String key) {
        for (GuiEventListener listener : event.getListenersList()) {
            if (listener instanceof AbstractWidget widget
                    && key.equals(translationKey(widget.getMessage()))) {
                return widget;
            }
        }
        return null;
    }

    private static AbstractWidget bySize(ScreenEvent.Init event, int x) {
        for (GuiEventListener listener : event.getListenersList()) {
            if (listener instanceof AbstractWidget widget
                    && widget.getWidth() == ICON && widget.getHeight() == ICON
                    && widget.getX() == x) {
                return widget;
            }
        }
        return null;
    }

    private static String translationKey(Component component) {
        return component != null && component.getContents() instanceof TranslatableContents contents
                ? contents.getKey()
                : null;
    }
}

package com.mousebeast.threehourtour.opening;

import com.mousebeast.threehourtour.ThreeHourTour;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Four lines, once, the first time a player joins a world.
 *
 * <p>The raft does not explain itself, and two of the four things a player has
 * to do are invisible until done. <b>The lever is first because it is the only
 * one that fails silently:</b> the sea scoop yields nothing at all unless the
 * vessel is under way, so a player who never assembles and never moves gets no
 * kelp, no clay, no string, and therefore no research, with nothing anywhere
 * saying why. The chest, the guidebook and the terminal are all things a player
 * finds by looking around; the lever is a thing they find by being told.
 *
 * <p><b>Once, not every login.</b> A message read forty times is noise, and
 * everything in it is also in the guidebook, which is the thing to reach for on
 * the fortieth day. The flag lives in the player's persisted data, so it
 * survives death and dimension changes as well as relogging.
 *
 * <p><b>The key is a keybind component, not the letter H.</b> The binding is
 * client-side and this message is built on the server, so naming a key here
 * would be a guess that goes stale the moment anyone rebinds it.
 * {@link Component#keybind} is resolved by the client against its own bindings.
 */
@EventBusSubscriber(modid = ThreeHourTour.MOD_ID)
public final class WelcomeMessage {

    /** Under the player's persisted tag, which survives death; a plain
     *  persistent-data key does not. */
    private static final String SEEN = "threehourtour_welcomed";

    private static final String[] LINES = {
        "threehourtour.welcome.lever",
        "threehourtour.welcome.chest",
        "threehourtour.welcome.guide",
        "threehourtour.welcome.terminal",
    };

    private static final String GUIDE_LINE = "threehourtour.welcome.guide";
    private static final String GUIDE_KEY = "key.threehourtour.open_guide";

    private WelcomeMessage() {}

    @SubscribeEvent
    public static void onJoin(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        CompoundTag persisted = player.getPersistentData()
                                      .getCompound(Player.PERSISTED_NBT_TAG);
        if (persisted.getBoolean(SEEN)) {
            return;
        }
        persisted.putBoolean(SEEN, true);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);

        player.sendSystemMessage(Component.translatable("threehourtour.welcome.title")
                                          .withStyle(ChatFormatting.GOLD));
        for (String line : LINES) {
            Component body = GUIDE_LINE.equals(line)
                    ? Component.translatable(line, Component.keybind(GUIDE_KEY)
                                                            .withStyle(ChatFormatting.WHITE))
                    : Component.translatable(line);
            player.sendSystemMessage(Component.literal("  ").append(body)
                                              .withStyle(ChatFormatting.GRAY));
        }
    }
}

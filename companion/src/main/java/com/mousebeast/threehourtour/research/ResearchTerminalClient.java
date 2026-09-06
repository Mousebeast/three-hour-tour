package com.mousebeast.threehourtour.research;

import com.mousebeast.threehourtour.ThreeHourTour;

/**
 * Client-only entry points for the research terminal.
 *
 * Kept in its own class so a dedicated server never loads it: the only caller
 * is guarded by {@code level.isClientSide()}, which is never true there, so
 * the FTB Quests client class named below is never resolved on a server that
 * does not ship it.
 */
public final class ResearchTerminalClient {

    private ResearchTerminalClient() {}

    /**
     * Opens the quest book, exactly as the book item does.
     *
     * The terminal has no picker of its own on purpose - this is the picker,
     * and it is the one that already knows how to draw a tree.
     */
    public static void openQuestBook() {
        try {
            dev.ftb.mods.ftbquests.client.FTBQuestsClient.openGui();
        } catch (Throwable t) {
            // FTB Quests absent or its client entry point moved. Nothing to
            // open is a survivable outcome; a crash on right-click is not.
            ThreeHourTour.LOG.warn("Could not open the quest book from a research terminal", t);
        }
    }
}

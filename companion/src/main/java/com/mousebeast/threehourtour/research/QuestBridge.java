package com.mousebeast.threehourtour.research;

import dev.ftb.mods.ftbquests.api.FTBQuestsAPI;
import dev.ftb.mods.ftbquests.quest.BaseQuestFile;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.ItemTask;
import dev.ftb.mods.ftbquests.quest.task.Task;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import com.mousebeast.threehourtour.ThreeHourTour;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Every point where the research terminal touches FTB Quests.
 *
 * FTB Quests is an OPTIONAL dependency, so this follows the same shape as
 * {@code FtbTeamResolver}: guard on {@link ModList}, then catch Throwable so a
 * missing class fails quiet instead of crashing a block that ticks. Nothing
 * here leaks an FTB type through a public signature - the terminal, its
 * screen, and the routing rule all speak in this class's own records, which is
 * what keeps the rest of the mod compilable and testable without a quest file.
 *
 * <p><b>Version coupling.</b> Only {@code dev.ftb.mods.ftbquests.api} is
 * declared API. {@link ItemTask}, {@link TeamData}, {@link Quest} and
 * {@link BaseQuestFile} are internal and can change between FTB Quests
 * releases. That is survivable only because {@code pack/manifest.json} pins an
 * exact file id, so a version bump is a deliberate act with a re-verification
 * attached - the same posture the mod already runs with Sable.
 */
public final class QuestBridge {

    private QuestBridge() {}

    private static final String FTBQUESTS = "ftbquests";

    /** Call sites that have already logged a swallowed throwable. */
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    /** One task of a node, flattened for display. */
    public record TaskView(Component title, ItemStack icon, long progress, long max) {
        public boolean complete() {
            return progress >= max;
        }
    }

    /** A node as the terminal needs to show it. */
    public record NodeView(long questId, Component title, List<TaskView> tasks) {}

    public static boolean available() {
        try {
            return ModList.get().isLoaded(FTBQUESTS);
        } catch (Throwable t) {
            warnOnce("available", t);
            return false;
        }
    }

    /**
     * The quest file for a side.
     *
     * <p><b>The API's parameter is {@code isClientSide}, not "server side" -
     * it is inverted here.</b> {@code FTBQuestsAPIImpl.getQuestFile(true)}
     * returns {@code FTBQuestsClient.getClientQuestFile()}; {@code false}
     * returns {@code ServerQuestFile.INSTANCE}. FTB Quests' own
     * {@code TaskScreenBlockEntity} passes {@code level.isClientSide} straight
     * in, which is the convention this mirrors.
     *
     * <p>Getting this backwards asked a dedicated server for a client-only
     * class on every call, and the catch blocks below turned each resulting
     * throwable into a polite empty answer - so the terminal refused every
     * bind and credited every pack to nothing, silently. That is why those
     * catches now log.
     */
    private static BaseQuestFile file(boolean serverSide) {
        return FTBQuestsAPI.api().getQuestFile(!serverSide);
    }

    /**
     * Logs the first throwable each call site swallows, then stays quiet.
     *
     * The catches exist so a missing FTB Quests cannot crash a block that
     * ticks - but a missing mod never reaches them, because {@link #available()}
     * stops it first. Anything that arrives here is therefore a bug, and the
     * silence is what let one live through a deploy. Once per site, so a
     * per-tick failure cannot flood the log.
     */
    private static void noteOnce(String where, String why) {
        if (WARNED.add(where)) {
            ThreeHourTour.LOG.warn("Research terminal: refusing packs - {}", why);
        }
    }

    private static void warnOnce(String where, Throwable t) {
        if (WARNED.add(where)) {
            ThreeHourTour.LOG.warn("Research terminal: {} failed against FTB Quests - "
                    + "this call will keep returning its empty answer", where, t);
        }
    }

    /**
     * The team id to store on a terminal when a player places it.
     *
     * Stored rather than resolved per insertion because a pipe is not a
     * player: by the time a pack arrives there is nobody to ask which team
     * should be credited.
     */
    public static Optional<UUID> teamIdFor(Player placer) {
        if (!available() || placer == null) return Optional.empty();
        try {
            return Optional.ofNullable(file(true).getOrCreateTeamData(placer)).map(TeamData::getTeamId);
        } catch (Throwable t) {
            warnOnce("teamIdFor", t);
            return Optional.empty();
        }
    }

    /**
     * The single quest this player has pinned in the quest book.
     *
     * Empty when none is pinned, and also when several are: a LongSet has no
     * order, so "the most recent" is not a question that can be answered
     * honestly. Refusing and saying so beats binding to an arbitrary one.
     * {@code AUTO_PIN_ID} is FTB Quests' own tracker sentinel, not a quest.
     */
    public static OptionalLong pinnedQuest(Player player) {
        if (!available() || player == null) return OptionalLong.empty();
        try {
            TeamData data = file(true).getOrCreateTeamData(player);
            if (data == null) return OptionalLong.empty();
            LongSet pinned = data.getPinnedQuestIds(player);
            if (pinned == null) return OptionalLong.empty();
            long found = 0L;
            int count = 0;
            for (LongIterator it = pinned.iterator(); it.hasNext(); ) {
                long id = it.nextLong();
                if (id == TeamData.AUTO_PIN_ID) continue;
                found = id;
                count++;
            }
            return count == 1 ? OptionalLong.of(found) : OptionalLong.empty();
        } catch (Throwable t) {
            warnOnce("pinnedQuest", t);
            return OptionalLong.empty();
        }
    }

    /** How many non-sentinel quests this player has pinned - for the "pin exactly one" message. */
    public static int pinnedCount(Player player) {
        if (!available() || player == null) return 0;
        try {
            TeamData data = file(true).getOrCreateTeamData(player);
            if (data == null) return 0;
            LongSet pinned = data.getPinnedQuestIds(player);
            if (pinned == null) return 0;
            int count = 0;
            for (LongIterator it = pinned.iterator(); it.hasNext(); ) {
                if (it.nextLong() != TeamData.AUTO_PIN_ID) count++;
            }
            return count;
        } catch (Throwable t) {
            warnOnce("pinnedCount", t);
            return 0;
        }
    }

    /**
     * Whether a node can be paid into at all.
     *
     * A node whose tasks are all checkmarks or advancements can never accept a
     * research pack, so binding a terminal to it would leave the player piping
     * into a block that silently refuses everything. Better to refuse the bind
     * and say why.
     */
    public static boolean hasPayableTasks(long questId, boolean serverSide) {
        if (!available()) return false;
        try {
            Quest quest = file(serverSide).getQuest(questId);
            if (quest == null) return false;
            for (Task task : quest.getTasksAsList()) {
                if (task instanceof ItemTask item && item.consumesResources()) return true;
            }
            return false;
        } catch (Throwable t) {
            warnOnce("hasPayableTasks", t);
            return false;
        }
    }

    public static Optional<Component> questTitle(long questId, boolean serverSide) {
        if (!available() || questId == 0L) return Optional.empty();
        try {
            Quest quest = file(serverSide).getQuest(questId);
            return quest == null ? Optional.empty() : Optional.of(quest.getTitle());
        } catch (Throwable t) {
            warnOnce("questTitle", t);
            return Optional.empty();
        }
    }

    /**
     * The node's tasks with live progress, for the terminal's readout.
     *
     * Works on either side because both hold a quest file and the team's
     * progress - the client's copy is what the quest book itself draws from,
     * so the terminal's numbers and the book's numbers cannot disagree.
     */
    public static Optional<NodeView> nodeView(long questId, UUID teamId, boolean serverSide) {
        if (!available() || questId == 0L) return Optional.empty();
        try {
            BaseQuestFile questFile = file(serverSide);
            Quest quest = questFile.getQuest(questId);
            if (quest == null) return Optional.empty();
            TeamData data = teamId == null ? null : questFile.getNullableTeamData(teamId);
            List<TaskView> views = new ArrayList<>();
            for (Task task : quest.getTasksAsList()) {
                if (!(task instanceof ItemTask item)) continue;
                long progress = data == null ? 0L : data.getProgress(task);
                views.add(new TaskView(task.getTitle(), item.getItemStack(), progress, task.getMaxProgress()));
            }
            return Optional.of(new NodeView(questId, quest.getTitle(), views));
        } catch (Throwable t) {
            warnOnce("nodeView", t);
            return Optional.empty();
        }
    }

    /**
     * Pays a stack into the targeted node and returns whatever would not fit.
     *
     * This is the whole payment path. {@code ItemTask.insert} is the same call
     * FTB Quests' own task screen makes, so completion, rewards and the
     * consume-items rule all behave identically - and, checked in the jar, it
     * does NOT enforce {@code task_screen_only}, which is read only on the
     * manual submit path. That is what lets the pack keep that flag on to
     * block hand-submission from the book while this block still works.
     *
     * <p>Returning the remainder rather than voiding it is deliberate and
     * matches the task screen: a jammed pipe is recoverable, an eaten stack of
     * research packs is not.
     */
    public static ItemStack pay(long questId, UUID teamId, ItemStack stack, boolean simulate) {
        if (!available() || questId == 0L || stack == null || stack.isEmpty()) return stack;
        try {
            BaseQuestFile questFile = file(true);
            Quest quest = questFile.getQuest(questId);
            if (quest == null) return stack;
            TeamData data = teamId == null ? null : questFile.getNullableTeamData(teamId);
            if (data == null) {
                // A terminal with no team credits nobody, so every pack is
                // refused and a pipe simply stops moving - which looks exactly
                // like a full inventory from the outside. Say so once.
                noteOnce("pay:no-team", "the terminal has no team to credit - sneak + right-click it to claim it");
                return stack;
            }
            if (data.isCompleted(quest) || !data.canStartTasks(quest)) return stack;

            List<ItemTask> tasks = new ArrayList<>();
            for (Task task : quest.getTasksAsList()) {
                if (task instanceof ItemTask item) tasks.add(item);
            }

            List<ResearchRouting.Sink> sinks = new ArrayList<>(tasks.size());
            for (ItemTask task : tasks) {
                final boolean accepts = task.consumesResources()
                        && !data.isCompleted(task)
                        && task.test(stack);
                final long remaining = task.getMaxProgress() - data.getProgress(task);
                sinks.add(new ResearchRouting.Sink() {
                    @Override public boolean accepts() { return accepts; }
                    @Override public long remaining() { return remaining; }
                });
            }

            var chosen = ResearchRouting.choose(sinks);
            if (chosen.isEmpty()) return stack;

            int index = chosen.getAsInt();
            int accepted = ResearchRouting.acceptedAmount(sinks.get(index), stack.getCount());
            if (accepted <= 0) return stack;
            if (simulate) {
                ItemStack left = stack.copy();
                left.shrink(accepted);
                return left;
            }
            // insert() takes what it wants and hands back the rest itself, so
            // hand it only the portion this task can use and keep the
            // remainder here - splitting first keeps the arithmetic in one
            // place instead of trusting two subtractions to agree.
            ItemStack offer = stack.copyWithCount(accepted);
            ItemStack refused = tasks.get(index).insert(data, offer, false);
            ItemStack left = stack.copy();
            left.shrink(accepted - refused.getCount());
            return left;
        } catch (Throwable t) {
            warnOnce("pay", t);
            return stack;
        }
    }
}

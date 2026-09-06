package com.mousebeast.threehourtour.research;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import org.joml.Matrix4f;

import java.util.List;
import java.util.Optional;

/**
 * Draws the pinned research node and its task progress on the terminal's face.
 *
 * <p>Why this exists: the terminal already knew all of this and would only say
 * it in chat, one line at a time, when you sneak-used it. A block whose whole
 * job is "what am I researching and how far along am I" should answer that by
 * being looked at.
 *
 * <p><b>Client only.</b> A dedicated server never loads this class -- it is
 * referenced solely from {@link ResearchTerminalClientHooks}, which is bound to
 * {@code Dist.CLIENT}. That matters because it resolves FTB Quests' client-side
 * quest file through {@link QuestBridge}, and because {@code Font} and
 * {@code ItemRenderer} do not exist on a server at all.
 *
 * <p><b>Another team's terminal reads zero, not wrong.</b> The view is looked
 * up with {@code getNullableTeamData(teamId)}, and a client holds only its own
 * team's data, so a terminal bound to someone else shows {@code 0/n} rather
 * than borrowing the viewer's numbers. That is the benign failure and it is
 * only reachable while standing at a terminal the block already refuses to let
 * you use.
 *
 * <p><b>Why the cache.</b> {@link QuestBridge#nodeView} walks FTB Quests' task
 * and progress structures. A renderer runs per frame per visible block entity,
 * so calling it directly would do that work a hundred times a second for a
 * block that changes state a few times a minute. The view is refreshed on an
 * interval and whenever the synced quest id changes.
 */
public class ResearchTerminalRenderer implements BlockEntityRenderer<ResearchTerminalBlockEntity> {

    /** Past this, the text is unreadable anyway and we skip the work entirely. */
    private static final double VISIBLE_RANGE_SQ = 12.0 * 12.0;

    /** Ticks between refreshes of the cached view. Progress is not urgent. */
    private static final long REFRESH_TICKS = 20L;

    /**
     * The face is treated as an {@value #CANVAS}-unit square and everything is
     * laid out in those units, so the numbers below read as pixels on a panel
     * rather than fractions of a block.
     */
    /** Font pixels per block. Sets how big the text is, nothing else. */
    private static final int UNITS_PER_BLOCK = 80;
    private static final float SCALE = 1.0f / UNITS_PER_BLOCK;

    /**
     * The drawable area, in the same units. The model recesses a 14-of-16
     * display behind a one-unit bezel, so the panel is 14/16 of a block --
     * {@code 0.875 * 80 = 70} units. Change the model's frame and this follows.
     */
    private static final int EXTENT = 70;
    private static final int HALF = EXTENT / 2;

    /**
     * How far from block centre the panel sits: the face is at 0.5 and the
     * recess floor is half a unit behind it, so 0.46875, plus a hair to clear
     * it. The recess is deliberately shallow -- two units read as a hole
     * rather than a screen bezel.
     */
    private static final float FACE_OFFSET = 0.470f;

    private static final int MARGIN = 5;        // horizontal breathing room
    private static final int LINE = 9;          // font line height
    private static final int ICON = 7;
    private static final int GAP = 2;           // icon to count
    private static final int ROWS = 3;

    /** Charcoal, near-opaque. Drawn behind everything so the block's own
     *  texture cannot fight the text -- whatever texture it ends up having. */
    private static final int PANEL = 0xE01C1C20;
    private static final int TITLE_COLOUR = 0xFFF2F2F2;

    private long cachedForQuest = Long.MIN_VALUE;
    private long cachedAtTick = Long.MIN_VALUE;
    private Optional<QuestBridge.NodeView> cached = Optional.empty();

    public ResearchTerminalRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public boolean shouldRenderOffScreen(ResearchTerminalBlockEntity be) {
        return false;
    }

    @Override
    public int getViewDistance() {
        return 24;
    }

    @Override
    public void render(ResearchTerminalBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // Distance is read off the pose, not from the block's coordinates.
        //
        // A block entity aboard an assembled vessel lives in the ship's own
        // sub-level, and getBlockPos() reports its position in THAT space --
        // while the player's position is in the world. Comparing the two put
        // them thousands of blocks apart the moment a raft was assembled, this
        // check failed every frame, and the screen simply never drew. It worked
        // on an unassembled raft because there the two spaces are the same one,
        // which is why it survived every test until a ship existed.
        //
        // The renderer is handed a pose already translated to this block
        // relative to the camera, so the translation IS the offset, in whatever
        // level the block happens to be in.
        Matrix4f at = pose.last().pose();
        double dx = at.m30() + 0.5, dy = at.m31() + 0.5, dz = at.m32() + 0.5;
        if (dx * dx + dy * dy + dz * dz > VISIBLE_RANGE_SQ) return;

        Direction facing = be.getBlockState().getValue(ResearchTerminalBlock.FACING);
        Optional<QuestBridge.NodeView> view = view(be, mc.level.getGameTime());
        Font font = mc.font;

        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-facing.toYRot()));
        pose.translate(0.0, 0.0, FACE_OFFSET);
        pose.scale(SCALE, -SCALE, SCALE);

        // The display lights itself. `light` is the block's packed light, and
        // threading it through made the screen legible in daylight and nearly
        // unreadable in a hold, at night, or anywhere a terminal sensibly
        // lives -- which is most of the time a player is actually looking at
        // one. A screen that dims with the room is a screen modelled as paint.
        // Full brightness is how a glowing sign works, and it costs nothing:
        // the panel stays the dark charcoal it is authored as, it just stops
        // being multiplied down by the room.
        light = LightTexture.FULL_BRIGHT;

        panel(pose, buffers, light);

        if (view.isEmpty()) {
            // Wrapped, for the same reason the node title below is: the panel
            // is 70 units wide with a 5-unit margin either side, and this
            // string does not fit on one line at any sensible font size. The
            // first version centred it as a single line and it ran off both
            // edges of the block.
            emptyState(font, pose, buffers, light);
            pose.popPose();
            return;
        }

        QuestBridge.NodeView node = view.get();
        int y = -HALF + MARGIN;

        // The title is a node name, not a label -- "The Ancient Remnant Hunting
        // Ground I" is far wider than a block face. Wrap it, and only then give
        // up and clip, so the common case reads properly instead of running off
        // the edge as it did on the first pass.
        List<FormattedCharSequence> title =
                font.split(node.title().copy().withStyle(ChatFormatting.WHITE), EXTENT - MARGIN * 2);
        for (int i = 0; i < title.size() && i < 2; i++) {
            FormattedCharSequence l = title.get(i);
            font.drawInBatch(l, -font.width(l) / 2f, y, TITLE_COLOUR, true,
                             pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, light);
            y += LINE;
        }

        y += 2;
        List<QuestBridge.TaskView> tasks = node.tasks();

        // Two columns when the counts fit, one when they do not. Recessing the
        // display cost 10 units of width, and "128/256" is a lot wider than
        // "0/9" -- choosing per node beats picking a layout that clips the
        // occasional expensive one.
        int widest = 0;
        for (QuestBridge.TaskView t : tasks) {
            widest = Math.max(widest, font.width(t.progress() + "/" + t.max()));
        }
        int usable = EXTENT - MARGIN * 2;
        int columns = (ICON + GAP + widest) * 2 <= usable ? 2 : 1;
        int rows = columns == 2 ? ROWS : ROWS + 1;
        int cell = usable / columns;
        int shown = Math.min(tasks.size(), columns * rows);
        for (int i = 0; i < shown; i++) {
            QuestBridge.TaskView t = tasks.get(i);
            int col = i % columns, row = i / columns;
            int x = -HALF + MARGIN + col * cell;
            int ty = y + row * (LINE + 2);
            icon(mc, pose, buffers, light, overlay, t.icon(), x, ty);
            Component count = Component.literal(t.progress() + "/" + t.max())
                    .withStyle(t.complete() ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
            draw(font, pose, buffers, light, count, x + ICON + GAP, ty + 1);
        }

        if (tasks.size() > shown) {
            centre(font, pose, buffers, light,
                   Component.literal("+" + (tasks.size() - shown) + " more")
                            .withStyle(ChatFormatting.DARK_GRAY),
                   y + rows * (LINE + 2));
        }
        pose.popPose();
    }

    /**
     * A flat charcoal quad just proud of the block face.
     *
     * <p>The first version drew text straight onto the block, and the terminal's
     * face is a lectern front over dark prismarine -- busy enough that yellow
     * counts were hard to read. Backing the text ourselves means the display
     * stays legible no matter what texture or model the block later gets.
     */
    private static void panel(PoseStack pose, MultiBufferSource buffers, int light) {
        Matrix4f m = pose.last().pose();
        VertexConsumer vc = buffers.getBuffer(RenderType.textBackground());
        float a = ((PANEL >> 24) & 0xFF) / 255f, r = ((PANEL >> 16) & 0xFF) / 255f;
        float g = ((PANEL >> 8) & 0xFF) / 255f, b = (PANEL & 0xFF) / 255f;
        float e = HALF;
        vc.addVertex(m, -e,  e, 0f).setColor(r, g, b, a).setLight(light);
        vc.addVertex(m,  e,  e, 0f).setColor(r, g, b, a).setLight(light);
        vc.addVertex(m,  e, -e, 0f).setColor(r, g, b, a).setLight(light);
        vc.addVertex(m, -e, -e, 0f).setColor(r, g, b, a).setLight(light);
    }

    /** Refresh only on an interval, or when the terminal is retargeted. */
    private Optional<QuestBridge.NodeView> view(ResearchTerminalBlockEntity be, long now) {
        long quest = be.targetQuestId();
        if (quest != cachedForQuest || now - cachedAtTick >= REFRESH_TICKS) {
            cachedForQuest = quest;
            cachedAtTick = now;
            cached = (quest == 0L || be.teamId() == null)
                    ? Optional.empty()
                    : QuestBridge.nodeView(quest, be.teamId(), false);
        }
        return cached;
    }

    private static void draw(Font font, PoseStack pose, MultiBufferSource buffers,
                             int light, Component text, int x, int y) {
        font.drawInBatch(text, x, y, 0xFFFFFF, true, pose.last().pose(), buffers,
                         Font.DisplayMode.NORMAL, 0, light);
    }

    /**
     * The idle panel: one message, wrapped to the panel and centred as a block.
     *
     * <p>Centred vertically on the whole wrapped run rather than on its first
     * line, so it reads the same whether it comes out as one line or two --
     * which depends on the language, and so is not something to hard-code.
     */
    private static void emptyState(Font font, PoseStack pose, MultiBufferSource buffers, int light) {
        List<FormattedCharSequence> lines = font.split(
                Component.translatable("block.threehourtour.research_terminal.no_target_short")
                         .withStyle(ChatFormatting.GRAY),
                EXTENT - MARGIN * 2);
        int y = -(lines.size() * LINE) / 2;
        for (FormattedCharSequence line : lines) {
            font.drawInBatch(line, -font.width(line) / 2f, y, 0xFFFFFF, true,
                             pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, light);
            y += LINE;
        }
    }

    private static void centre(Font font, PoseStack pose, MultiBufferSource buffers,
                               int light, Component text, int y) {
        draw(font, pose, buffers, light, text, -font.width(text) / 2, y);
    }

    /**
     * A small flat item, drawn in GUI context so it reads like the quest screen.
     *
     * <p><b>The anchor is the icon's centre, not its corner.</b>
     * {@code renderStatic} in {@code GUI} context draws into a unit square
     * centred on the origin, so scaling by N spans -N/2..+N/2. The first
     * version translated to the cell's left edge and every icon therefore hung
     * half its width off that edge -- invisible until the display was recessed
     * and the leftmost column ran past the bezel.
     */
    private static void icon(Minecraft mc, PoseStack pose, MultiBufferSource buffers,
                             int light, int overlay, ItemStack stack, int x, int y) {
        if (stack == null || stack.isEmpty()) return;
        pose.pushPose();
        pose.translate(x + ICON / 2.0, y + ICON / 2.0, 0.0);
        pose.scale(ICON, -ICON, ICON);
        mc.getItemRenderer().renderStatic(stack, ItemDisplayContext.GUI, light, overlay,
                                          pose, buffers, mc.level, 0);
        pose.popPose();
    }
}

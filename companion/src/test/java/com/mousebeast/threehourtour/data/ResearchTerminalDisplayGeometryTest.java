package com.mousebeast.threehourtour.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The renderer's panel has to sit in the hole the model cuts for it.
 *
 * <p>The block model recesses the display behind a bezel, and
 * {@code ResearchTerminalRenderer} hard-codes two numbers derived from that
 * geometry: how far back the panel sits, and how wide it may be. Change the
 * model's frame or depth and those go stale -- the panel floats in front of
 * the bezel, or overruns it -- and nothing says so, because the renderer only
 * runs on a client and only when someone is stood in front of the block.
 *
 * <p>So this derives both numbers from the model and checks the source agrees.
 * It reads the renderer as <b>text</b> rather than loading the class on
 * purpose: that class is client-only and touches {@code Font} and
 * {@code ItemRenderer}, neither of which exists in a headless test JVM.
 */
class ResearchTerminalDisplayGeometryTest {

    private static final int UNITS_PER_BLOCK = 80;

    private static Path asset(String rel) {
        return Path.of("src/main/resources/assets/threehourtour", rel);
    }

    private static int constant(String source, String name) {
        Matcher m = Pattern.compile(
                "static final \\w+ " + name + " = ([0-9.]+)f?;").matcher(source);
        assertTrue(m.find(), "ResearchTerminalRenderer has no constant " + name);
        return (int) Math.round(Double.parseDouble(m.group(1)) * 1000);
    }

    /**
     * The size of the opening and the depth of the recess, read back out of
     * the model's elements.
     *
     * <p>Rasterised rather than inferred from bar widths. The first version
     * took the narrowest bezel bar, which meant widening a single bar left the
     * minimum unchanged and the guard silently passed -- caught by its own
     * negative test. Marking which of the 16x16 columns a bar covers and
     * measuring what is left over cannot be fooled that way.
     */
    private static double[] geometry() throws IOException {
        JsonObject model = JsonParser.parseString(new String(Files.readAllBytes(
                asset("models/block/research_terminal.json")))).getAsJsonObject();
        JsonArray elements = model.getAsJsonArray("elements");
        assertNotNull(elements, "the terminal model declares no elements -- if it went"
                + " back to a parent-only model, the renderer's recess maths is wrong");

        double depth = Double.MAX_VALUE;   // fractional: the recess is half a unit
        boolean[][] covered = new boolean[16][16];
        for (var e : elements) {
            JsonArray from = e.getAsJsonObject().getAsJsonArray("from");
            JsonArray to = e.getAsJsonObject().getAsJsonArray("to");
            if (to.get(2).getAsInt() == 16) {          // the body
                depth = Math.min(depth, from.get(2).getAsDouble());
                continue;
            }
            for (int x = (int) from.get(0).getAsDouble(); x < to.get(0).getAsDouble(); x++) {
                for (int y = (int) from.get(1).getAsDouble(); y < to.get(1).getAsDouble(); y++) {
                    covered[x][y] = true;
                }
            }
        }
        assertNotEquals(Double.MAX_VALUE, depth, "no body element found in the model");

        int x0 = 16, x1 = 0, y0 = 16, y1 = 0;
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                if (!covered[x][y]) {
                    x0 = Math.min(x0, x); x1 = Math.max(x1, x + 1);
                    y0 = Math.min(y0, y); y1 = Math.max(y1, y + 1);
                }
            }
        }
        assertTrue(x1 > x0 && y1 > y0, "the bezel covers the whole face; nothing to draw on");
        return new double[]{Math.min(x1 - x0, y1 - y0), depth};
    }

    @Test void thePanelSitsOnTheRecessFloor() throws IOException {
        double depth = geometry()[1];
        String src = Files.readString(Path.of(
                "src/main/java/com/mousebeast/threehourtour/research/ResearchTerminalRenderer.java"));
        // Face at 0.5 from centre, floor `depth`/16 behind it, plus a hair to
        // avoid z-fighting. Compared in thousandths so the hair is allowed.
        int expected = (int) Math.round((0.5 - depth / 16.0) * 1000);
        int actual = constant(src, "FACE_OFFSET");
        assertTrue(actual >= expected && actual <= expected + 3,
                "FACE_OFFSET is " + actual / 1000.0 + " but the model's recess floor is at "
                + expected / 1000.0 + "; the panel will " + (actual < expected
                        ? "sink into the block" : "float in front of the bezel"));
    }

    @Test void thePanelFitsBetweenTheBezels() throws IOException {
        int opening = (int) geometry()[0];
        String src = Files.readString(Path.of(
                "src/main/java/com/mousebeast/threehourtour/research/ResearchTerminalRenderer.java"));
        int expected = opening * UNITS_PER_BLOCK / 16;
        assertEquals(expected, constant(src, "EXTENT") / 1000,
                "EXTENT must match the model's " + opening + "/16 opening");
    }
}

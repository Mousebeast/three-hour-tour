package com.mousebeast.threehourtour.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A plot-space position with no vessel is the record that strands a player
 * 20.4M blocks out: CoreRecord says a null vessel id means grounded, and
 * grounded means CoreRespawn.resolveLive teleports to the stored position
 * verbatim. There is exactly one input pair that must refuse, and this pins it.
 */
class PlotBindingRuleTest {

    @Test void aPlotPositionWithNoVesselIsTheOneCombinationThatRefuses() {
        assertTrue(PlotBindingRule.mustRefuse(true, false));
        assertFalse(PlotBindingRule.isConsistent(true, false));
    }

    @Test void aPlotPositionWithAResolvedVesselIsFine() {
        assertFalse(PlotBindingRule.mustRefuse(true, true));
        assertTrue(PlotBindingRule.isConsistent(true, true));
    }

    @Test void aWorldPositionWithNoVesselIsAGroundedCoreAndIsFine() {
        assertFalse(PlotBindingRule.mustRefuse(false, false));
        assertTrue(PlotBindingRule.isConsistent(false, false));
    }

    @Test void aWorldPositionThatAlsoResolvesAVesselIsFine() {
        assertFalse(PlotBindingRule.mustRefuse(false, true));
        assertTrue(PlotBindingRule.isConsistent(false, true));
    }

    @Test void mustRefuseIsExactlyTheNegationOfIsConsistent() {
        for (boolean fromPlot : new boolean[]{false, true}) {
            for (boolean vessel : new boolean[]{false, true}) {
                assertNotEquals(PlotBindingRule.isConsistent(fromPlot, vessel),
                        PlotBindingRule.mustRefuse(fromPlot, vessel),
                        "fromPlot=" + fromPlot + " vessel=" + vessel);
            }
        }
    }

    @Test void exactlyOneOfTheFourInputsRefuses() {
        int refusals = 0;
        for (boolean fromPlot : new boolean[]{false, true}) {
            for (boolean vessel : new boolean[]{false, true}) {
                if (PlotBindingRule.mustRefuse(fromPlot, vessel)) refusals++;
            }
        }
        assertEquals(1, refusals, "the rule must refuse the plot-without-vessel pair and nothing else");
    }
}

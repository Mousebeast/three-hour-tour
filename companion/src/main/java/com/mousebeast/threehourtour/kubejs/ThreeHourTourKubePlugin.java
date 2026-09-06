package com.mousebeast.threehourtour.kubejs;

import com.mousebeast.threehourtour.core.CoreAccess;
import com.mousebeast.threehourtour.vessel.VesselLookup;
import com.mousebeast.threehourtour.vessel.VesselState;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingRegistry;

/**
 * Exposes the on-ship predicate to KubeJS.
 *
 * Scripts must NOT reproduce the Sable lookup chain themselves: four probe
 * iterations established that KubeJS's Rhino layer does not give clean access
 * to Sable's static API or to java.lang.Class reflection. One binding, one
 * implementation, checked at compile time.
 */
public class ThreeHourTourKubePlugin implements KubeJSPlugin {
    @Override
    public void registerBindings(BindingRegistry bindings) {
        bindings.add("Vessel", VesselLookup.class);
        bindings.add("ShipCore", CoreAccess.class);
        bindings.add("VesselState", VesselState.class);
    }
}

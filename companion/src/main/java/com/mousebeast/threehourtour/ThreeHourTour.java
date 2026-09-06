package com.mousebeast.threehourtour;

import com.mousebeast.threehourtour.core.CoreRegistry;
import com.mousebeast.threehourtour.material.TreatedWoodRegistry;
import com.mousebeast.threehourtour.research.ResearchRegistry;
import com.mousebeast.threehourtour.scoop.ScoopRegistry;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(ThreeHourTour.MOD_ID)
public class ThreeHourTour {
    public static final String MOD_ID = "threehourtour";
    public static final Logger LOG = LoggerFactory.getLogger("Three Hour Tour");

    public ThreeHourTour(IEventBus modBus, ModContainer container) {
        CoreRegistry.BLOCKS.register(modBus);
        CoreRegistry.BLOCK_ENTITIES.register(modBus);
        ScoopRegistry.BLOCKS.register(modBus);
        ScoopRegistry.ITEMS.register(modBus);
        ScoopRegistry.BLOCK_ENTITIES.register(modBus);
        ResearchRegistry.BLOCKS.register(modBus);
        ResearchRegistry.ITEMS.register(modBus);
        ResearchRegistry.BLOCK_ENTITIES.register(modBus);
        TreatedWoodRegistry.BLOCKS.register(modBus);
        TreatedWoodRegistry.ITEMS.register(modBus);
        CreativeTabRegistry.TABS.register(modBus);
        modBus.addListener(ScoopRegistry::registerCapabilities);
        modBus.addListener(ResearchRegistry::registerCapabilities);
        LOG.info("Three Hour Tour loaded");
    }
}

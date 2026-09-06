package com.mousebeast.threehourtour;

import com.mousebeast.threehourtour.material.TreatedWood;
import com.mousebeast.threehourtour.material.TreatedWoodRegistry;
import com.mousebeast.threehourtour.research.PackType;
import com.mousebeast.threehourtour.research.ResearchRegistry;
import com.mousebeast.threehourtour.scoop.MeshTier;
import com.mousebeast.threehourtour.scoop.ScoopRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The mod's creative-mode tab(s).
 *
 * Deliberately its own class rather than folded into ScoopRegistry: a
 * creative tab is not scoop-specific, and every item this mod adds later
 * needs somewhere to register a display slot. Today MAIN holds only the sea
 * scoop and its three mesh tiers.
 *
 * Without a tab, items belonging to none are excluded from JEI's ingredient
 * list - the crafting recipes exist but nobody can discover them.
 */
public final class CreativeTabRegistry {

    private CreativeTabRegistry() {}

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ThreeHourTour.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.threehourtour.main"))
                    .icon(() -> ScoopRegistry.SEA_SCOOP_ITEM.get().getDefaultInstance())
                    .displayItems((params, output) -> {
                        output.accept(ScoopRegistry.SEA_SCOOP_ITEM.get());
                        output.accept(ResearchRegistry.RESEARCH_TERMINAL_ITEM.get());
                        // Tier order, not declaration order of some other list - so a
                        // fourth tier automatically slots into the progression here.
                        for (MeshTier tier : MeshTier.values()) {
                            output.accept(ScoopRegistry.meshItem(tier).get());
                        }
                        // Enum order, so a variant added to TreatedWood appears
                        // here without anyone remembering to come back.
                        for (TreatedWood variant : TreatedWood.values()) {
                            output.accept(TreatedWoodRegistry.item(variant).get());
                        }
                        // Enum order, so a currency added to PackType appears
                        // in the tab without touching this file. Bundle, core,
                        // pack: stages 1, 2 and 3, so the chain reads left to
                        // right.
                        for (PackType type : PackType.values()) {
                            output.accept(ResearchRegistry.bundleItem(type).get());
                            output.accept(ResearchRegistry.coreItem(type).get());
                            output.accept(ResearchRegistry.packItem(type).get());
                        }
                        output.accept(ResearchRegistry.ASH.get());
                        output.accept(ResearchRegistry.FIRE_SIGIL.get());
                        output.accept(ResearchRegistry.SOUL_SIGIL.get());
                        output.accept(ResearchRegistry.VOID_SIGIL.get());
                        ResearchRegistry.TROPHIES.values()
                                .forEach(t -> output.accept(t.get()));
                        ResearchRegistry.SEALS.values()
                                .forEach(s -> output.accept(s.get()));
                    })
                    .build());
}

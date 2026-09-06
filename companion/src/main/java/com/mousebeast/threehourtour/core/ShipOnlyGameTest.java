package com.mousebeast.threehourtour.core;

import com.mousebeast.threehourtour.ThreeHourTour;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Optional;

@GameTestHolder(ThreeHourTour.MOD_ID)
@PrefixGameTestTemplate(false)
public class ShipOnlyGameTest {

    /**
     * Named blocks, not just "some blocks". A tag that resolved only its
     * botanypots reference would pass the emptiness check while leaving bonsai
     * pots placeable anywhere.
     */
    @GameTest(template = "empty")
    public static void bonsaiPotsAreInTheShipOnlyTag(GameTestHelper helper) {
        for (String id : new String[] {"bonsaitrees4:bonsaipot", "bonsaitrees4:bonsaipot_small"}) {
            // getOptional, NOT get: BuiltInRegistries.BLOCK.get returns AIR for a
            // missing id rather than null, and AIR is not in the tag - so a null
            // guard would turn "mod absent" into a failing test.
            Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(id));
            if (block.isPresent() && !block.get().defaultBlockState().is(ShipOnlyPlacement.SHIP_ONLY)) {
                helper.fail(id + " is present but not in #threehourtour:ship_only");
            }
        }
        helper.succeed();
    }
}

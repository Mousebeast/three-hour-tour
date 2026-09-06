package com.mousebeast.threehourtour.core;

import com.mousebeast.threehourtour.ThreeHourTour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ThreeHourTour.MOD_ID)
@PrefixGameTestTemplate(false)
public class ShipCoreGameTest {

    @GameTest(template = "empty")
    public static void coreBlockIsRegistered(GameTestHelper helper) {
        if (BuiltInRegistries.BLOCK.getOptional(CoreRegistry.SHIP_CORE_ID).isEmpty()) {
            helper.fail("ship_core is not in the block registry");
        }
        helper.succeed();
    }

    /**
     * The core must have NO item form. This is the enforcement for "the core
     * never becomes an item" - not a check we run, but a thing that does not
     * exist. If someone later adds a BlockItem for convenience, this fails.
     */
    @GameTest(template = "empty")
    public static void coreBlockHasNoItemForm(GameTestHelper helper) {
        if (BuiltInRegistries.ITEM.getOptional(CoreRegistry.SHIP_CORE_ID).isPresent()) {
            helper.fail("ship_core has a BlockItem - the core must not be obtainable as an item");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void coreBlockCannotBeBrokenOrBlown(GameTestHelper helper) {
        BlockPos rel = new BlockPos(1, 1, 1);
        helper.setBlock(rel, CoreRegistry.SHIP_CORE.get());
        BlockState state = helper.getBlockState(rel);

        if (state.getDestroySpeed(helper.getLevel(), helper.absolutePos(rel)) >= 0.0F) {
            helper.fail("ship_core is breakable");
        }
        if (state.getBlock().getExplosionResistance() < 3_600_000.0F) {
            helper.fail("ship_core is not blast proof");
        }
        if (state.getPistonPushReaction() != PushReaction.BLOCK) {
            helper.fail("ship_core can be moved by pistons");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bindingSurvivesSaveAndLoad(GameTestHelper helper) {
        BlockPos rel = new BlockPos(1, 1, 1);
        helper.setBlock(rel, CoreRegistry.SHIP_CORE.get());

        ShipCoreBlockEntity be = ShipCoreBlockEntity
                .at(helper.getLevel(), helper.absolutePos(rel))
                .orElse(null);
        if (be == null) {
            helper.fail("ship_core placed no block entity");
            return;
        }
        if (be.getBinding() != null) {
            helper.fail("a freshly placed core must be unclaimed, was " + be.getBinding());
        }

        java.util.UUID owner  = java.util.UUID.randomUUID();
        java.util.UUID vessel = java.util.UUID.randomUUID();
        be.setBinding(new CoreBinding(owner, vessel));

        var registries = helper.getLevel().registryAccess();
        net.minecraft.nbt.CompoundTag saved = be.saveWithFullMetadata(registries);

        net.minecraft.world.level.block.entity.BlockEntity reloaded =
                net.minecraft.world.level.block.entity.BlockEntity.loadStatic(
                        helper.absolutePos(rel), helper.getBlockState(rel), saved, registries);

        if (!(reloaded instanceof ShipCoreBlockEntity core)) {
            helper.fail("reloaded block entity was not a ShipCoreBlockEntity");
            return;
        }
        if (!new CoreBinding(owner, vessel).equals(core.getBinding())) {
            helper.fail("binding did not survive save/load, got " + core.getBinding());
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void groundedBindingSurvivesSaveAndLoad(GameTestHelper helper) {
        BlockPos rel = new BlockPos(2, 1, 1);
        helper.setBlock(rel, CoreRegistry.SHIP_CORE.get());
        ShipCoreBlockEntity be = ShipCoreBlockEntity
                .at(helper.getLevel(), helper.absolutePos(rel)).orElseThrow();

        java.util.UUID owner = java.util.UUID.randomUUID();
        be.setBinding(new CoreBinding(owner, null));

        var registries = helper.getLevel().registryAccess();
        var reloaded = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(
                helper.absolutePos(rel), helper.getBlockState(rel),
                be.saveWithFullMetadata(registries), registries);

        CoreBinding out = ((ShipCoreBlockEntity) reloaded).getBinding();
        if (out == null || out.isBound() || !owner.equals(out.owner())) {
            helper.fail("grounded binding did not round-trip, got " + out);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void consumeRemovesEveryTargetBlockInRange(GameTestHelper helper) {
        BlockPos centre = new BlockPos(1, 1, 1);
        helper.setBlock(new BlockPos(1, 1, 2), net.minecraft.world.level.block.Blocks.STONE);
        helper.setBlock(new BlockPos(2, 1, 2), net.minecraft.world.level.block.Blocks.STONE);

        int removed = AssemblerConsumer.consume(
                helper.getLevel(), helper.absolutePos(centre),
                net.minecraft.world.level.block.Blocks.STONE);

        if (removed < 2) {
            helper.fail("expected at least 2 blocks consumed, got " + removed);
        }
        helper.assertBlockPresent(net.minecraft.world.level.block.Blocks.AIR, new BlockPos(1, 1, 2));
        helper.assertBlockPresent(net.minecraft.world.level.block.Blocks.AIR, new BlockPos(2, 1, 2));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void consumeFindingNothingReturnsZero(GameTestHelper helper) {
        int removed = AssemblerConsumer.consume(
                helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)),
                net.minecraft.world.level.block.Blocks.DIAMOND_BLOCK);
        if (removed != 0) {
            helper.fail("expected 0 consumed, got " + removed);
        }
        helper.succeed();
    }

    /** The dev GameTest server has no `simulated` mod. That must be a no-op, not a crash. */
    @GameTest(template = "empty")
    public static void consumingAssemblersWithoutSimulatedIsSafe(GameTestHelper helper) {
        int result = AssemblerConsumer.consumeAssemblers(
                helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)));
        if (result > 0) {
            helper.fail("consumed " + result + " assemblers on a server with no simulated mod");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void indexRemembersAndForgetsACore(GameTestHelper helper) {
        ShipCoreIndex index = ShipCoreIndex.get(helper.getLevel());
        java.util.UUID owner  = java.util.UUID.randomUUID();
        java.util.UUID vessel = java.util.UUID.randomUUID();
        BlockPos abs = helper.absolutePos(new BlockPos(1, 1, 1));

        if (index.hasCore(owner)) {
            helper.fail("a fresh owner already has a core");
        }
        index.put(new CoreRecord(owner, helper.getLevel().dimension().location(), abs, vessel));

        if (!index.hasCore(owner)) helper.fail("index did not record the core");
        CoreRecord found = index.forPlayer(owner).orElse(null);
        if (found == null || !abs.equals(found.pos()) || !vessel.equals(found.vesselId())) {
            helper.fail("index returned the wrong record: " + found);
        }

        index.forget(owner);
        if (index.hasCore(owner)) helper.fail("index did not forget the core");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void indexSurvivesSaveAndLoad(GameTestHelper helper) {
        ShipCoreIndex index = ShipCoreIndex.get(helper.getLevel());
        java.util.UUID owner = java.util.UUID.randomUUID();
        BlockPos abs = helper.absolutePos(new BlockPos(1, 1, 1));
        index.put(new CoreRecord(owner, helper.getLevel().dimension().location(), abs, null));

        var registries = helper.getLevel().registryAccess();
        net.minecraft.nbt.CompoundTag tag = index.save(new net.minecraft.nbt.CompoundTag(), registries);
        ShipCoreIndex reloaded = ShipCoreIndex.load(tag, registries);

        CoreRecord found = reloaded.forPlayer(owner).orElse(null);
        if (found == null || found.isBound() || !abs.equals(found.pos())) {
            helper.fail("grounded record did not survive save/load: " + found);
        }
        helper.succeed();
    }

}

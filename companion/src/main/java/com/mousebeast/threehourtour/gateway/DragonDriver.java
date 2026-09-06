package com.mousebeast.threehourtour.gateway;

import com.mousebeast.threehourtour.ThreeHourTour;
import dev.shadowsoffire.gateways.event.GateEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonChargePlayerPhase;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Makes an Ender Dragon summoned by one of our Named Gates actually fight.
 * See DragonDriverRule for why this cannot simply set a flying phase.
 */
@EventBusSubscriber(modid = ThreeHourTour.MOD_ID)
public final class DragonDriver {

    /** Matches the leash Gateways enforces; a charge is re-aimed past this. */
    private static final double LEASH = 64.0;
    private static final double LEASH_SQR = LEASH * LEASH;

    /** The gate position, stored on the dragon so it survives a reload. */
    private static final String GATE_X = "3ht_gate_x";
    private static final String GATE_Y = "3ht_gate_y";
    private static final String GATE_Z = "3ht_gate_z";

    private DragonDriver() {
    }

    @SubscribeEvent
    public static void onWaveEntitySpawned(GateEvent.WaveEntitySpawned event) {
        if (!(event.getWaveEntity() instanceof EnderDragon dragon)) {
            return;
        }
        // GateEvent has no getGateway() (checked against the jar's bytecode via
        // javap; the brief's claim of an inherited getGateway() does not hold) --
        // it covariant-overrides EntityEvent.getEntity() to return GatewayEntity,
        // which is what WaveEntitySpawned resolves to here.
        BlockPos gate = event.getEntity().blockPosition();
        CompoundTag data = dragon.getPersistentData();
        data.putBoolean(DragonDriverRule.MARKER, true);
        data.putInt(GATE_X, gate.getX());
        data.putInt(GATE_Y, gate.getY());
        data.putInt(GATE_Z, gate.getZ());
        // Read for the podium position, not for the flight ring -- harmless,
        // and it keeps LANDING_APPROACH from aiming at world origin if some
        // other mod ever forces that phase.
        dragon.setFightOrigin(gate);
        ThreeHourTour.LOG.info("Gate dragon {} bound to {}", dragon.getUUID(), gate);
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        // The type test is cheap (an instanceof) and rejects almost every
        // ticking entity on the server; level().isClientSide() is a virtual
        // call, so it goes second and only runs for the rare entity that is
        // actually an EnderDragon.
        if (!(entity instanceof EnderDragon dragon)
                || entity.level().isClientSide()
                || !dragon.getPersistentData().getBoolean(DragonDriverRule.MARKER)) {
            return;
        }
        // Every half second is enough: a charge lasts far longer than that,
        // and re-issuing one every tick would reset its target constantly.
        if (dragon.tickCount % 10 != 0) {
            return;
        }

        CompoundTag data = dragon.getPersistentData();
        Vec3 gate = new Vec3(data.getInt(GATE_X) + 0.5,
                             data.getInt(GATE_Y) + 0.5,
                             data.getInt(GATE_Z) + 0.5);
        boolean charging = dragon.getPhaseManager().getCurrentPhase().getPhase()
                == EnderDragonPhase.CHARGING_PLAYER;
        if (!DragonDriverRule.shouldRecharge(charging, gate.distanceToSqr(dragon.position()),
                                             LEASH_SQR)) {
            return;
        }

        Player target = dragon.level().getNearestPlayer(gate.x, gate.y, gate.z,
                                                        LEASH, false);
        // No player at the gate: hold the dragon at the gate rather than
        // letting HOLDING_PATTERN walk it to world origin while nobody looks.
        Vec3 aim = target == null ? gate : target.position();
        dragon.getPhaseManager().setPhase(EnderDragonPhase.CHARGING_PLAYER);
        DragonChargePlayerPhase phase =
                dragon.getPhaseManager().getPhase(EnderDragonPhase.CHARGING_PLAYER);
        phase.setTarget(aim);
    }
}

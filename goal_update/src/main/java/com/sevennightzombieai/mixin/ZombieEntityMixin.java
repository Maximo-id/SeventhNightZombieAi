package com.sevennightzombieai.mixin;

import com.sevennightzombieai.goal.ZombieBreakBlockGoal;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.ZombieEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Inyecta ZombieBreakBlockGoal en initGoals() de TODA entidad que extienda
 * ZombieEntity — esto incluye zombies vanilla, husks, drowned, y cualquier
 * subclase custom de otros mods (como los zombies explosivos de SeventhNight),
 * sin que este mod tenga que depender de esos mods.
 *
 * También sube el atributo GENERIC_FOLLOW_RANGE para que detecten al jugador
 * a mayor distancia que el comportamiento vanilla.
 */
@Mixin(ZombieEntity.class)
public abstract class ZombieEntityMixin {

    // Distancia de detección deseada, en bloques. Vanilla ronda 35 en zombies,
    // pero en la práctica el jugador siente que "no lo ven" hasta estar muy cerca
    // por la forma en que interactúan follow range + line of sight + goals.
    // Subimos el piso para que el rango efectivo sea consistente y notorio.
    private static final double DETECTION_RANGE_BLOCKS = 48.0;

    @Inject(method = "<init>(Lnet/minecraft/entity/EntityType;Lnet/minecraft/world/World;)V", at = @At("RETURN"))
    private void seventhNightZombieAi$boostDetectionRange(CallbackInfo ci) {
        ZombieEntity self = (ZombieEntity) (Object) this;
        EntityAttributeInstance followRange = self.getAttributeInstance(EntityAttributes.GENERIC_FOLLOW_RANGE);
        if (followRange != null && followRange.getBaseValue() < DETECTION_RANGE_BLOCKS) {
            followRange.setBaseValue(DETECTION_RANGE_BLOCKS);
        }
    }

    @Inject(method = "initGoals", at = @At("TAIL"))
    private void seventhNightZombieAi$addBreakBlockGoal(CallbackInfo ci) {
        ZombieEntity self = (ZombieEntity) (Object) this;
        // Prioridad 2: más urgente que wander, menos que atacar directamente al jugador
        self.goalSelector.add(2, new ZombieBreakBlockGoal(self));
    }
}

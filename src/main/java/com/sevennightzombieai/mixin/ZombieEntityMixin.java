package com.sevennightzombieai.mixin;

import com.mojang.text2speech.Narrator;
import com.sevennightzombieai.SeventhNightZombieAIMod;
import com.sevennightzombieai.goal.ZombieBreakBlockGoal;
import com.sevennightzombieai.goal.ZombieRepathGoal;
import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.ZombieEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Inyecta ZombieBreakBlockGoal y ZombieRepathGoal en initGoals() de TODA
 * entidad que extienda ZombieEntity — esto incluye zombies vanilla, husks,
 * drowned, y cualquier subclase custom de otros mods (como los zombies
 * explosivos de SeventhNight), sin que este mod tenga que depender de esos mods.
 *
 * También sube el atributo GENERIC_FOLLOW_RANGE (distancia de detección) y
 * el stepHeight (qué tan alto pueden subir sin saltar) respecto a vanilla.
 */
@Mixin(ZombieEntity.class)
public abstract class ZombieEntityMixin {

    // Distancia de detección deseada, en bloques. Vanilla ronda 35 en zombies,
    // pero en la práctica el jugador siente que "no lo ven" hasta estar muy cerca
    // por la forma en que interactúan follow range + line of sight + goals.
    // Subimos el piso para que el rango efectivo sea consistente y notorio.
    private static final double DETECTION_RANGE_BLOCKS = 48.0;

    // Vanilla es 1.0 (sube bloques sueltos pero no escombros/escalones dobles).
    // 1.5 les permite subir obstáculos irregulares sin sentirse sobrenatural.
    private static final float STEP_HEIGHT_BLOCKS = 1.5f;

    @Inject(method = "<init>(Lnet/minecraft/entity/EntityType;Lnet/minecraft/world/World;)V", at = @At("RETURN"))
    private void seventhNightZombieAi$boostMovementStats(CallbackInfo ci) {
        ZombieEntity self = (ZombieEntity) (Object) this;

        EntityAttributeInstance followRange = self.getAttributeInstance(EntityAttributes.GENERIC_FOLLOW_RANGE);
        if (followRange != null) {
            double before = followRange.getBaseValue();
            if (before < DETECTION_RANGE_BLOCKS) {
                followRange.setBaseValue(DETECTION_RANGE_BLOCKS);
            }
            SeventhNightZombieAIMod.LOGGER.info(
                    "[AI-DEBUG] Zombie construido. followRange antes={} despues={}",
                    before, followRange.getBaseValue());
        } else {
            SeventhNightZombieAIMod.LOGGER.warn("[AI-DEBUG] Zombie construido pero followRange attribute es NULL");
        }

        self.setStepHeight(STEP_HEIGHT_BLOCKS);
    }

    @Inject(method = "initGoals", at = @At("TAIL"))
    private void seventhNightZombieAi$addCustomGoals(CallbackInfo ci) {
        ZombieEntity self = (ZombieEntity) (Object) this;
        GoalSelector goalSelector = ((MobEntityAccessor) self).seventhNightZombieAi$getGoalSelector();

        // Prioridad 1: romper bloques. Va POR ENCIMA de ZombieAttackGoal (prioridad 2
        // en vanilla) para que cuando el zombie esté trabado contra un bloque rompible,
        // rompa el bloque en lugar de quedarse intentando atacar al aire.
        goalSelector.add(1, new ZombieBreakBlockGoal(self));

        // Prioridad 3: re-pathfinding cuando está trabado. Menos urgente que romper
        // bloques, pero sigue activo para dar variedad al comportamiento.
        goalSelector.add(3, new ZombieRepathGoal(self));

        Narrator SevenNightZombieAIMod = null;
        SevenNightZombieAIMod.LOGGER.info("[AI-DEBUG] Goals custom agregados a initGoals()");
    }
}
package com.sevennightzombieai.mixin;

import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Expone el goalSelector (protected en MobEntity) como un getter público
 * generado por Mixin, evitando accesos directos a campos protegidos desde
 * otros mixins y los falsos positivos de análisis estático en el IDE.
 */
@Mixin(MobEntity.class)
public interface MobEntityAccessor {

    @Accessor("goalSelector")
    GoalSelector seventhNightZombieAi$getGoalSelector();
}

package com.sevennightzombieai.mixin;

import com.sevennightzombieai.SevenNightZombieAIMod;
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

@Mixin(ZombieEntity.class)
public abstract class ZombieEntityMixin {

    private static final double DETECTION_RANGE_BLOCKS = 48.0;
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
            SevenNightZombieAIMod.LOGGER.info(
                    "[AI-DEBUG] Zombie construido. followRange antes={} despues={}",
                    before, followRange.getBaseValue());
        } else {
            SevenNightZombieAIMod.LOGGER.warn("[AI-DEBUG] Zombie construido pero followRange attribute es NULL");
        }

        self.setStepHeight(STEP_HEIGHT_BLOCKS);
    }

    @Inject(method = "initGoals", at = @At("TAIL"))
    private void seventhNightZombieAi$addCustomGoals(CallbackInfo ci) {
        ZombieEntity self = (ZombieEntity) (Object) this;
        GoalSelector goalSelector = ((MobEntityAccessor) self).seventhNightZombieAi$getGoalSelector();

        goalSelector.add(1, new ZombieBreakBlockGoal(self));
        goalSelector.add(3, new ZombieRepathGoal(self));

        SevenNightZombieAIMod.LOGGER.info("[AI-DEBUG] Goals custom agregados a initGoals()");
    }
}
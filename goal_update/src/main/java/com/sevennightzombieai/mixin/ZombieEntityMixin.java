package com.sevennightzombieai.mixin;

import com.sevennightzombieai.goal.ZombieBreakBlockGoal;
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
 */
@Mixin(ZombieEntity.class)
public abstract class ZombieEntityMixin {

    @Inject(method = "initGoals", at = @At("TAIL"))
    private void seventhNightZombieAi$addBreakBlockGoal(CallbackInfo ci) {
        ZombieEntity self = (ZombieEntity) (Object) this;
        // Prioridad 2: más urgente que wander, menos que atacar directamente al jugador
        self.goalSelector.add(2, new ZombieBreakBlockGoal(self));
    }
}

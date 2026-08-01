package com.sevennightzombieai.goal;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.EnumSet;
import java.util.Random;

/**
 * Detecta cuando el zombie está "vibrando" contra un obstáculo que el
 * pathfinding vanilla no logra resolver (típico en esquinas o formas en L)
 * y fuerza un recálculo de ruta apuntando a un punto con offset aleatorio
 * cerca del objetivo, para desatascarlo.
 *
 * Prioridad más alta (número más bajo) que el ataque cuerpo a cuerpo normal,
 * así toma el control de movimiento brevemente y después se lo devuelve.
 */
public class ZombieRepathGoal extends Goal {

    private static final int STUCK_TICKS_THRESHOLD = 30; // ~1.5s
    private static final int JITTER_DURATION_TICKS = 15;  // cuánto dura el intento de desatasque
    private static final double JITTER_RADIUS = 2.5;

    private final ZombieEntity zombie;
    private final World world;
    private final Random random = new Random();

    private int stuckTicks;
    private int jitterTicksRemaining;
    private Vec3d lastPos;

    public ZombieRepathGoal(ZombieEntity zombie) {
        this.zombie = zombie;
        this.world = zombie.getWorld();
        this.setControls(EnumSet.of(Goal.Control.MOVE));
    }

    @Override
    public boolean canStart() {
        LivingEntity target = this.zombie.getTarget();
        if (target == null || !this.zombie.isAlive()) {
            this.stuckTicks = 0;
            this.lastPos = null;
            return false;
        }

        Vec3d currentPos = this.zombie.getPos();
        if (this.lastPos != null && this.lastPos.squaredDistanceTo(currentPos) < 0.0025) {
            this.stuckTicks++;
        } else {
            this.stuckTicks = 0;
        }
        this.lastPos = currentPos;

        return this.stuckTicks >= STUCK_TICKS_THRESHOLD;
    }

    @Override
    public boolean shouldContinue() {
        return this.jitterTicksRemaining > 0 && this.zombie.isAlive() && this.zombie.getTarget() != null;
    }

    @Override
    public void start() {
        this.jitterTicksRemaining = JITTER_DURATION_TICKS;
        this.moveToJitteredPoint();
    }

    @Override
    public void stop() {
        this.stuckTicks = 0;
        this.jitterTicksRemaining = 0;
    }

    @Override
    public void tick() {
        this.jitterTicksRemaining--;
        // Recalcula el punto jitter cada pocos ticks para explorar distintos ángulos
        if (this.jitterTicksRemaining % 5 == 0) {
            this.moveToJitteredPoint();
        }
    }

    private void moveToJitteredPoint() {
        LivingEntity target = this.zombie.getTarget();
        if (target == null) {
            return;
        }

        double angle = this.random.nextDouble() * Math.PI * 2;
        double offsetX = Math.cos(angle) * JITTER_RADIUS;
        double offsetZ = Math.sin(angle) * JITTER_RADIUS;

        Vec3d jitteredTarget = target.getPos().add(offsetX, 0, offsetZ);

        this.zombie.getNavigation().startMovingTo(
                jitteredTarget.x, jitteredTarget.y, jitteredTarget.z, 1.1
        );
    }
}

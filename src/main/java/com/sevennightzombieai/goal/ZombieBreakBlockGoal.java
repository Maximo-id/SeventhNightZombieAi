package com.sevennightzombieai.goal;

import com.sevennightzombieai.tag.ModBlockTags;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;

import java.util.EnumSet;

/**
 * Hace que un zombie rompa bloques "débiles" (whitelist en
 * data/sevennightzombieai/tags/block/breakable_by_zombies.json) cuando está
 * bloqueado en su camino hacia el jugador objetivo.
 *
 * Diseño pensado para hardware de gama baja:
 * - Solo actúa cuando el zombie lleva un rato sin poder avanzar (stuckTicks),
 *   no en cada intento de movimiento.
 * - El rompimiento es progresivo (breakingProgress 0..1), no instantáneo,
 *   evitando picos de cómputo y dando feedback visual/sonoro tipo vanilla.
 * - Solo un bloque objetivo a la vez, recalculado con cooldown.
 */
public class ZombieBreakBlockGoal extends Goal {

    private static final int STUCK_TICKS_THRESHOLD = 40; // ~2s antes de intentar romper
    private static final int RECHECK_COOLDOWN = 20;       // recalcula target cada 1s
    private static final float BREAK_SPEED_PER_TICK = 0.05f; // ~20 ticks para romper (~1s)

    private final ZombieEntity zombie;
    private final World world;

    private int stuckTicks;
    private int rechecksCooldown;
    private BlockPos targetBlock;
    private float breakingProgress;
    private Vec3d lastPos;

    public ZombieBreakBlockGoal(ZombieEntity zombie) {
        this.zombie = zombie;
        this.world = zombie.getWorld();
        this.setControls(EnumSet.of(Goal.Control.MOVE));
    }

    @Override
    public boolean canStart() {
        if (!this.zombie.isAlive()) {
            return false;
        }

        PlayerEntity target = this.world.getClosestPlayer(this.zombie, 16.0);
        if (target == null) {
            this.resetStuckTracking();
            return false;
        }

        // Detecta si el zombie está "trabado" comparando posición vs. tick anterior
        Vec3d currentPos = this.zombie.getPos();
        if (this.lastPos != null && this.lastPos.squaredDistanceTo(currentPos) < 0.0009) {
            this.stuckTicks++;
        } else {
            this.stuckTicks = 0;
        }
        this.lastPos = currentPos;

        if (this.stuckTicks < STUCK_TICKS_THRESHOLD) {
            return false;
        }

        return this.findBreakableBlockTowards(target) != null;
    }

    @Override
    public boolean shouldContinue() {
        return this.targetBlock != null
                && this.zombie.isAlive()
                && this.isBreakable(this.targetBlock)
                && this.zombie.squaredDistanceTo(
                        this.targetBlock.getX() + 0.5, this.targetBlock.getY() + 0.5, this.targetBlock.getZ() + 0.5)
                        < 6.0;
    }

    @Override
    public void start() {
        PlayerEntity target = this.world.getClosestPlayer(this.zombie, 16.0);
        this.targetBlock = target != null ? this.findBreakableBlockTowards(target) : null;
        this.breakingProgress = 0f;
        this.rechecksCooldown = RECHECK_COOLDOWN;
    }

    @Override
    public void stop() {
        if (this.targetBlock != null && this.world instanceof ServerWorld serverWorld) {
            // Limpia el crack overlay si abandonamos a mitad de rotura
            serverWorld.setBlockBreakingInfo(this.zombie.getId(), this.targetBlock, -1);
        }
        this.targetBlock = null;
        this.breakingProgress = 0f;
        this.stuckTicks = 0;
    }

    @Override
    public void tick() {
        if (this.targetBlock == null) {
            return;
        }

        // Mira/navega hacia el bloque para que la animación tenga sentido
        this.zombie.getLookControl().lookAt(
                this.targetBlock.getX() + 0.5,
                this.targetBlock.getY() + 0.5,
                this.targetBlock.getZ() + 0.5
        );
        this.zombie.getNavigation().startMovingTo(
                this.targetBlock.getX() + 0.5,
                this.targetBlock.getY() + 0.5,
                this.targetBlock.getZ() + 0.5,
                1.0
        );

        if (--this.rechecksCooldown <= 0) {
            this.rechecksCooldown = RECHECK_COOLDOWN;
            if (!this.isBreakable(this.targetBlock)) {
                this.targetBlock = null;
                return;
            }
        }

        this.breakingProgress += BREAK_SPEED_PER_TICK;

        if (this.world instanceof ServerWorld serverWorld) {
            int stage = (int) (this.breakingProgress * 9.0f); // 0-9 como el crack overlay vanilla
            serverWorld.setBlockBreakingInfo(this.zombie.getId(), this.targetBlock, Math.min(stage, 9));

            if (this.zombie.age % 4 == 0) {
                BlockState state = this.world.getBlockState(this.targetBlock);
                serverWorld.spawnParticles(
                        new BlockStateParticleEffect(ParticleTypes.BLOCK, state),
                        this.targetBlock.getX() + 0.5, this.targetBlock.getY() + 0.5, this.targetBlock.getZ() + 0.5,
                        3, 0.2, 0.2, 0.2, 0.0
                );
                this.world.playSound(null, this.targetBlock, state.getSoundGroup().getHitSound(),
                        SoundCategory.HOSTILE, 0.25f, 0.9f);
            }
        }

        if (this.breakingProgress >= 1.0f) {
            this.breakBlock();
        }
    }

    private void breakBlock() {
        if (!(this.world instanceof ServerWorld serverWorld) || this.targetBlock == null) {
            return;
        }

        BlockState state = this.world.getBlockState(this.targetBlock);
        serverWorld.setBlockBreakingInfo(this.zombie.getId(), this.targetBlock, -1);
        serverWorld.breakBlock(this.targetBlock, false, this.zombie);
        serverWorld.emitGameEvent(this.zombie, GameEvent.BLOCK_DESTROY, this.targetBlock);
        serverWorld.playSound(null, this.targetBlock, state.getSoundGroup().getBreakSound(),
                SoundCategory.HOSTILE, 0.7f, 0.8f);

        this.targetBlock = null;
        this.breakingProgress = 0f;
        this.stuckTicks = 0;
    }

    /**
     * Busca, entre los bloques adyacentes al zombie en dirección al jugador,
     * el primero que esté en la whitelist de bloques rompibles.
     */
    private BlockPos findBreakableBlockTowards(PlayerEntity target) {
        BlockPos zombiePos = this.zombie.getBlockPos();
        Vec3d direction = target.getPos().subtract(this.zombie.getPos()).normalize();

        BlockPos.Mutable checkPos = new BlockPos.Mutable();
        // Chequea a la altura de los pies y de la cabeza, un par de bloques hacia el jugador
        for (int step = 1; step <= 2; step++) {
            for (int yOffset = 0; yOffset <= 1; yOffset++) {
                checkPos.set(
                        zombiePos.getX() + (int) Math.round(direction.x * step),
                        zombiePos.getY() + yOffset,
                        zombiePos.getZ() + (int) Math.round(direction.z * step)
                );
                BlockPos candidate = checkPos.toImmutable();
                if (this.isBreakable(candidate)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private boolean isBreakable(BlockPos pos) {
        BlockState state = this.world.getBlockState(pos);
        if (state.isAir()) {
            return false;
        }
        Block block = state.getBlock();
        return state.isIn(ModBlockTags.BREAKABLE_BY_ZOMBIES) && state.getHardness(this.world, pos) >= 0;
    }

    private void resetStuckTracking() {
        this.stuckTicks = 0;
        this.lastPos = null;
    }
}

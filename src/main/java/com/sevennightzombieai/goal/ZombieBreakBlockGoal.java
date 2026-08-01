package com.sevennightzombieai.goal;

import com.sevennightzombieai.tag.ModBlockTags;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.attribute.EntityAttributes;
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

public class ZombieBreakBlockGoal extends Goal {

    private static final int STUCK_TICKS_THRESHOLD = 40;
    private static final int RECHECK_COOLDOWN = 20;
    private static final float BREAK_SPEED_PER_TICK = 0.05f;

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
        this.setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (!this.zombie.isAlive()) {
            return false;
        }

        double followRange = this.zombie.getAttributeValue(EntityAttributes.GENERIC_FOLLOW_RANGE);
        PlayerEntity target = this.world.getClosestPlayer(this.zombie, followRange);
        if (target == null) {
            this.resetStuckTracking();
            return false;
        }

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
        PlayerEntity target = this.world.getClosestPlayer(
                this.zombie, this.zombie.getAttributeValue(EntityAttributes.GENERIC_FOLLOW_RANGE));
        this.targetBlock = target != null ? this.findBreakableBlockTowards(target) : null;
        this.breakingProgress = 0f;
        this.rechecksCooldown = RECHECK_COOLDOWN;
    }

    @Override
    public void stop() {
        if (this.targetBlock != null && this.world instanceof ServerWorld serverWorld) {
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
            int stage = (int) (this.breakingProgress * 9.0f);
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

    private BlockPos findBreakableBlockTowards(PlayerEntity target) {
        BlockPos zombiePos = this.zombie.getBlockPos();
        Vec3d toTarget = target.getPos().subtract(this.zombie.getPos());

        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy <= 1; dy++) {
                    BlockPos candidate = zombiePos.add(dx, dy, dz);
                    if (!this.isBreakable(candidate)) {
                        continue;
                    }

                    Vec3d toBlock = new Vec3d(
                            candidate.getX() + 0.5,
                            candidate.getY() + 0.5,
                            candidate.getZ() + 0.5
                    ).subtract(this.zombie.getPos());

                    double score = toBlock.lengthSquared();
                    if (toTarget.dotProduct(toBlock) > 0) {
                        score -= 4.0;
                    }
                    if (score < bestScore) {
                        bestScore = score;
                        best = candidate;
                    }
                }
            }
        }
        return best;
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
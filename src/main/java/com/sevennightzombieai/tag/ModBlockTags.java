package com.sevennightzombieai.tag;

import com.sevennightzombieai.SevenNightZombieAIMod;
import net.minecraft.block.Block;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

/**
 * Tags custom del mod. El whitelist de bloques rompibles por zombies
 * vive en el datapack: data/sevennightzombieai/tags/block/breakable_by_zombies.json
 * Así el usuario final puede extender/reducir la lista con datapacks sin tocar código.
 */
public final class ModBlockTags {

    private ModBlockTags() {}

    public static final TagKey<Block> BREAKABLE_BY_ZOMBIES = TagKey.of(
            RegistryKeys.BLOCK,
            Identifier.of(SevenNightZombieAIMod.MOD_ID, "breakable_by_zombies")
    );
}

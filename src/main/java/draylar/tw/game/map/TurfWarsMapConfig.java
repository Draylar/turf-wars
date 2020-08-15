package draylar.tw.game.map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import xyz.nucleoid.plasmid.game.map.template.MapTemplate;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;

public class TurfWarsMapConfig {
    public static final Codec<TurfWarsMapConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockState.CODEC.fieldOf("spawn_block").forGetter(map -> map.spawnBlock)
    ).apply(instance, TurfWarsMapConfig::new));

    public final BlockState spawnBlock;

    public TurfWarsMapConfig(BlockState spawnBlock) {
        this.spawnBlock = spawnBlock;
    }
}

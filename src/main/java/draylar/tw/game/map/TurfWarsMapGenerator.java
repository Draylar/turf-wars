package draylar.tw.game.map;

import xyz.nucleoid.plasmid.map.template.MapTemplate;
import xyz.nucleoid.plasmid.util.BlockBounds;
import net.minecraft.block.Blocks;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import draylar.tw.game.TurfWarsConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class TurfWarsMapGenerator {

    private final TurfWarsMapConfig config;
    public final static List<BlockPos> centerCirclePositions = new ArrayList<>();

    static {
        for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) {
                double distance = Math.sqrt(Math.pow(x, 2) + Math.pow(z, 2));

                if(distance <= 4.1) {
                    centerCirclePositions.add(new BlockPos(x + 32, 64, z));
                }
            }
        }
    }

    public TurfWarsMapGenerator(TurfWarsMapConfig config) {
        this.config = config;
    }

    public TurfWarsMap build() {
        MapTemplate template = MapTemplate.createEmpty();
        TurfWarsMap map = new TurfWarsMap(template, this.config);

        this.buildSpawn(template);
        map.spawn = new BlockPos(32,67,0);

        return map;
    }

    private void buildSpawn(MapTemplate builder) {
        BlockPos min = new BlockPos(0, 64, -48);
        BlockPos mid = new BlockPos(64, 64, 0);
        BlockPos max = new BlockPos(0, 64, 48);

        for (BlockPos pos : BlockPos.iterate(min, mid)) {
            builder.setBlockState(pos, Blocks.RED_TERRACOTTA.getDefaultState());
        }

        for (BlockPos pos : BlockPos.iterate(mid, max)) {
            builder.setBlockState(pos, Blocks.LIGHT_BLUE_TERRACOTTA.getDefaultState());
        }

        centerCirclePositions.forEach(pos -> {
            builder.setBlockState(pos, Blocks.PURPLE_TERRACOTTA.getDefaultState());
        });
    }
}

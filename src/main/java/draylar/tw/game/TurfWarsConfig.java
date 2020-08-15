package draylar.tw.game;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import xyz.nucleoid.plasmid.game.config.PlayerConfig;
import draylar.tw.game.map.TurfWarsMapConfig;

public class TurfWarsConfig {

    public static final Codec<TurfWarsConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            PlayerConfig.CODEC.fieldOf("players").forGetter(config -> config.playerConfig),
            TurfWarsMapConfig.CODEC.fieldOf("map").forGetter(config -> config.mapConfig),
            Codec.INT.fieldOf("time_limit_secs").forGetter(config -> config.timeLimitSecs)
    ).apply(instance, TurfWarsConfig::new));

    public final PlayerConfig playerConfig;
    public final TurfWarsMapConfig mapConfig;
    public final int timeLimitSecs;

    public TurfWarsConfig(PlayerConfig players, TurfWarsMapConfig mapConfig, int timeLimitSecs) {
        this.playerConfig = players;
        this.mapConfig = mapConfig;
        this.timeLimitSecs = timeLimitSecs;
    }
}

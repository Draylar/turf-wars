package draylar.tw;

import net.fabricmc.api.ModInitializer;
import xyz.nucleoid.plasmid.game.GameType;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import draylar.tw.game.TurfWarsConfig;
import draylar.tw.game.TurfWarsWaiting;

public class TurfWars implements ModInitializer {

    public static final String ID = "turfwars";
    public static final Logger LOGGER = LogManager.getLogger(ID);

    public static final GameType<TurfWarsConfig> TYPE = GameType.register(
            new Identifier(ID, "turfwars"),
            TurfWarsWaiting::open,
            TurfWarsConfig.CODEC
    );

    @Override
    public void onInitialize() {}
}

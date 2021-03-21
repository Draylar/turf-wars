package draylar.tw.game;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ActionResult;
import xyz.nucleoid.plasmid.game.GameOpenContext;
import xyz.nucleoid.plasmid.game.GameOpenProcedure;
import xyz.nucleoid.plasmid.game.GameSpace;
import xyz.nucleoid.plasmid.game.StartResult;
import xyz.nucleoid.plasmid.game.config.PlayerConfig;
import xyz.nucleoid.plasmid.game.event.OfferPlayerListener;
import xyz.nucleoid.plasmid.game.event.PlayerAddListener;
import xyz.nucleoid.plasmid.game.event.PlayerDeathListener;
import xyz.nucleoid.plasmid.game.event.RequestStartListener;
import xyz.nucleoid.plasmid.game.player.JoinResult;
import xyz.nucleoid.plasmid.game.rule.GameRule;
import xyz.nucleoid.plasmid.game.rule.RuleResult;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameMode;
import draylar.tw.game.map.TurfWarsMap;
import draylar.tw.game.map.TurfWarsMapGenerator;
import xyz.nucleoid.fantasy.BubbleWorldConfig;

import java.util.concurrent.CompletableFuture;

public class TurfWarsWaiting {
    private final GameSpace gameSpace;
    private final TurfWarsMap map;
    private final TurfWarsConfig config;
    private final TurfWarsSpawnLogic spawnLogic;

    private TurfWarsWaiting(GameSpace gameSpace, TurfWarsMap map, TurfWarsConfig config) {
        this.gameSpace = gameSpace;
        this.map = map;
        this.config = config;
        this.spawnLogic = new TurfWarsSpawnLogic(gameSpace, map);
    }

    public static GameOpenProcedure open(GameOpenContext<TurfWarsConfig> context) {
        TurfWarsMapGenerator generator = new TurfWarsMapGenerator(context.getConfig().mapConfig);
        TurfWarsMap map = generator.build();

        BubbleWorldConfig worldConfig = new BubbleWorldConfig()
                .setGenerator(map.asGenerator(context.getServer()))
                .setDefaultGameMode(GameMode.SPECTATOR);

        return context.createOpenProcedure(worldConfig, game -> {
            TurfWarsWaiting waiting = new TurfWarsWaiting(game.getSpace(), map, context.getConfig());

            game.setRule(GameRule.CRAFTING, RuleResult.DENY);
            game.setRule(GameRule.PORTALS, RuleResult.DENY);
            game.setRule(GameRule.PVP, RuleResult.DENY);
            game.setRule(GameRule.BLOCK_DROPS, RuleResult.DENY);
            game.setRule(GameRule.HUNGER, RuleResult.DENY);
            game.setRule(GameRule.FALL_DAMAGE, RuleResult.DENY);

            game.on(RequestStartListener.EVENT, waiting::requestStart);
            game.on(OfferPlayerListener.EVENT, waiting::offerPlayer);

            game.on(PlayerAddListener.EVENT, waiting::addPlayer);
            game.on(PlayerDeathListener.EVENT, waiting::onPlayerDeath);
        });
    }

    private JoinResult offerPlayer(ServerPlayerEntity player) {
        if (this.gameSpace.getPlayerCount() >= this.config.playerConfig.getMaxPlayers()) {
            return JoinResult.gameFull();
        }

        return JoinResult.ok();
    }

    private StartResult requestStart() {
        PlayerConfig playerConfig = this.config.playerConfig;
        if (this.gameSpace.getPlayerCount() < playerConfig.getMinPlayers()) {
            return StartResult.NOT_ENOUGH_PLAYERS;
        }

        TurfWarsActive.open(this.gameSpace, this.map, this.config);

        return StartResult.OK;
    }

    private void addPlayer(ServerPlayerEntity player) {
        this.spawnPlayer(player);
    }

    private ActionResult onPlayerDeath(ServerPlayerEntity player, DamageSource source) {
        this.spawnPlayer(player);
        return ActionResult.FAIL;
    }

    private void spawnPlayer(ServerPlayerEntity player) {
        this.spawnLogic.resetPlayer(player, GameMode.ADVENTURE);
        this.spawnLogic.spawnPlayer(player);
    }
}

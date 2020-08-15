package draylar.tw.game;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ActionResult;
import xyz.nucleoid.plasmid.game.GameOpenContext;
import xyz.nucleoid.plasmid.game.GameWorld;
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
import xyz.nucleoid.plasmid.game.world.bubble.BubbleWorldConfig;

import java.util.concurrent.CompletableFuture;

public class TurfWarsWaiting {
    private final GameWorld gameWorld;
    private final TurfWarsMap map;
    private final TurfWarsConfig config;
    private final TurfWarsSpawnLogic spawnLogic;

    private TurfWarsWaiting(GameWorld gameWorld, TurfWarsMap map, TurfWarsConfig config) {
        this.gameWorld = gameWorld;
        this.map = map;
        this.config = config;
        this.spawnLogic = new TurfWarsSpawnLogic(gameWorld, map);
    }

    public static CompletableFuture<Void> open(GameOpenContext<TurfWarsConfig> context) {
        TurfWarsMapGenerator generator = new TurfWarsMapGenerator(context.getConfig().mapConfig);

        return generator.create().thenAccept(map -> {
            BubbleWorldConfig worldConfig = new BubbleWorldConfig()
                    .setGenerator(map.asGenerator(context.getServer()))
                    .setDefaultGameMode(GameMode.SPECTATOR);

            GameWorld gameWorld = context.openWorld(worldConfig);
            TurfWarsWaiting waiting = new TurfWarsWaiting(gameWorld, map, context.getConfig());

            gameWorld.openGame(builder -> {
                builder.setRule(GameRule.CRAFTING, RuleResult.DENY);
                builder.setRule(GameRule.PORTALS, RuleResult.DENY);
                builder.setRule(GameRule.PVP, RuleResult.DENY);
                builder.setRule(GameRule.BLOCK_DROPS, RuleResult.DENY);
                builder.setRule(GameRule.HUNGER, RuleResult.DENY);
                builder.setRule(GameRule.FALL_DAMAGE, RuleResult.DENY);

                builder.on(RequestStartListener.EVENT, waiting::requestStart);
                builder.on(OfferPlayerListener.EVENT, waiting::offerPlayer);

                builder.on(PlayerAddListener.EVENT, waiting::addPlayer);
                builder.on(PlayerDeathListener.EVENT, waiting::onPlayerDeath);
            });
        });
    }

    private JoinResult offerPlayer(ServerPlayerEntity player) {
        if (this.gameWorld.getPlayerCount() >= this.config.playerConfig.getMaxPlayers()) {
            return JoinResult.gameFull();
        }

        return JoinResult.ok();
    }

    private StartResult requestStart() {
        PlayerConfig playerConfig = this.config.playerConfig;
        if (this.gameWorld.getPlayerCount() < playerConfig.getMinPlayers()) {
            return StartResult.notEnoughPlayers();
        }

        TurfWarsActive.open(this.gameWorld, this.map, this.config);

        return StartResult.ok();
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

package draylar.tw.game;

import draylar.tw.game.team.Team;
import io.netty.util.internal.logging.AbstractInternalLogger;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.client.particle.BlockDustParticle;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ArrowItem;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.tag.ItemTags;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.EntityHitResult;
import xyz.nucleoid.plasmid.game.GameSpace;
import xyz.nucleoid.plasmid.game.GameCloseReason;
import xyz.nucleoid.plasmid.game.event.*;
import xyz.nucleoid.plasmid.game.player.JoinResult;
import xyz.nucleoid.plasmid.game.rule.GameRule;
import xyz.nucleoid.plasmid.game.rule.RuleResult;
import xyz.nucleoid.plasmid.util.BlockBounds;
import xyz.nucleoid.plasmid.util.PlayerRef;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import draylar.tw.TurfWars;
import draylar.tw.game.map.TurfWarsMap;

import java.util.*;
import java.util.stream.Collectors;

public class TurfWarsActive {
    private final TurfWarsConfig config;

    public final GameSpace gameSpace;
    private final TurfWarsMap gameMap;

    // TODO replace with ServerPlayerEntity if players are removed upon leaving
    private final Object2ObjectMap<PlayerRef, TurfWarsPlayer> participants;
    private final TurfWarsSpawnLogic spawnLogic;
    private final TurfWarsIdle idle;
    private final boolean ignoreWinState;
    private final TurfWarsTimerBar timerBar;

    // phase
    private boolean isBuildTime = true;
    private int currentPhaseProgress = 0;
    private int maxPhaseTime = 60 * 20;

    private TurfWarsActive(GameSpace gameSpace, TurfWarsMap map, TurfWarsConfig config, Set<PlayerRef> participants) {
        this.gameSpace = gameSpace;
        this.config = config;
        this.gameMap = map;
        this.spawnLogic = new TurfWarsSpawnLogic(gameSpace, map);
        this.participants = new Object2ObjectOpenHashMap<>();

        List<PlayerRef> randomizedPlayers = new ArrayList<>(participants);
        Collections.shuffle(randomizedPlayers);

        for (PlayerRef player : participants) {
            int playerCount = randomizedPlayers.size();
            int half = playerCount / 2;
            int index = randomizedPlayers.indexOf(player);
            this.participants.put(player, new TurfWarsPlayer(index <= half ? Team.BLUE : Team.RED));
        }

        this.idle = new TurfWarsIdle();
        this.ignoreWinState = this.participants.size() <= 1;
        this.timerBar = new TurfWarsTimerBar();
    }

    public static void open(GameSpace gameSpace, TurfWarsMap map, TurfWarsConfig config) {
        Set<PlayerRef> participants = gameSpace.getPlayers().stream()
                .map(PlayerRef::of)
                .collect(Collectors.toSet());
        TurfWarsActive active = new TurfWarsActive(gameSpace, map, config, participants);

        gameSpace.openGame(builder -> {
            builder.setRule(GameRule.CRAFTING, RuleResult.DENY);
            builder.setRule(GameRule.PORTALS, RuleResult.DENY);
            builder.setRule(GameRule.PVP, RuleResult.ALLOW);
            builder.setRule(GameRule.HUNGER, RuleResult.DENY);
            builder.setRule(GameRule.FALL_DAMAGE, RuleResult.ALLOW);
            builder.setRule(GameRule.INTERACTION, RuleResult.ALLOW);
            builder.setRule(GameRule.BLOCK_DROPS, RuleResult.DENY);
            builder.setRule(GameRule.THROW_ITEMS, RuleResult.DENY);
            builder.setRule(GameRule.UNSTABLE_TNT, RuleResult.DENY);

            builder.on(GameOpenListener.EVENT, active::onOpen);
            builder.on(GameCloseListener.EVENT, active::onClose);

            builder.on(OfferPlayerListener.EVENT, player -> JoinResult.ok());
            builder.on(PlayerAddListener.EVENT, active::addPlayer);

            builder.on(GameTickListener.EVENT, active::tick);

            builder.on(PlayerDamageListener.EVENT, active::onPlayerDamage);
            builder.on(PlayerDeathListener.EVENT, active::onPlayerDeath);
            builder.on(AttackEntityListener.EVENT, active::onAttackEntity);

            builder.on(UseItemListener.EVENT, active::onUseItem);
        });
    }

    private void onOpen() {
        ServerWorld world = this.gameSpace.getWorld();

        for (PlayerRef ref : this.participants.keySet()) {
            ref.ifOnline(world, this::spawnParticipant);
        }

        this.idle.onOpen(world.getTime(), this.config);
        // TODO setup logic
        timerBar.setReverse(true);

    }

    private void onClose() {
        this.timerBar.close();
        // TODO teardown logic
    }

    private void addPlayer(ServerPlayerEntity player) {
        if (!this.participants.containsKey(PlayerRef.of(player))) {
            this.spawnSpectator(player);
        }

        this.timerBar.addPlayer(player);
    }

    private ActionResult onPlayerDamage(ServerPlayerEntity player, DamageSource source, float amount) {
        if(source.getAttacker() != null && source.getAttacker() instanceof PlayerEntity) {
            this.spawnParticipant(player);
        }

        return ActionResult.FAIL;
    }

    private ActionResult onPlayerDeath(ServerPlayerEntity player, DamageSource source) {
        this.spawnParticipant(player);

        if(source.getAttacker() instanceof PlayerEntity) {
            ((PlayerEntity) source.getAttacker()).inventory.offerOrDrop(player.world, new ItemStack(Items.ARROW));
        }

        return ActionResult.FAIL;
    }

    private ActionResult onAttackEntity(ServerPlayerEntity serverPlayerEntity, Hand hand, Entity entity, EntityHitResult entityHitResult) {
        if(isBuildTime) {
            return ActionResult.FAIL;
        }

        return ActionResult.PASS;
    }


    private TypedActionResult<ItemStack> onUseItem(ServerPlayerEntity serverPlayerEntity, Hand hand) {
        ItemStack heldStack = serverPlayerEntity.getStackInHand(hand);

        if(!isBuildTime) {
            if(heldStack.getItem() instanceof BlockItem) {
                return TypedActionResult.fail(heldStack);
            }
        }

        return TypedActionResult.pass(heldStack);
    }

    private void spawnParticipant(ServerPlayerEntity player) {
        this.spawnLogic.resetPlayer(player, GameMode.ADVENTURE, participants.get(PlayerRef.of(player)).getTeam());
        this.spawnLogic.spawnPlayer(player, participants.get(PlayerRef.of(player)).getTeam());
    }

    private void spawnSpectator(ServerPlayerEntity player) {
        this.spawnLogic.resetPlayer(player, GameMode.SPECTATOR);
        this.spawnLogic.spawnPlayer(player);
    }

    private void tick() {
        ServerWorld world = this.gameSpace.getWorld();
        long time = world.getTime();

        TurfWarsIdle.IdleTickResult result = this.idle.tick(time, gameSpace);

        switch (result) {
            case CONTINUE_TICK:
                break;
            case TICK_FINISHED:
                return;
            case GAME_FINISHED:
                this.broadcastWin(this.checkWinResult());
                return;
            case GAME_CLOSED:
                this.gameSpace.close(GameCloseReason.FINISHED);
                return;
        }

        this.timerBar.update(currentPhaseProgress, maxPhaseTime);

        // update phase
        if(currentPhaseProgress++ >= maxPhaseTime) {
            currentPhaseProgress = 0;
            isBuildTime = !isBuildTime;

            if(isBuildTime) {
                broadcastMessage(new LiteralText("It is now build time!"), gameSpace);
                timerBar.setPrefix("Build Time - ");
                maxPhaseTime = 45 * 20;
            } else {
                broadcastMessage(new LiteralText("It is battle time!"), gameSpace);
                timerBar.setPrefix("Battle Time - ");
                maxPhaseTime = 90 * 20;
            }
        }

        // update arrows in inventory
        participants.forEach((playerRef, turfWarsPlayer) -> {
            ServerPlayerEntity player = playerRef.getEntity(world);

            if(player != null) {
                int arrowCount = player.inventory.method_29280(stack -> stack.getItem() instanceof ArrowItem, 0, new SimpleInventory());

                if(arrowCount < 3) {
                    turfWarsPlayer.incrementArrowCooldown();

                    if(turfWarsPlayer.getArrowCooldown() >= 20 * 5) {
                        turfWarsPlayer.resetArrowCooldown();
                        player.inventory.offerOrDrop(world, new ItemStack(Items.ARROW));
                    }
                }
            }
        });

        // don't let players cross boundaries
        // blue = +z, red = -z
        participants.forEach((playerRef, turfWarsPlayer) -> {
            ServerPlayerEntity player = playerRef.getEntity(world);

            if(player != null) {
                double zPos = player.getPos().z;

                if(zPos < 0 && turfWarsPlayer.getTeam().equals(Team.BLUE)) {
                    BlockState underState = world.getBlockState(player.getBlockPos().down());
                    BlockState under2State = world.getBlockState(player.getBlockPos().down(2));

                    if(!underState.getBlock().equals(Blocks.PURPLE_TERRACOTTA) && !under2State.getBlock().equals(Blocks.PURPLE_TERRACOTTA)) {
                        player.addVelocity(0, .5, 1);
                        player.velocityModified = true;
                    }
                }

                else if (zPos > 0 && turfWarsPlayer.getTeam().equals(Team.RED)) {
                    BlockState underState = world.getBlockState(player.getBlockPos().down());
                    BlockState under2State = world.getBlockState(player.getBlockPos().down(2));

                    if(!underState.getBlock().equals(Blocks.PURPLE_TERRACOTTA) && !under2State.getBlock().equals(Blocks.PURPLE_TERRACOTTA)) {
                        player.addVelocity(0, .5, -1);
                        player.velocityModified = true;
                    }
                }
            }
        });
    }

    protected static void broadcastMessage(Text message, GameSpace space) {
        for (ServerPlayerEntity player : space.getPlayers()) {
            player.sendMessage(message, false);
        };
    }

    protected static void broadcastSound(SoundEvent sound, float pitch, GameSpace space) {
        for (ServerPlayerEntity player : space.getPlayers()) {
            player.playSound(sound, SoundCategory.PLAYERS, 1.0F, pitch);
        };
    }

    protected static void broadcastSound(SoundEvent sound,  GameSpace space) {
        broadcastSound(sound, 1.0f, space);
    }

    protected static void broadcastTitle(Text message, GameSpace space) {
        for (ServerPlayerEntity player : space.getPlayers()) {
            TitleS2CPacket packet = new TitleS2CPacket(TitleS2CPacket.Action.TITLE, message, 1, 5,  3);
            player.networkHandler.sendPacket(packet);
        }
    }

    private void broadcastWin(WinResult result) {
        ServerPlayerEntity winningPlayer = result.getWinningPlayer();

        Text message;
        if (winningPlayer != null) {
            message = winningPlayer.getDisplayName().shallowCopy().append(" has won the game!").formatted(Formatting.GOLD);
        } else {
            message = new LiteralText("The game ended, but nobody won!").formatted(Formatting.GOLD);
        }

        broadcastMessage(message, this.gameSpace);
        broadcastSound(SoundEvents.ENTITY_VILLAGER_YES, this.gameSpace);
    }

    private WinResult checkWinResult() {
        // for testing purposes: don't end the game if we only ever had one participant
        if (this.ignoreWinState) {
            return WinResult.no();
        }

        ServerWorld world = this.gameSpace.getWorld();
        ServerPlayerEntity winningPlayer = null;

        // TODO win result logic
        return WinResult.no();
    }

    static class WinResult {
        final ServerPlayerEntity winningPlayer;
        final boolean win;

        private WinResult(ServerPlayerEntity winningPlayer, boolean win) {
            this.winningPlayer = winningPlayer;
            this.win = win;
        }

        static WinResult no() {
            return new WinResult(null, false);
        }

        static WinResult win(ServerPlayerEntity player) {
            return new WinResult(player, true);
        }

        public boolean isWin() {
            return this.win;
        }

        public ServerPlayerEntity getWinningPlayer() {
            return this.winningPlayer;
        }
    }
}

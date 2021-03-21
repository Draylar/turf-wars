package draylar.tw.game;

import draylar.tw.game.team.Team;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.DyeableArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import xyz.nucleoid.plasmid.game.GameSpace;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.GameMode;
import draylar.tw.TurfWars;
import draylar.tw.game.map.TurfWarsMap;
import xyz.nucleoid.plasmid.util.ItemStackBuilder;

public class TurfWarsSpawnLogic {

    private final GameSpace gameSpace;
    private final TurfWarsMap map;

    public TurfWarsSpawnLogic(GameSpace gameSpace, TurfWarsMap map) {
        this.gameSpace = gameSpace;
        this.map = map;
    }

    public void resetPlayer(ServerPlayerEntity player, GameMode gameMode) {
        player.inventory.clear();
        player.setGameMode(gameMode);

        if(gameMode != GameMode.SPECTATOR) {
            player.inventory.offerOrDrop(player.world, new ItemStack(Items.BOW));
            player.inventory.offerOrDrop(player.world, new ItemStack(Items.ARROW, 3));
            player.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        }

        player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.NIGHT_VISION,
                20 * 60 * 60,
                1,
                true,
                false
        ));
    }

    public void resetPlayer(ServerPlayerEntity player, GameMode gameMode, Team team) {
        player.inventory.clear();
        player.setGameMode(gameMode);

        if(gameMode != GameMode.SPECTATOR) {
            player.inventory.offerOrDrop(player.world, new ItemStack(Items.BOW));
            player.inventory.offerOrDrop(player.world, new ItemStack(Items.ARROW, 3));
            player.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));

            if(team.equals(Team.BLUE)) {
                ItemStackBuilder builder = ItemStackBuilder.of(new ItemStack(Items.LIGHT_BLUE_WOOL, 64))
                        .addCanPlaceOn(Blocks.LIGHT_BLUE_WOOL)
                        .addCanPlaceOn(Blocks.LIGHT_BLUE_TERRACOTTA);

                player.inventory.offerOrDrop(player.world, builder.build());
            } else {
                ItemStackBuilder builder = ItemStackBuilder.of(new ItemStack(Items.RED_WOOL, 64))
                        .addCanPlaceOn(Blocks.RED_WOOL)
                        .addCanPlaceOn(Blocks.RED_TERRACOTTA);

                player.inventory.offerOrDrop(player.world, builder.build());
            }
        }

        player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.NIGHT_VISION,
                20 * 60 * 60,
                1,
                true,
                false
        ));
    }

    public void spawnPlayer(ServerPlayerEntity player) {
        ServerWorld world = this.gameSpace.getWorld();

        BlockPos pos = this.map.spawn;
        if (pos == null) {
            TurfWars.LOGGER.warn("Cannot spawn player! No spawn is defined in the map!");
            return;
        }

        float radius = 4.5f;
        double x = pos.getX() + MathHelper.nextDouble(player.getRandom(), -radius, radius);
        double z = pos.getZ() + MathHelper.nextFloat(player.getRandom(), -radius, radius);

        player.teleport(world, x, pos.getY() + 0.5, z, 0.0F, 0.0F);
    }

    public void spawnPlayer(ServerPlayerEntity player, Team team) {
        ServerWorld world = this.gameSpace.getWorld();

        BlockPos pos = this.map.spawn;
        if (pos == null) {
            TurfWars.LOGGER.warn("Cannot spawn player! No spawn is defined in the map!");
            return;
        }

        float radius = 4.5f;
        double x = pos.getX() + MathHelper.nextDouble(player.getRandom(), -radius, radius);
        double z = 46 * (team.equals(Team.BLUE) ? 1 : -1);

        player.teleport(world, x, pos.getY() + 0.5, z, 0.0F, 0.0F);
    }
}

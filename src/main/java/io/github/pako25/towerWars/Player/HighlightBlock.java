package io.github.pako25.towerWars.Player;

import io.github.pako25.towerWars.TowerWars;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * 可放塔方块高亮：持有"放置塔"物品时，准星指向赛道内可放塔方块会
 * 持续冒出暴击粒子（每 4 tick 一次），提示玩家这里能放塔。
 */
public class HighlightBlock extends BukkitRunnable {

    public static HighlightBlock HighlightBlockFactory() {
        HighlightBlock i = new HighlightBlock();
        i.start();
        return i;
    }

    @Override
    public void run() {
        for (TWPlayer twPlayer : TWPlayer.getTWPlayerCollection()) {
            if (isHoldingPlaceTowerItem(twPlayer.getPlayer()) && twPlayer.isInGame()) {
                if (twPlayer.isAttacker()) continue; // 围攻进攻者没有赛道，无放塔高亮
                Block targetBlock = twPlayer.getPlayer().getTargetBlockExact(30);
                if (targetBlock == null) continue;
                if (targetBlock.getType() != twPlayer.getGame().getTowerPlaceMaterial()) continue; // 非放塔方块：跳过本玩家，继续处理其他玩家
                if (twPlayer.getTrack().isLocationInsideTrackBounds(targetBlock.getLocation())) {
                    spawnHighlightParticles(targetBlock, twPlayer.getPlayer());
                }
            }
        }
    }

    private boolean isHoldingPlaceTowerItem(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        return item.getType() == Material.ARMOR_STAND;
    }

    private void spawnHighlightParticles(Block block, Player player) {
        player.spawnParticle(
                Particle.CRIT,
                block.getLocation().add(0.5, 1.0, 0.5),
                10,            // 粒子数量
                0.3, 0.3, 0.3, // 扩散范围（XYZ 偏移）
                0.05           // 速度
        );
    }

    public void start() {
        this.runTaskTimer(TowerWars.getPlugin(), 0L, 4L);
    }

    public void stop() {
        this.cancel();
    }
}

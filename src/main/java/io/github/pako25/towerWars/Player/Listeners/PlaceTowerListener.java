package io.github.pako25.towerWars.Player.Listeners;

import io.github.pako25.towerWars.GameManagement.Game;
import io.github.pako25.towerWars.Player.TWPlayer;
import io.github.pako25.towerWars.Tower.Tower;
import io.github.pako25.towerWars.message.Messages;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.RayTraceResult;

import java.util.Collection;

/**
 * 放置塔物品（盔甲架）右键监听器：
 * ① 准星先做 30 格实体射线——命中塔身实体则打开该塔的升级菜单；
 * ② 否则检查准星方块是否是可放塔的方块（竞技场配置的 towerPlaceMaterial），
 *    是则放置塔（已占用则打开升级菜单）。
 */
public class PlaceTowerListener implements Listener {

    /** 游戏物品显示名（必须与 Messages.Gui.itemPlaceTower 保持一致，改动需同步！） */
    private static final String ITEM_NAME = "放置塔";

    @EventHandler
    public void onPlayerRightClick(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item.getType() != Material.ARMOR_STAND) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return;
        if (!ChatColor.stripColor(meta.getDisplayName()).equals(ITEM_NAME)) return;
        event.setCancelled(true);
        TWPlayer twPlayer = TWPlayer.getTWPlayer(player.getUniqueId());
        if (!twPlayer.isInGame()) return;
        if (twPlayer.isAttacker()) {
            // 围攻进攻者没有赛道，不能放塔
            player.sendMessage(Messages.Game.attackerCannotPlaceTower());
            return;
        }

        // ① 实体射线：点中塔身 → 打开该塔升级菜单
        RayTraceResult result = player.getWorld().rayTraceEntities(player.getEyeLocation(), player.getEyeLocation().getDirection(), 30, entity -> !entity.equals(player));
        if (result != null) {
            Entity target = result.getHitEntity();
            boolean found = false;
            if (target instanceof Mob mob) {
                Collection<Tower> towers = twPlayer.getTrack().getTowers().values();
                for (Tower tower : towers) {
                    if (tower.isEntityInTower(mob)) {
                        found = true;
                        twPlayer.openTowerMenu(tower.getLocation());
                    }
                }
            }
            if (found) return;
        }

        // ② 方块检查：准星指向可放塔方块 → 放塔/开塔菜单。
        // 未对准时给提示而不是静默返回——否则玩家会以为放置功能没实现
        Game game = twPlayer.getGame();
        Material towerPlaceMaterial = game.getTowerPlaceMaterial();

        Block targetBlock = player.getTargetBlockExact(30);
        if (targetBlock == null || targetBlock.getType() != towerPlaceMaterial) {
            player.sendMessage(Messages.Gui.rightClickOnPlaceBlock(towerPlaceMaterial));
            return;
        }
        twPlayer.clickOnBlock(targetBlock.getLocation());
    }
}

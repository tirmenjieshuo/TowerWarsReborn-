package io.github.pako25.towerWars.Player.Listeners;

import io.github.pako25.towerWars.Player.TWPlayer;
import io.github.pako25.towerWars.Tower.Tower;
import io.github.pako25.towerWars.message.Messages;
import org.bukkit.ChatColor;
import org.bukkit.Material;
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

/**
 * 升级塔物品（经验瓶）右键监听器：对准塔身实体直接付费升级（距离升级）。
 * 物品识别 = 材质 + 显示名（显示名必须与 Messages.Gui.itemUpgradeTower 一致！）。
 */
public class UpgradeTowerListener implements Listener {

    /** 游戏物品显示名（必须与 Messages.Gui.itemUpgradeTower 保持一致，改动需同步！） */
    private static final String ITEM_NAME = "升级塔";

    @EventHandler
    public void onPlayerRightClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = player.getInventory().getItemInMainHand();

        if (item.getType() != Material.EXPERIENCE_BOTTLE) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return;
        if (!ChatColor.stripColor(meta.getDisplayName()).equals(ITEM_NAME)) return;

        TWPlayer twPlayer = TWPlayer.getTWPlayer(player.getUniqueId());
        if (twPlayer == null || !twPlayer.isInGame()) return;
        if (twPlayer.isAttacker()) {
            player.sendMessage(Messages.Game.attackerCannotPlaceTower());
            return;
        }
        event.setCancelled(true);

        // 实体射线命中塔身 → 距离升级（已满级的普通塔会打开升级菜单）
        RayTraceResult result = player.getWorld().rayTraceEntities(player.getEyeLocation(), player.getEyeLocation().getDirection(), 30, entity -> entity != player);
        if (result == null) return;
        Entity target = result.getHitEntity();

        if (target instanceof Mob mob) {
            for (Tower tower : twPlayer.getTrack().getTowers().values()) {
                if (tower.isEntityInTower(mob)) {
                    tower.upgradeFromDistance();
                }
            }
        }
    }
}

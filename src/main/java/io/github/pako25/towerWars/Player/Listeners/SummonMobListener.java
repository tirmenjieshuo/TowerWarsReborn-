package io.github.pako25.towerWars.Player.Listeners;

import io.github.pako25.towerWars.Player.TWPlayer;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * 召唤怪物品（下界之星）右键监听器：打开召唤怪菜单。
 * 物品识别 = 材质 + 显示名（显示名必须与 Messages.Gui.itemSummonMob 一致！）。
 */
public class SummonMobListener implements Listener {

    /** 游戏物品显示名（必须与 Messages.Gui.itemSummonMob 保持一致，改动需同步！） */
    private static final String ITEM_NAME = "召唤怪";

    @EventHandler
    public void onPlayerRightClick(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item.getType() != Material.NETHER_STAR) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return;
        if (!ChatColor.stripColor(meta.getDisplayName()).equals(ITEM_NAME)) return;

        TWPlayer twPlayer = TWPlayer.getTWPlayer(player.getUniqueId());
        if (twPlayer != null) {
            twPlayer.openSummonMobInventory();
        }
    }
}

package io.github.pako25.towerWars.Player.Listeners;

import io.github.pako25.towerWars.GameManagement.GameManager;
import io.github.pako25.towerWars.GameManagement.PlayerStats;
import io.github.pako25.towerWars.Player.Inventories.StatsInventory;
import io.github.pako25.towerWars.Player.TWPlayer;
import io.github.pako25.towerWars.message.Messages;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * 大厅物品监听器：右键"离开"时钟退出队列；右键"统计"告示牌打开统计面板。
 * 物品识别按材质（大厅物品是插件专属发放，不会与玩家自带物品混淆）。
 */
public class LobbyItemClickListener implements Listener {

    @EventHandler
    public void onPlayerRightClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        TWPlayer twPlayer = TWPlayer.getTWPlayer(player.getUniqueId());
        if (!twPlayer.isInLobby()) return;

        if (item.getType() == Material.CLOCK) {
            GameManager.getInstance().leaveQueue(twPlayer);
        }
        if (item.getType() == Material.DARK_OAK_SIGN) {
            if (PlayerStats.trackingEnabled) {
                new StatsInventory(twPlayer).open();
            } else {
                player.sendMessage(Messages.Lobby.statTrackingDisabled());
            }
        }
    }
}

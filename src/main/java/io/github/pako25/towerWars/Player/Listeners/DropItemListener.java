package io.github.pako25.towerWars.Player.Listeners;

import io.github.pako25.towerWars.Player.TWPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;

/**
 * 丢物品拦截：游戏/编辑器/大厅期间禁止玩家丢弃物品（防止误丢游戏物品）。
 */
public class DropItemListener implements Listener {

    @EventHandler
    public void onItemDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        TWPlayer twPlayer = TWPlayer.getTWPlayer(player.getUniqueId());
        if (twPlayer == null) return;
        if (twPlayer.isInGame() || twPlayer.isInEditor() || twPlayer.isInLobby()) {
            event.setCancelled(true);
        }
    }
}

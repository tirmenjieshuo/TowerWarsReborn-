package io.github.pako25.towerWars.Player.Listeners;

import io.github.pako25.towerWars.Player.TWPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;

/**
 * 拾取拦截：游戏/编辑器/大厅期间禁止玩家捡起地上的物品（保持背包干净）。
 * 高优先级 + 忽略已取消事件，避免与其他插件冲突。
 */
public class PickupListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        TWPlayer twPlayer = TWPlayer.getTWPlayer(player.getUniqueId());
        if (twPlayer == null) return; // 未注册（理论不会发生，防御性检查）
        if (twPlayer.isInEditor() || twPlayer.isInGame() || twPlayer.isInLobby()) event.setCancelled(true);
    }
}

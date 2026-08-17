package io.github.pako25.towerWars.Player.Listeners;

import io.github.pako25.towerWars.Player.TWPlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

/**
 * 背包点击锁定：编辑器/大厅/游戏期间禁止玩家拖拽背包物品。
 *
 * 设计意图：
 * 全部游戏菜单（放塔/召唤/升级/统计/编辑器选项）已迁移到 InvUI，
 * 菜单内点击由 InvUI 窗口自己处理并取消；本监听器只负责"锁定玩家背包"，
 * 防止玩家把物品拖进菜单或从背包拿出（InvUI 只拦截窗口内点击）。
 */
public class InventoryClickListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory inventory = event.getClickedInventory();
        if (inventory == null) return;

        TWPlayer twPlayer = TWPlayer.getTWPlayer(event.getWhoClicked().getUniqueId());
        if (twPlayer == null) return;

        if (twPlayer.isInEditor() || twPlayer.isInLobby() || twPlayer.isInGame()) {
            // 只锁定玩家背包区域；InvUI 窗口的点击交给 InvUI 自己处理
            // （若在此取消 InvUI 窗口点击，且 InvUI 忽略已取消事件，菜单按钮会失效）
            if (inventory.equals(event.getWhoClicked().getInventory())) {
                event.setCancelled(true);
            }
        }
    }
}

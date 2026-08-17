package io.github.pako25.towerWars.Player.Listeners;

import io.github.pako25.towerWars.Player.TWPlayer;
import io.github.pako25.towerWars.TowerWars;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 玩家进出服务器监听器：进服创建 TWPlayer 会话（含统计加载），
 * 离服时清理会话状态（游戏/队列/编辑器退出 + 统计缓存释放）。
 */
public class PlayerJoinLeaveListener implements Listener {

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        TWPlayer.newTWPlayer(event.getPlayer(), TowerWars.getPlugin());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        TWPlayer twPlayer = TWPlayer.getTWPlayer(event.getPlayer().getUniqueId());
        if (twPlayer != null) {
            twPlayer.leaveServer();
        }
        TWPlayer.removePlayer(event.getPlayer().getUniqueId());
    }
}

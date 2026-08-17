package io.github.pako25.towerWars.Player.Listeners;

import io.github.pako25.towerWars.Player.TWPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.FoodLevelChangeEvent;

/**
 * 饥饿锁定：游戏中玩家的饥饿/饱食恒满（塔防没有进食环节，防干扰）。
 */
public class HungerListener implements Listener {

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        if (!TWPlayer.getTWPlayer(player.getUniqueId()).isInGame()) return;

        event.setCancelled(true);
        player.setFoodLevel(20);
        player.setSaturation(20f);
    }
}

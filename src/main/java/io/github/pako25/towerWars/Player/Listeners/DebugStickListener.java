package io.github.pako25.towerWars.Player.Listeners;

import io.github.pako25.towerWars.Arena.Track;
import io.github.pako25.towerWars.Player.TWPlayer;
import io.github.pako25.towerWars.Tower.Tower;
import io.github.pako25.towerWars.message.Messages;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.Location;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 调试木棍监听器（towerwars.debug 权限）：右键"调试木棍"（普通木棍改名为
 * "调试木棍"）可以透视视线内实体/方块属于哪座塔，供开发者排查塔实体问题。
 *
 * 设计意图：
 * 原版在产线类里堆了大量实体状态 dump（Tower.toString 巨型串、TeslaTower.debugInfo），
 * 重构后这些 dump 已删除，调试信息收敛为简洁的塔摘要（toString 一行版）。
 */
public class DebugStickListener implements Listener {

    @EventHandler
    public void onPlayerRightClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() != Material.STICK) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return;
        // 通过改名识别调试木棍：与 /towerwars debug 发放的名字一致
        // （原版汉化后发放"调试木棍"但这里仍检测英文 "Debug stick"，导致木棍完全无效）
        String expectedName = PlainTextComponentSerializer.plainText().serialize(Messages.Cmd.debugStickName());
        if (!ChatColor.stripColor(meta.getDisplayName()).equals(expectedName)) return;

        TWPlayer twPlayer = TWPlayer.getTWPlayer(player.getUniqueId());
        if (twPlayer == null || !twPlayer.isInGame()) return;
        event.setCancelled(true);

        // 30 格射线：先找实体
        RayTraceResult result = player.getWorld().rayTraceEntities(player.getEyeLocation(), player.getEyeLocation().getDirection(), 30, entity -> !entity.equals(player));
        if (result == null) {
            player.sendMessage(Messages.Debug.noEntityHits());
            checkBlock(twPlayer);
            return;
        }
        Entity target = result.getHitEntity();
        boolean found = false;

        if (target instanceof Mob mob) {
            // 收集全服务器所有赛道上的塔，逐个比对实体归属
            Map<UUID, TWPlayer> allPlayers = TWPlayer.getTWPlayerMap();
            List<Tower> allTowers = new ArrayList<>();
            for (TWPlayer oneTwPlayer : allPlayers.values()) {
                if (oneTwPlayer.getTrack() != null) {
                    allTowers.addAll(oneTwPlayer.getTrack().getTowers().values());
                }
            }
            for (Tower tower : allTowers) {
                if (tower.isEntityInTower(mob)) {
                    found = true;
                    player.sendMessage(Messages.Debug.entityBelongsToTower(tower));
                }
            }
        }
        if (!found) {
            player.sendMessage(Messages.Debug.entityDoesNotBelongToTower());
        }
    }

    /** 射线没打到实体时，检查准星方块是否被塔占用 */
    private void checkBlock(TWPlayer twPlayer) {
        Block targetBlock = twPlayer.getPlayer().getTargetBlockExact(50);
        // 只检查本局竞技场配置的"可放塔方块"（原版硬编码 GRASS_BLOCK，配置改了材质就失效）
        if (targetBlock == null || targetBlock.getType() != twPlayer.getGame().getTowerPlaceMaterial()) return;
        Location targetLocation = targetBlock.getLocation();

        Map<UUID, TWPlayer> allPlayers = TWPlayer.getTWPlayerMap();
        List<Track> allTracks = new ArrayList<>();
        for (TWPlayer oneTwPlayer : allPlayers.values()) {
            if (oneTwPlayer.isInGame()) {
                allTracks.add(oneTwPlayer.getTrack());
            }
        }

        boolean found = false;
        for (Track track : allTracks) {
            if (track.isLocationInsideTrackBounds(targetLocation)) {
                if (track.isBlockOccupiedByTower(targetLocation)) {
                    found = true;
                    Tower tower = track.getTowers().get(targetLocation);
                    twPlayer.getPlayer().sendMessage(Messages.Debug.blockBelongsToTower(tower));
                }
            }
        }

        if (!found) {
            twPlayer.getPlayer().sendMessage(Messages.Debug.blockNotOccupied());
        }
    }
}

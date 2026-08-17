package io.github.pako25.towerWars.Editor;

import io.github.pako25.towerWars.Player.TWPlayer;
import io.github.pako25.towerWars.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * 编辑器世界内交互监听器：手持编辑工具（木棍/栅栏/铁轨/中继器/指南针/时钟）
 * 在竞技场世界里点击，配置出生点/边界/路径点，进出编辑器。
 *
 * 设计意图：
 * 左键=设置/切换，右键=删除/打开选项；工具物品与材质绑定（编辑器专属发放，
 * 不会与玩家自带物品混淆）。路径选择器（中继器）的 lore 实时显示各路径状态。
 */
public class EditorClickListener implements Listener {

    @EventHandler
    public void onPlayerLeftClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_AIR) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        TWPlayer twPlayer = TWPlayer.getTWPlayer(player.getUniqueId());
        if (!twPlayer.isInEditor()) return;
        ArenaEditor arenaEditor = twPlayer.getActiveArenaEditor();
        event.setCancelled(true);

        switch (item.getType()) {
            case STICK -> {
                // 设置轨道出生点（数量到上限后自动退出配置）
                if (event.getClickedBlock() == null) return;
                if (arenaEditor.getTrackSpawns().size() >= arenaEditor.getAvailableColors().size()) {
                    player.sendMessage(Messages.Editor.allTrackSpawnsSet());
                    arenaEditor.giveDefaultInventory();
                    arenaEditor.showEditorOptions();
                    return;
                }
                arenaEditor.addNewTrackSpawn(event.getClickedBlock().getLocation());
            }
            case OAK_FENCE -> {
                // 设置轨道边界（4 个角到上限后自动退出配置）
                if (event.getClickedBlock() == null) return;
                if (arenaEditor.getTrackBoundsLength() >= 4) {
                    player.sendMessage(Messages.Editor.allTrackBoundsSet());
                    arenaEditor.giveDefaultInventory();
                    arenaEditor.showEditorOptions();
                    return;
                }
                arenaEditor.addNewTrackBound(event.getClickedBlock().getLocation());
            }
            case REPEATER -> {
                // 切换正在编辑的路径
                arenaEditor.changeSelectedPathIndex();
                remakeRepeater(item, arenaEditor);
            }
            case RAIL -> {
                // 在当前选中的路径上加点（点在上方一格）
                if (event.getClickedBlock() == null) return;
                arenaEditor.addNewWaypoint(event.getClickedBlock().getLocation().clone().add(0, 1, 0));
                remakeRepeater(twPlayer.getPlayer().getInventory().getItem(1), arenaEditor);
            }
            case CLOCK -> {
                // 保存退出
                if (twPlayer.getPlayer().isConversing()) {
                    twPlayer.getPlayer().sendMessage(Messages.Editor.answerFirst());
                    return;
                }
                ArenaEditor.closeInstanceByPlayer(twPlayer, true);
            }
            default -> {
            }
        }
    }

    @EventHandler
    public void onPlayerRightClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        TWPlayer twPlayer = TWPlayer.getTWPlayer(player.getUniqueId());
        if (!twPlayer.isInEditor()) return;
        ArenaEditor arenaEditor = twPlayer.getActiveArenaEditor();
        event.setCancelled(true);

        switch (item.getType()) {
            case COMPASS -> arenaEditor.showEditorOptions();
            case STICK -> {
                // 右键删除出生点
                if (event.getClickedBlock() == null) return;
                Location removedTrackSpawn = arenaEditor.removeTrackSpawn(event.getClickedBlock().getLocation());
                if (removedTrackSpawn != null) {
                    twPlayer.getPlayer().sendMessage(Messages.Editor.removedTrackSpawn(removedTrackSpawn.toVector()));
                }
            }
            case WRITTEN_BOOK -> event.setCancelled(false); // 放行阅读说明成书
            case OAK_FENCE -> {
                // 右键删除边界
                if (event.getClickedBlock() == null) return;
                Vector removedTrackBound = arenaEditor.removeTrackBound(event.getClickedBlock().getLocation());
                if (removedTrackBound != null) {
                    twPlayer.getPlayer().sendMessage(Messages.Editor.removedTrackBound(removedTrackBound));
                }
            }
            case REPEATER -> {
                // 右键删除当前选中的路径
                arenaEditor.removeSelectedPath();
                remakeRepeater(item, arenaEditor);
            }
            case RAIL -> {
                // 右键删除路径点
                if (event.getClickedBlock() == null) return;
                arenaEditor.removeWaypoint(event.getClickedBlock().getLocation().clone().add(0, 1, 0));
                remakeRepeater(twPlayer.getPlayer().getInventory().getItem(1), arenaEditor);
            }
            case CLOCK -> {
                // 潜行右键 = 放弃修改退出
                if (twPlayer.getPlayer().isConversing()) {
                    twPlayer.getPlayer().sendMessage(Messages.Editor.answerFirst());
                    return;
                }
                if (!player.isSneaking()) {
                    player.sendMessage(Messages.Editor.discardHint());
                    return;
                }
                ArenaEditor.closeInstanceByPlayer(twPlayer, false);
            }
            default -> {
            }
        }
    }

    /** 重建路径选择器（中继器）的 lore：路径列表 + 当前选中项高亮 */
    private void remakeRepeater(ItemStack repeater, ArenaEditor arenaEditor) {
        if (repeater == null) return;
        ItemMeta repeaterMeta = repeater.getItemMeta();
        List<Component> lore = new ArrayList<>(List.of(
                Messages.Editor.loreLeftClickToChangePath(),
                Messages.Editor.loreRightClickToRemoveSelectedPath()
        ));
        int pathCount = 0;
        for (List<Vector> path : arenaEditor.getPaths()) {
            NamedTextColor pathColor = pathCount == arenaEditor.getSelectedPathIndex() ? NamedTextColor.YELLOW : NamedTextColor.GRAY;
            lore.add(Component.text("路径 " + pathCount + ": " + path.size() + " 个路径点", pathColor));
            pathCount++;
        }
        repeaterMeta.lore(lore);
        repeater.setItemMeta(repeaterMeta);
    }
}

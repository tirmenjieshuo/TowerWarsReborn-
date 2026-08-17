package io.github.pako25.towerWars.Player.Inventories;

import io.github.pako25.towerWars.GameManagement.PlayerStats;
import io.github.pako25.towerWars.Player.TWPlayer;
import io.github.pako25.towerWars.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import xyz.xenondevs.inventoryaccess.component.AdventureComponentWrapper;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.gui.SlotElement;
import xyz.xenondevs.invui.item.impl.SimpleItem;
import xyz.xenondevs.invui.window.Window;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 玩家统计面板（InvUI 版，45 格）：6 项战绩各占一格，lore = 我的数值 + 全服 TOP-10。
 *
 * 设计意图：
 * 排行榜数据来自 PlayerStats 的懒刷新缓存；物品不响应点击（纯展示面板，
 * 点击被 InvUI 窗口拦截，无副作用）。
 */
public class StatsInventory {

    private final TWPlayer twPlayer;
    private final UUID uuid;
    private Gui gui;
    private Window window;

    public StatsInventory(TWPlayer twPlayer) {
        this.twPlayer = twPlayer;
        this.uuid = twPlayer.getPlayer().getUniqueId();
        rebuild();
    }

    public void rebuild() {
        Gui newGui = Gui.empty(9, 5);
        PlayerStats stats = PlayerStats.getStats(uuid);

        newGui.setSlotElement(11, slot(buildStatItem(Material.GREEN_WOOL, Messages.Gui.statsGamesWon(), stats.getGames_won(), PlayerStats.getGames_wonLeaderBoard())));
        newGui.setSlotElement(13, slot(buildStatItem(Material.RED_WOOL, Messages.Gui.statsGamesLost(), stats.getGames_lost(), PlayerStats.getGames_lostLeaderBoard())));
        newGui.setSlotElement(15, slot(buildStatItem(Material.IRON_SWORD, Messages.Gui.statsMobKills(), stats.getMob_kills(), PlayerStats.getMob_killsLeaderBoard())));
        newGui.setSlotElement(29, slot(buildStatItem(Material.OAK_FENCE, Messages.Gui.statsTowersPlaced(), stats.getTowers_placed(), PlayerStats.getTowers_placedLeaderBoard())));
        newGui.setSlotElement(31, slot(buildStatItem(Material.GOLD_INGOT, Messages.Gui.statsGoldSpent(), stats.getGold_spent(), PlayerStats.getGold_spentLeaderBoard())));
        newGui.setSlotElement(33, slot(buildStatItem(Material.ZOMBIE_HEAD, Messages.Gui.statsMobsSent(), stats.getMobs_sent(), PlayerStats.getMobs_sentLeaderBoard())));

        this.gui = newGui;
    }

    private SlotElement slot(ItemStack item) {
        return new SlotElement.ItemSlotElement(new SimpleItem(item));
    }

    /** 单个统计物品：我的数值 + 排行榜前 10（"排名. 玩家名 (数值)"） */
    private ItemStack buildStatItem(Material material, Component name, int yourValue, List<Pair<String, Integer>> leaderboard) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        List<Component> lore = new ArrayList<>(List.of(
                Messages.Gui.statsYourValue(yourValue),
                Component.empty()
        ));
        for (int i = 0; i < leaderboard.size(); i++) {
            Pair<String, Integer> record = leaderboard.get(i);
            lore.add(Component.text((i + 1) + ". " + record.getLeft() + " (" + record.getRight() + ")", NamedTextColor.GRAY));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /** 打开统计面板 */
    public void open() {
        window = Window.single(builder -> builder
                .setGui(gui)
                .setTitle(new AdventureComponentWrapper(Messages.Gui.statsInventoryTitle()))
                .setViewer(twPlayer.getPlayer()));
        window.open();
    }
}

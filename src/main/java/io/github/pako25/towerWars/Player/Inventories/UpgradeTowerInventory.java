package io.github.pako25.towerWars.Player.Inventories;

import io.github.pako25.towerWars.Player.TWPlayer;
import io.github.pako25.towerWars.Tower.Tower;
import io.github.pako25.towerWars.Tower.TowerType;
import io.github.pako25.towerWars.config.TowerConfig;
import io.github.pako25.towerWars.message.Messages;
import io.github.pako25.towerWars.util.GameRules;
import io.github.pako25.towerWars.util.LoreUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import xyz.xenondevs.inventoryaccess.component.AdventureComponentWrapper;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.gui.SlotElement;
import xyz.xenondevs.invui.item.impl.SimpleItem;
import xyz.xenondevs.invui.window.Window;

import java.util.ArrayList;
import java.util.List;

/**
 * 塔升级菜单（InvUI 版，45 格）：显示射程 / 统计 / 升级 / 出售 / 替换 /
 * 专精二选一（满级普通塔）。
 *
 * 设计意图：
 * InvUI 迁移后按钮点击改为闭包处理，不再依赖 PDC 动作键；
 * 升级/专精成功后重建菜单展示新数值，出售/替换后关闭菜单。
 */
public class UpgradeTowerInventory {

    private final TWPlayer twPlayer;
    private final Tower tower;
    private final Location location;
    private Gui gui;
    private Window window;

    public UpgradeTowerInventory(JavaPlugin plugin, TWPlayer twPlayer, Tower tower, Location location) {
        this.twPlayer = twPlayer;
        this.tower = tower;
        this.location = location;
        rebuild();
    }

    /** 重建菜单：升级/专精成功后刷新数值展示 */
    public void rebuild() {
        Gui newGui = Gui.empty(9, 5);
        TowerType type = tower.getTowerType();
        int maxLevel = TowerConfig.maxLevel(type);
        int level = tower.getLevel();
        int prestige = tower.getPrestige();

        newGui.setSlotElement(9, new SlotElement.ItemSlotElement(new SimpleItem(buildShowRangeItem(), click -> tower.showRange())));
        newGui.setSlotElement(10, new SlotElement.ItemSlotElement(new SimpleItem(buildStatsItem())));
        newGui.setSlotElement(13, new SlotElement.ItemSlotElement(new SimpleItem(buildUpgradeItem(type, maxLevel, level, prestige), click -> tryUpgrade())));
        newGui.setSlotElement(16, new SlotElement.ItemSlotElement(new SimpleItem(buildSellItem(type, level, prestige), click -> {
            tower.sell();
            if (window != null) window.close();
        })));
        newGui.setSlotElement(17, new SlotElement.ItemSlotElement(new SimpleItem(buildReplaceItem(), click -> {
            tower.sell();
            if (window != null) window.close();
            twPlayer.openPlaceTowerInventory(location);
        })));

        // 已满级的普通塔：展示专精二选一
        if (level >= maxLevel && !type.isSupport() && prestige == 0) {
            newGui.setSlotElement(21, new SlotElement.ItemSlotElement(new SimpleItem(buildPrestigeItem(type, 1), click -> tryPrestige(1))));
            newGui.setSlotElement(23, new SlotElement.ItemSlotElement(new SimpleItem(buildPrestigeItem(type, 2), click -> tryPrestige(2))));
        }

        this.gui = newGui;
    }

    /** 显示射程按钮：粒子画一圈射程范围 */
    private ItemStack buildShowRangeItem() {
        ItemStack item = new ItemStack(Material.REDSTONE, 1);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Messages.Gui.showRange());
        meta.lore(List.of(Messages.Gui.showRangeLore()));
        item.setItemMeta(meta);
        return item;
    }

    /** 统计按钮：发射/伤害/击杀三行 */
    private ItemStack buildStatsItem() {
        ItemStack item = new ItemStack(Material.PAPER, 1);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Messages.Gui.statsButton());
        meta.lore(List.of(
                Component.text(" "),
                LoreUtil.statLine(Messages.Gui.loreShots(), tower.getShots()),
                LoreUtil.statLine(Messages.Gui.loreDamageDealt(), tower.getDamageDealt()),
                LoreUtil.statLine(Messages.Gui.loreKills(), tower.getKills())
        ));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * 升级按钮：可升级时展示"旧值 >>> 新值"对比（数据来自 TowerConfig），
     * 已满级时展示当前数值与"已达最高等级"。
     */
    private ItemStack buildUpgradeItem(TowerType type, int maxLevel, int level, int prestige) {
        ItemStack item = new ItemStack(Material.EXPERIENCE_BOTTLE, 1);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Messages.Gui.towerDisplayName(TowerConfig.displayName(type), level));
        item.setItemMeta(meta);

        if (level < maxLevel) {
            TowerConfig.LevelStats nextStats = TowerConfig.levelStats(type, level + 1);
            int upgradeCost = TowerConfig.buyCost(type, level + 1, prestige) - TowerConfig.buyCost(type, level, prestige);
            List<Component> lore = new ArrayList<>(List.of(
                    Messages.Gui.loreClickToUpgrade(),
                    Component.text(" "),
                    Messages.Gui.upgradeFor(upgradeCost),
                    Component.text(" "),
                    LoreUtil.statChange(Messages.Gui.loreDamage(), tower.getDamage(), nextStats.damage()),
                    LoreUtil.statChange(Messages.Gui.loreReload(), tower.getReload(), nextStats.reload()),
                    LoreUtil.statChange(Messages.Gui.loreDps(), tower.getDamage() / tower.getReload(), nextStats.damage() / nextStats.reload()),
                    LoreUtil.statChange(Messages.Gui.loreRange(), tower.getRange(), nextStats.range()),
                    LoreUtil.statChange(Messages.Gui.loreSplash(), tower.getSplash(), nextStats.splash())
            ));
            appendSpecial(lore, nextStats.specialLines());
            meta.lore(lore);
        } else {
            // 满级展示：专精形态（level=4）读专精数值，普通满级读等级数值。
            // 原版对专精塔读 levels.4.xxx 会静默得到 0（显示错误），此处修复。
            List<String> specialLines;
            if (prestige != 0) {
                TowerConfig.PrestigeStats prestigeStats = TowerConfig.prestigeStats(type, prestige);
                specialLines = prestigeStats.specialLines();
            } else {
                specialLines = TowerConfig.levelStats(type, level).specialLines();
            }
            List<Component> lore = new ArrayList<>(List.of(
                    Component.text(""),
                    Messages.Gui.maxLevel(),
                    Component.text(" "),
                    LoreUtil.statLine(Messages.Gui.loreDamage(), tower.getDamage()),
                    LoreUtil.statLine(Messages.Gui.loreReload(), tower.getReload()),
                    LoreUtil.statLine(Messages.Gui.loreDps(), tower.getDamage() / tower.getReload()),
                    LoreUtil.statLine(Messages.Gui.loreRange(), tower.getRange()),
                    LoreUtil.statLine(Messages.Gui.loreSplash(), tower.getSplash())
            ));
            appendSpecial(lore, specialLines);
            meta.lore(lore);
        }
        item.setItemMeta(meta);
        return item;
    }

    /** 出售按钮：显示半价回收金额 */
    private ItemStack buildSellItem(TowerType type, int level, int prestige) {
        ItemStack item = new ItemStack(Material.EMERALD, 1);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Messages.Gui.sellButton());
        int sellCost = (int) (TowerConfig.buyCost(type, level, prestige) * GameRules.SELL_REFUND_RATIO);
        meta.lore(List.of(Messages.Gui.loreClickToSell(), Messages.Gui.sellFor(sellCost)));
        item.setItemMeta(meta);
        return item;
    }

    /** 替换按钮：卖掉旧塔换新塔（回到放置菜单） */
    private ItemStack buildReplaceItem() {
        ItemStack item = new ItemStack(Material.CRAFTING_TABLE, 1);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Messages.Gui.replaceButton());
        meta.lore(List.of(Messages.Gui.replaceLore()));
        item.setItemMeta(meta);
        return item;
    }

    /** 专精按钮：只展示一次属性对比（与升级按钮共用同一 lore 模板） */
    private ItemStack buildPrestigeItem(TowerType type, int prestigeSlot) {
        TowerConfig.PrestigeStats prestigeStats = TowerConfig.prestigeStats(type, prestigeSlot);
        ItemStack item = new ItemStack(prestigeStats.shopMaterial(), 1);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Messages.Gui.prestigeDisplayName(prestigeStats.displayName()));

        int upgradeCost = TowerConfig.buyCost(type, 4, prestigeSlot) - TowerConfig.buyCost(type, 3, 0);
        List<Component> lore = new ArrayList<>(List.of(
                Messages.Gui.loreClickToPrestige(),
                Component.text(" "),
                Messages.Gui.onePrestigePerTower(),
                Component.text(" "),
                Messages.Gui.upgradeFor(upgradeCost),
                Component.text(" "),
                LoreUtil.statChange(Messages.Gui.loreDamage(), tower.getDamage(), prestigeStats.damage()),
                LoreUtil.statChange(Messages.Gui.loreReload(), tower.getReload(), prestigeStats.reload()),
                LoreUtil.statChange(Messages.Gui.loreDps(), tower.getDamage() / tower.getReload(), prestigeStats.damage() / prestigeStats.reload()),
                LoreUtil.statChange(Messages.Gui.loreRange(), tower.getRange(), prestigeStats.range()),
                LoreUtil.statChange(Messages.Gui.loreSplash(), tower.getSplash(), prestigeStats.splash())
        ));
        appendSpecial(lore, prestigeStats.specialLines());
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /** 把"特殊:"标题与多行描述追加进 lore（无描述时不追加） */
    private void appendSpecial(List<Component> lore, List<String> specialLines) {
        if (specialLines.isEmpty()) return;
        lore.add(Component.text(" "));
        lore.add(LoreUtil.specialTitle());
        lore.addAll(LoreUtil.specialLines(specialLines));
    }

    /** 尝试升级：付差价成功后重建菜单刷新数值 */
    private void tryUpgrade() {
        TowerType type = tower.getTowerType();
        int maxLevel = TowerConfig.maxLevel(type);
        if (tower.getLevel() >= maxLevel) return;
        int cost = TowerConfig.buyCost(type, tower.getLevel() + 1, 0) - TowerConfig.buyCost(type, tower.getLevel(), 0);
        boolean success = twPlayer.buyForCoin(cost);
        if (success) {
            tower.upgrade();
            reopen();
        } else {
            notEnoughCoins();
        }
    }

    /** 尝试进阶：付差价成功后重建菜单 */
    private void tryPrestige(int prestigeSlot) {
        TowerType type = tower.getTowerType();
        int cost = TowerConfig.buyCost(type, 4, prestigeSlot) - TowerConfig.buyCost(type, 3, 0);
        boolean success = twPlayer.buyForCoin(cost);
        if (success) {
            tower.prestige(prestigeSlot);
            reopen();
        } else {
            notEnoughCoins();
        }
    }

    /** 升级/专精成功后：关旧窗口、重建、重开（展示新等级/专精数值） */
    private void reopen() {
        if (window != null && window.isOpen()) {
            window.close();
        }
        rebuild();
        open();
    }

    private void notEnoughCoins() {
        if (window != null) window.close();
        twPlayer.getPlayer().sendMessage(Messages.Tower.notEnoughGold());
    }

    /** 打开升级菜单 */
    public void open() {
        window = Window.single(builder -> builder
                .setGui(gui)
                .setTitle(new AdventureComponentWrapper(Messages.Gui.upgradeInventoryTitle()))
                .setViewer(twPlayer.getPlayer()));
        window.open();
    }
}

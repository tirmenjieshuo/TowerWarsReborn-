package io.github.pako25.towerWars.Player.Inventories;

import io.github.pako25.towerWars.Player.TWPlayer;
import io.github.pako25.towerWars.Tower.TowerType;
import io.github.pako25.towerWars.config.TowerConfig;
import io.github.pako25.towerWars.message.Messages;
import io.github.pako25.towerWars.util.LoreUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
 * 放置塔菜单（InvUI 版，54 格）：按列展示 8 种塔，行 = 等级/专精。
 * 布局约定：普通塔占 5 行（Lv.1 / Lv.2 / Lv.3 / 专精1 / 专精2），
 * 支援塔占 2 行（Lv.1 / Lv.2）；未解锁（价格 ≥ 收入×5）的等级显示为屏障。
 *
 * 设计意图：
 * InvUI 迁移后点击处理改为闭包捕获（塔类型/等级/专精在构建时固定），
 * 不再依赖 PDC 键；每次打开/收入变化后 rebuild() 重建 GUI（解锁状态刷新）。
 */
public class PlaceTowerInventory {

    private final TWPlayer twPlayer;
    private Gui gui;
    private Window window;

    public PlaceTowerInventory(JavaPlugin plugin, TWPlayer twPlayer) {
        this.twPlayer = twPlayer;
        rebuild();
    }

    /** 重建 GUI：随玩家收入变化，可解锁的新等级会替换掉屏障 */
    public void rebuild() {
        Gui newGui = Gui.empty(9, 6);
        int i = 0;
        for (var entry : TowerConfig.allTowers()) {
            TowerType type = entry.getKey();
            TowerConfig.TowerDefinition definition = entry.getValue();
            int income = twPlayer.getIncome();
            if (definition.category() == TowerType.Category.NORMAL) {
                newGui.setSlotElement(i, buildLevelSlot(type, definition, 1, income));
                newGui.setSlotElement(i + 9, buildLevelSlot(type, definition, 2, income));
                newGui.setSlotElement(i + 18, buildLevelSlot(type, definition, 3, income));
                newGui.setSlotElement(i + 36, buildPrestigeSlot(type, definition, 1, income));
                newGui.setSlotElement(i + 45, buildPrestigeSlot(type, definition, 2, income));
            } else {
                // 支援塔只有 2 个等级
                newGui.setSlotElement(i, buildLevelSlot(type, definition, 1, income));
                newGui.setSlotElement(i + 9, buildLevelSlot(type, definition, 2, income));
            }
            i++;
        }
        this.gui = newGui;
    }

    /** 等级解锁判定：该等级价格必须低于 收入×5（原版阈值，不变） */
    private boolean isLevelUnlocked(TowerConfig.TowerDefinition definition, int level, int income) {
        return definition.levels().get(level).cost() < income * 5;
    }

    /** 专精解锁判定：满级价格（专精价）低于 收入×5 */
    private boolean isPrestigeUnlocked(TowerConfig.TowerDefinition definition, int prestige, int income) {
        return definition.prestiges().get(prestige).cost() < income * 5;
    }

    /** 等级槽：未解锁 = 屏障（不可点击）；解锁 = 点击直接放置 */
    private SlotElement buildLevelSlot(TowerType type, TowerConfig.TowerDefinition definition, int level, int income) {
        Material material = definition.shopMaterial();
        boolean unlocked = isLevelUnlocked(definition, level, income);
        ItemStack item = buildLevelItem(type, definition, level, material, unlocked);
        if (!unlocked) return new SlotElement.ItemSlotElement(new SimpleItem(item));
        return new SlotElement.ItemSlotElement(new SimpleItem(item, click -> placeTower(type, level, 0, Messages.Gui.towerDisplayName(definition.displayName(), level))));
    }

    /** 专精槽：未解锁 = 屏障；解锁 = 点击放置专精形态 */
    private SlotElement buildPrestigeSlot(TowerType type, TowerConfig.TowerDefinition definition, int prestige, int income) {
        boolean unlocked = isPrestigeUnlocked(definition, prestige, income);
        ItemStack item = buildPrestigeItem(type, definition, prestige, unlocked);
        if (!unlocked) return new SlotElement.ItemSlotElement(new SimpleItem(item));
        return new SlotElement.ItemSlotElement(new SimpleItem(item, click -> placeTower(type, 4, prestige, Messages.Gui.prestigeDisplayName(definition.prestiges().get(prestige).displayName()))));
    }

    /** 放置塔：成功扣款后关闭菜单（原 itemClick 逻辑） */
    private void placeTower(TowerType type, int level, int prestige, Component towerName) {
        boolean success = twPlayer.placeTower(type, level, prestige, towerName);
        if (success && window != null) {
            window.close();
        }
    }

    /** 等级物品：名字/lore 来自配置（与重构前一致） */
    private ItemStack buildLevelItem(TowerType type, TowerConfig.TowerDefinition definition, int level, Material levelsMaterial, boolean unlocked) {
        TowerConfig.LevelStats stats = definition.levels().get(level);
        ItemStack item = new ItemStack(unlocked ? levelsMaterial : Material.BARRIER, 1);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Messages.Gui.towerDisplayName(definition.displayName(), level));
        List<Component> lore = new ArrayList<>(List.of(
                Messages.Gui.loreClickToPlace(),
                Component.text(" "),
                LoreUtil.statLine(Messages.Gui.loreDamage(), stats.damage()),
                LoreUtil.statLine(Messages.Gui.loreReload(), stats.reload()),
                LoreUtil.statLine(Messages.Gui.loreRange(), stats.range()),
                LoreUtil.statLine(Messages.Gui.loreSplash(), stats.splash()),
                Component.text(" "),
                Messages.Gui.loreCost().append(Component.text(stats.cost(), NamedTextColor.GOLD))
        ));
        if (!stats.specialLines().isEmpty()) {
            lore.add(Component.text(" "));
            lore.add(LoreUtil.specialTitle());
            lore.addAll(LoreUtil.specialLines(stats.specialLines()));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /** 专精物品：名字与 lore 来自配置的专精段 */
    private ItemStack buildPrestigeItem(TowerType type, TowerConfig.TowerDefinition definition, int prestige, boolean unlocked) {
        TowerConfig.PrestigeStats stats = definition.prestiges().get(prestige);
        ItemStack item = new ItemStack(unlocked ? stats.shopMaterial() : Material.BARRIER, 1);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Messages.Gui.prestigeDisplayName(stats.displayName()));
        List<Component> lore = new ArrayList<>(List.of(
                Messages.Gui.loreClickToPlace(),
                Component.text(" "),
                LoreUtil.statLine(Messages.Gui.loreDamage(), stats.damage()),
                LoreUtil.statLine(Messages.Gui.loreReload(), stats.reload()),
                LoreUtil.statLine(Messages.Gui.loreRange(), stats.range()),
                LoreUtil.statLine(Messages.Gui.loreSplash(), stats.splash()),
                Component.text(" "),
                Messages.Gui.loreCost().append(Component.text(stats.cost(), NamedTextColor.GOLD)),
                Component.text(" "),
                LoreUtil.specialTitle()
        ));
        lore.addAll(LoreUtil.specialLines(stats.specialLines()));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /** 打开放置塔菜单 */
    public void open() {
        window = Window.single(builder -> builder
                .setGui(gui)
                .setTitle(new AdventureComponentWrapper(Messages.Gui.placeTowerInventoryTitle()))
                .setViewer(twPlayer.getPlayer()));
        window.open();
    }

    /** 刷新：关闭旧窗口、重建 GUI、重新打开（收入变化后调用） */
    public void reopen() {
        if (window != null && window.isOpen()) {
            window.close();
        }
        rebuild();
        open();
    }
}

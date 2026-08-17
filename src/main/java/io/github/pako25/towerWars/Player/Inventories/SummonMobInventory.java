package io.github.pako25.towerWars.Player.Inventories;

import io.github.pako25.towerWars.Arena.MobType;
import io.github.pako25.towerWars.Arena.MobData.MobState;
import io.github.pako25.towerWars.Arena.MobData.MobStates;
import io.github.pako25.towerWars.Player.TWPlayer;
import io.github.pako25.towerWars.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
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
 * 召唤怪菜单（InvUI 版，45 格）：基础怪按序排列，收入达进化门槛时
 * 格子直接变成进化形态；点击送怪，库存变化后重建菜单刷新数字。
 *
 * 设计意图：
 * InvUI 迁移后点击处理改为闭包捕获 MobType，不再依赖 PDC 键；
 * 送怪成功刷新库存显示（reopen），金币不足保持菜单打开并提示。
 */
public class SummonMobInventory {

    private final TWPlayer twPlayer;
    private Gui gui;
    private Window window;

    public SummonMobInventory(JavaPlugin plugin, TWPlayer twPlayer) {
        this.twPlayer = twPlayer;
        rebuild();
    }

    /** （重新）装载菜单：库存/收入变化后刷新（twPlayer.increaseStock 会触发） */
    public synchronized void rebuild() {
        Gui newGui = Gui.empty(9, 5);
        MobStates mobStates = twPlayer.getMobStates();
        int i = 0;

        for (MobType key : MobType.values()) {
            MobState mobState = mobStates.getMobState(key);
            if (!mobState.isSummonable()) continue;

            boolean disabled = false;
            if (mobState.isAdvanced()) continue; // 基础怪才占据格子（进化形态替换基础形态显示）
            if (mobState.getIncomeToUnlock() > twPlayer.getIncome()) disabled = true;

            // 收入达到进化门槛：格子直接变成进化形态
            if (mobState.getIncomeToPrestige() < twPlayer.getIncome()) {
                mobState = mobStates.getMobState(mobState.getAdvancedForm());
            }

            if (twPlayer.getStock() < 1) disabled = true;

            Material material = mobState.getMaterial();
            if (disabled) material = Material.BARRIER;

            ItemStack item = new ItemStack(material, disabled ? 1 : Math.max(twPlayer.getStock(), 1));
            ItemMeta meta = item.getItemMeta();

            if (meta != null) {
                // 移除装备类属性修饰，防止装备图标影响面板显示
                meta.removeAttributeModifier(Attribute.ARMOR);
                meta.removeAttributeModifier(Attribute.ARMOR_TOUGHNESS);

                meta.displayName(Messages.Gui.mobDisplayName(mobState.getName(), mobState.getIncomeEvolutionText(twPlayer.getIncome())));

                List<Component> lore = new ArrayList<>(List.of(
                        Messages.Gui.loreClickToSummon(),
                        Component.text(" "),
                        Messages.Gui.loreCost().append(Component.text(mobState.getCost(twPlayer.getIncome()), NamedTextColor.YELLOW)),
                        Messages.Gui.loreHealth().append(Component.text(mobState.getHealth(), NamedTextColor.YELLOW)),
                        Messages.Gui.loreSpeed().append(Component.text(mobState.getSpeed(), NamedTextColor.YELLOW)),
                        Messages.Gui.loreIncome().append(Component.text(mobState.getIncome(twPlayer.getIncome()), NamedTextColor.YELLOW))
                ));

                if (mobState.isAdvanced()) {
                    int incomeForNextEvolutionByIncome = mobState.getIncomeForNextEvolutionByIncome(twPlayer.getIncome());
                    lore.add(Component.text(" "));
                    if (incomeForNextEvolutionByIncome == 0) {
                        lore.add(Messages.Gui.maxEvolutionReached());
                    } else {
                        lore.add(Messages.Gui.evolveAtIncome(incomeForNextEvolutionByIncome));
                    }
                    lore.add(Component.text(" "));
                    lore.add(Messages.Gui.loreStock().append(Component.text(twPlayer.getStock(), NamedTextColor.GOLD)));
                    lore.add(Messages.Gui.loreSummoned().append(Component.text(mobState.getSummonCount(), NamedTextColor.GOLD)));
                    lore.add(Component.text(" "));
                    lore.add(Messages.Gui.loreSummonedBonus());
                    // 召唤加成：结构化行列表直接逐行展示（原版 "//" 拼接已消除）
                    for (String line : mobState.getSummonedBonus()) {
                        lore.add(Component.text("  " + line, NamedTextColor.GOLD));
                    }
                }

                meta.lore(lore);
                item.setItemMeta(meta);
            }

            // 槽位：禁用 = 屏障不可点击；可用 = 点击送怪
            SlotElement element;
            if (!disabled) {
                MobType summonType = mobState.getMobType();
                element = new SlotElement.ItemSlotElement(new SimpleItem(item, click -> {
                    MobState state = twPlayer.getMobStates().getMobState(summonType);
                    boolean success = twPlayer.summonMob(summonType, state.getCost(twPlayer.getIncome()), state.getIncome(twPlayer.getIncome()));
                    if (success) {
                        reopen(); // 库存变化：重建菜单刷新数字
                    } else {
                        twPlayer.getPlayer().sendMessage(Messages.Tower.notEnoughGold());
                    }
                }));
            } else {
                element = new SlotElement.ItemSlotElement(new SimpleItem(item));
            }
            newGui.setSlotElement(i, element);
            i++;
        }
        this.gui = newGui;
    }

    /** 打开召唤怪菜单 */
    public void open() {
        window = Window.single(builder -> builder
                .setGui(gui)
                .setTitle(new AdventureComponentWrapper(Messages.Gui.summonMobInventoryTitle()))
                .setViewer(twPlayer.getPlayer()));
        window.open();
    }

    /** 刷新：关闭旧窗口、重建 GUI、重新打开 */
    public void reopen() {
        if (window != null && window.isOpen()) {
            window.close();
        }
        rebuild();
        open();
    }

    /** 仅当菜单打开时刷新（库存恢复等后台事件调用，避免无谓重建） */
    public void refreshIfOpen() {
        if (window != null && window.isOpen()) {
            reopen();
        }
    }
}

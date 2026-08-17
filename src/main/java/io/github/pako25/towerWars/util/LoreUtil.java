package io.github.pako25.towerWars.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.ArrayList;
import java.util.List;

/**
 * 物品 lore 构建工具。
 *
 * 设计意图：
 * 原版在升级菜单里把"属性对比 lore""特殊效果 lore"两大段代码复制了三遍
 * （普通升级 / 专精 1 / 专精 2），改一处必漏两处。这里把两个最常用的
 * lore 段落收敛为参数化方法，GUI 只负责拼装。
 * 颜色规范与 Messages 一致：标签 AQUA 青色、旧值 YELLOW、新值 GREEN。
 */
public final class LoreUtil {

    private LoreUtil() {
        // 工具类，禁止实例化
    }

    /** 单个属性的"旧值 >>> 新值"对比行，如 "伤害: 15 >>> 50"（label 直接传 Messages 的标签组件） */
    public static Component statChange(Component label, Object oldValue, Object newValue) {
        return label.append(Component.text(String.valueOf(oldValue), NamedTextColor.YELLOW))
                .append(Component.text(" >>> ", NamedTextColor.GRAY))
                .append(Component.text(String.valueOf(newValue), NamedTextColor.GREEN));
    }

    /** 只展示当前值的属性行，如 "伤害: 15"（满级/专精展示用） */
    public static Component statLine(Component label, Object value) {
        return label.append(Component.text(String.valueOf(value), NamedTextColor.YELLOW));
    }

    /** "特殊:" 标题（浅紫） */
    public static Component specialTitle() {
        return Component.text("特殊: ", NamedTextColor.LIGHT_PURPLE);
    }

    /** 特殊效果的多行描述（青色逐行），配置里的 "//" 已在加载期切成列表 */
    public static List<Component> specialLines(List<String> lines) {
        List<Component> components = new ArrayList<>();
        for (String line : lines) {
            components.add(Component.text(line, NamedTextColor.AQUA));
        }
        return components;
    }
}

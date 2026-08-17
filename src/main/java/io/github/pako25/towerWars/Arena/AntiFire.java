package io.github.pako25.towerWars.Arena;

import org.bukkit.entity.Mob;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 防燃烧监听器：塔身实体注册进来后免疫一切点燃（EntityCombustEvent 取消）。
 *
 * 设计意图：
 * 塔身是用怪物实体拼的造型，如果被火/岩浆点着会烧毁并触发杂音；
 * 全插件所有"塔身/导航体"在生成时都调 AntiFire.add 注册，出售时 remove。
 * 单例 + Listener 一体（TowerWars.registerEvents 里直接注册）。
 */
public class AntiFire implements Listener {

    private final Set<UUID> protectedMobs = new HashSet<>();

    private AntiFire() {
    }

    private static AntiFire listener;

    public static AntiFire getListener() {
        if (listener == null) {
            listener = new AntiFire();
        }
        return listener;
    }

    /** 注册保护：该实体之后不会燃烧 */
    public void add(Mob mob) {
        protectedMobs.add(mob.getUniqueId());
    }

    /** 移除保护（实体出售/移除时调用，防 UUID 泄漏） */
    public void remove(Mob mob) {
        protectedMobs.remove(mob.getUniqueId());
    }

    @EventHandler
    public void onCombust(EntityCombustEvent event) {
        if (protectedMobs.contains(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
        }
    }
}

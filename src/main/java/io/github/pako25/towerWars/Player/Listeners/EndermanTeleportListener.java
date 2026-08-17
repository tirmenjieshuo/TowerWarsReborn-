package io.github.pako25.towerWars.Player.Listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTeleportEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 末影人传送拦截：注册进来的末影人禁止自然随机传送。
 *
 * 设计意图：
 * 末影人塔的塔身是末影人，其"随机瞬移"特性会让塔身乱跑——只有
 * 塔的攻击效果（把怪传回出生点）才是玩法想要的传送，因此黑名单拦截
 * 一切自然传送事件（单例 + Listener 一体）。
 */
public class EndermanTeleportListener implements Listener {

    private static final Set<UUID> uuidSet = new HashSet<>();
    private static EndermanTeleportListener endermanTeleportListener;

    private EndermanTeleportListener() {
    }

    public static EndermanTeleportListener getListener() {
        if (endermanTeleportListener == null) {
            endermanTeleportListener = new EndermanTeleportListener();
        }
        return endermanTeleportListener;
    }

    /** 注册末影人（末影人塔生成塔身时调用） */
    public void addEntityUUID(UUID uuid) {
        uuidSet.add(uuid);
    }

    /** 注销（塔被出售/移除时调用，防 UUID 泄漏） */
    public void removeEntityUUID(UUID uuid) {
        uuidSet.remove(uuid);
    }

    @EventHandler
    public void onEndermanTeleport(EntityTeleportEvent event) {
        if (uuidSet.contains(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
        }
    }
}

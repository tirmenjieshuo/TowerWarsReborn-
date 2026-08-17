package io.github.pako25.towerWars.Arena.MobData;

import io.github.pako25.towerWars.Arena.MobType;
import io.github.pako25.towerWars.Arena.TWMob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 怪物路径导航（NMS 直连层）。
 *
 * ⚠️ NMS 耦合声明：本类是全插件唯一直接操作 Minecraft 内部类
 * （PathfinderMob/PathNavigation/Path/Node）的地方——Bukkit API 没有暴露
 * "沿指定路径点导航"的能力，必须经由 NMS 的路径导航系统实现。
 * 升级 Minecraft 版本时，本文件是唯一需要核对映射的 NMS 面。
 *
 * 设计意图：
 * 路径点（waypoint）序列来自竞技场配置，导航系统把"怪 → 下个路径点"串成
 * 一条 Path。怪物沿路径前进、被末影人塔传回、被守卫者逼退（倒退）、被
 * 传送技能跳格（skipBlocks），本质都是"重建/替换当前 Path"。
 */
public class MobNavigation {

    private PathNavigation navigation;
    /** 竞技场配置的路径点序列（相对 trackSpawn 的向量） */
    private final ArrayList<Vector> path;
    private final Location trackSpawn;
    private final TWMob twMob;

    /** 是否处于"倒退行走"状态（守卫者专精 1 的逼退效果） */
    private boolean walkingBackwards = false;
    private double lastPathLeft = 0;
    private int walkingBackwardsTimer = 0;

    public MobNavigation(ArrayList<Vector> path, Location trackSpawn, TWMob twMob) {
        this.path = path;
        this.trackSpawn = trackSpawn;
        this.twMob = twMob;
    }

    /**
     * 启动导航：把路径点序列（path[1..n-1]，不含出生点）灌进 NMS 路径系统。
     * 做法是先 createPath 出一个空壳再清空 nodes，手动把每个路径点转成 Node 填进去，
     * 最后按怪的速度 moveTo。
     */
    public void startNavigation() {
        navigation = ((PathfinderMob) ((CraftLivingEntity) twMob.getNavigatableCreature()).getHandle()).getNavigation();
        navigation.setCanFloat(true);

        Path navigationPath = null;
        for (int i = 1; i < path.size(); i++) {
            Vector waypoint = path.get(i);
            Location goal = trackSpawn.clone().add(waypoint).add(0.5, 0, 0.5);
            if (i == 1) {
                navigationPath = navigation.createPath(goal.getBlockX(), goal.getBlockY(), goal.getBlockZ(), 0);
                if (navigationPath == null) {
                    // 寻路失败（如出生点被方块封死）：跳过导航，怪原地停留
                    twMob.getPlugin().getLogger().warning("怪物路径创建失败（路径点 " + goal + " 不可达），跳过本次导航。");
                    return;
                }
                navigationPath.nodes.clear();
            }
            navigationPath.nodes.add(new Node(goal.getBlockX(), goal.getBlockY(), goal.getBlockZ()));
        }

        navigation.moveTo(navigationPath, Math.sqrt(twMob.getSpeed()));
    }

    /**
     * 剩余路程（曼哈顿距离）：当前位置 → 下个导航节点 + 沿剩余节点逐段累加。
     * 每 tick 被每座塔的选目标逻辑调用。
     *
     * 实现说明（为什么不用预计算距离表）：导航 Path 会被 walkBackwards /
     * resumeForward / skipBlocks 反复重建——重建后的节点序列是完整路径的
     * **子序列**，任何基于"完整路径索引"的预计算都会错位。因此这里保留
     * 原版的线性遍历（对"当前 Path 的节点"精确求和），性能差异在射程内
     * 怪物数量有限时可忽略。
     * 倒退行走期间沿用上次的缓存值（倒退时距离无意义，只是保持判定不跳变）。
     */
    public double getPathLeft() {
        if (navigation == null) return 0;
        if (walkingBackwards) return lastPathLeft;
        Path currentPath = navigation.getPath();
        if (currentPath == null) return 0;
        Node nextNode = currentPath.getNextNode();
        if (nextNode == null) return 0; // 已到终点：剩余 0

        double pathLeft = customVectorDistance(twMob.getLocation().toVector(), nodeToVector(nextNode));
        List<Node> nodeList = currentPath.nodes;
        // 从当前目标节点起，逐段累加到最后一个节点
        int nextNodeIndex = currentPath.getNextNodeIndex();
        for (int i = nextNodeIndex; i < nodeList.size() - 1; i++) {
            pathLeft += customVectorDistance(nodeToVector(nodeList.get(i)), nodeToVector(nodeList.get(i + 1)));
        }
        lastPathLeft = pathLeft;
        return pathLeft;
    }

    private Vector nodeToVector(Node node) {
        return new Vector(node.x, node.y, node.z);
    }

    /** 曼哈顿距离（只算 X/Z，路径导航按格子走，Y 变化不在寻路考量内） */
    private double customVectorDistance(Vector v1, Vector v2) {
        double dx = Math.abs(v1.getX() - v2.getX());
        double dz = Math.abs(v1.getZ() - v2.getZ());
        return dx + dz;
    }

    /** 末影人塔效果：把怪瞬间传送回出生点并重启导航（相当于强制回城） */
    public void teleportBack() {
        twMob.getNavigatableCreature().teleport(trackSpawn.clone().add(path.getFirst()));
        // 幽灵/鱿鱼/兔子这类"驮行怪"（隐形蠹虫驮着走）需要连坐骑一起搬
        if (twMob.getMobType() == MobType.GHAST || twMob.getMobType() == MobType.SQUID || twMob.getMobType() == MobType.RABBIT) {
            twMob.getNavigatableCreature().removePassenger(twMob.getCreature());
            twMob.getCreature().teleport(trackSpawn.clone().add(path.getFirst()));
            twMob.getNavigatableCreature().teleport(trackSpawn.clone().add(path.getFirst()));
            twMob.getNavigatableCreature().addPassenger(twMob.getCreature());
        }
        navigation.stop();
        startNavigation();
    }

    /**
     * 守卫者专精 1 的逼退：让怪沿路径"倒着走"回出生点方向。
     * 把当前目标节点之前的所有路径点反序（加上出生点）拼成一条新 Path 喂给导航。
     */
    public void walkBackwards(int duration) {
        if (walkingBackwards) {
            walkingBackwardsTimer = duration; // 已在倒退：只续时长
            return;
        }

        Node nextNode = navigation.getPath().getNextNode();
        List<Node> previousNodes = new ArrayList<>();
        for (Vector waypoint : path) {
            Location goal = trackSpawn.clone().add(waypoint).add(0.5, 0, 0.5);
            Node node = new Node(goal.getBlockX(), goal.getBlockY(), goal.getBlockZ());
            if (node.equals(nextNode)) break;
            previousNodes.add(node);
        }
        Collections.reverse(previousNodes);
        Location spawn = trackSpawn.clone().add(path.getFirst());
        // 若被逼退到起点，则补上出生点节点
        previousNodes.add(new Node(spawn.getBlockX(), spawn.getBlockY(), spawn.getBlockZ()));
        navigation.stop();

        // 构建"向回走"的路径
        Path backwardsPath = navigation.createPath(nextNode.x, nextNode.y, nextNode.z, 0);
        backwardsPath.nodes.clear();
        backwardsPath.nodes.addAll(previousNodes);
        navigation.moveTo(backwardsPath, Math.sqrt(twMob.getSpeed()));

        walkingBackwardsTimer = duration;
        walkingBackwards = true;
    }

    /** 倒走结束，恢复正常前进（保留当前路径点位置，从"下一个未走点"继续） */
    public void resumeForward() {
        if (navigation.getPath().getNextNodeIndex() == navigation.getPath().nodes.size()) {
            startNavigation(); // 倒走时已走完所有节点：干脆重启全程导航
            return;
        }

        Node nextNode = navigation.getPath().getNextNode();
        navigation.stop();
        // 从"当前目标节点之后"的路径点重建前进路径
        Path forwardPath = navigation.createPath(nextNode.x, nextNode.y, nextNode.z, 0);
        forwardPath.nodes.clear();
        boolean reachedNextNode = false;
        for (Vector waypoint : path) {
            Location goal = trackSpawn.clone().add(waypoint).add(0.5, 0, 0.5);
            Node node = new Node(goal.getBlockX(), goal.getBlockY(), goal.getBlockZ());
            if (reachedNextNode) {
                forwardPath.nodes.add(node);
            }
            if (node.equals(nextNode)) {
                reachedNextNode = true;
            }
        }
        navigation.moveTo(forwardPath, Math.sqrt(twMob.getSpeed()));
    }

    /**
     * TPOnHit 技能：让怪沿路径方向瞬移 {amount} 格（跳过方块）。
     * 分段计算：先看当前段剩余够不够，不够就沿后续路径点继续消耗距离，
     * 最后按到达位置重建剩余导航路径。
     */
    public void skipBlocks(int amount) {
        Location mobLocation = twMob.getLocation().clone();
        Node nextNode = navigation.getPath().getNextNode();

        Vector lastWaypoint = mobLocation.toVector();
        Vector nextWaypoint = nodeToVector(nextNode);
        double distance = customVectorDistance(lastWaypoint, nextWaypoint);

        Vector endVector;
        if (distance >= amount) {
            // 当前段内就够跳
            endVector = nextWaypoint.clone().subtract(lastWaypoint).normalize().multiply(amount);
            Location newPosition = mobLocation.toVector().clone().add(endVector).toLocation(mobLocation.getWorld());
            twMob.getCreature().teleport(newPosition);
            return;
        }

        // 跨多个路径点消耗剩余距离
        endVector = nextWaypoint.clone().subtract(lastWaypoint);
        int i = path.indexOf(nextWaypoint.clone().subtract(trackSpawn.toVector())) + 1;

        while (distance < amount && i < path.size()) {
            lastWaypoint = nextWaypoint;
            nextWaypoint = trackSpawn.clone().toVector().add(path.get(i));
            double segmentLength = customVectorDistance(lastWaypoint, nextWaypoint);
            distance += segmentLength;
            if (distance >= amount) {
                endVector.add(nextWaypoint.clone().subtract(lastWaypoint).normalize().multiply(segmentLength - (distance - amount)));
            } else {
                endVector.add(nextWaypoint.clone().subtract(lastWaypoint));
            }
            i++;
        }

        Location newPosition = mobLocation.toVector().clone().add(endVector).toLocation(mobLocation.getWorld());
        newPosition.setY(mobLocation.getY());
        navigation.stop();
        twMob.getCreature().teleport(newPosition);

        // 重建跳完之后的前进路径
        Path forwardPath = navigation.createPath(nextNode.x, nextNode.y, nextNode.z, 0);
        forwardPath.nodes.clear();
        forwardPath.nodes.add(new Node(nextWaypoint.getBlockX(), nextWaypoint.getBlockY(), nextWaypoint.getBlockZ()));
        boolean reachedNextNode = false;
        for (Vector waypoint : path) {
            Vector absoluteVector = trackSpawn.clone().toVector().add(waypoint);
            if (reachedNextNode) {
                forwardPath.nodes.add(new Node(absoluteVector.getBlockX(), absoluteVector.getBlockY(), absoluteVector.getBlockZ()));
            }
            if (absoluteVector.equals(nextWaypoint)) {
                reachedNextNode = true;
            }
        }
        navigation.moveTo(forwardPath, Math.sqrt(twMob.getSpeed()));
    }

    public PathNavigation getNavigation() {
        return navigation;
    }

    // ========== 倒退状态读写（守卫者逼退效果的驱动） ==========

    public boolean isWalkingBackwards() {
        return walkingBackwards;
    }

    public double getLastPathLeft() {
        return lastPathLeft;
    }

    public void setWalkingBackwards(boolean walkingBackwards) {
        this.walkingBackwards = walkingBackwards;
    }

    public int getBackwardsTimer() {
        return walkingBackwardsTimer;
    }

    public void decrementBackwardsTimer() {
        walkingBackwardsTimer--;
    }
}

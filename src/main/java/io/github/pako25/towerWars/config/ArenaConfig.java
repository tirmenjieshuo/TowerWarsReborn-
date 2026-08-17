package io.github.pako25.towerWars.config;

import io.github.pako25.towerWars.CustomConfig;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * 单个竞技场配置的类型安全访问层（&lt;arenaName&gt;.yml）。
 *
 * 设计意图：
 * 原版在 Game.initialiseTracks、Track.loadBounds/loadPaths、GameQueue、SignManager
 * 等处各自散落地解析同一个 yml，报错信息不统一（有的 throw、有的 warning 跳过）。
 * 本类把"一个竞技场"的解析收拢到一处：字段缺失抛带中文说明的异常，
 * 非法条目记 warning 跳过，供各模块直接读取强类型数据。
 */
public final class ArenaConfig {

    private final Plugin plugin;
    private final String arenaName;
    private final FileConfiguration config;

    /** 是否启用了该竞技场（enabled 键，缺省视为禁用） */
    private final boolean enabled;
    private final String worldName;
    private final Material towerPlaceMaterial;
    /** 轨道出生点（绝对坐标） */
    private final List<Location> trackSpawns;
    /** 轨道可放置塔的矩形边界（相对出生点的向量，Y 忽略），固定 4 个角 */
    private final Vector[] trackBounds;
    /** 怪物行进路径（相对向量序列），每条路径一个列表 */
    private final List<List<Vector>> paths;

    public ArenaConfig(Plugin plugin, String arenaName) {
        this.plugin = plugin;
        this.arenaName = arenaName;
        this.config = CustomConfig.getFileConfiguration(arenaName);

        this.enabled = config.getBoolean("enabled", false);
        this.worldName = config.getString("worldName");
        this.towerPlaceMaterial = parseTowerPlaceMaterial();
        this.trackSpawns = parseTrackSpawns();
        this.trackBounds = parseTrackBounds();
        this.paths = parsePaths();
    }

    private Material parseTowerPlaceMaterial() {
        String raw = config.getString("towerPlaceMaterial");
        if (raw == null) {
            throw new IllegalStateException("竞技场 '" + arenaName + "' 缺少 towerPlaceMaterial 字段！");
        }
        Material material = Material.matchMaterial(raw, false);
        if (material == null) {
            throw new IllegalStateException("竞技场 '" + arenaName + "' 的 towerPlaceMaterial 非法: '" + raw + "'！");
        }
        return material;
    }

    private List<Location> parseTrackSpawns() {
        List<Location> result = new ArrayList<>();
        List<?> raw = config.getList("trackSpawns");
        if (raw == null) {
            throw new IllegalStateException("竞技场 '" + arenaName + "' 缺少 trackSpawns 配置段！");
        }
        for (Object obj : raw) {
            if (!(obj instanceof List<?> coords) || coords.size() != 3) {
                plugin.getLogger().warning("竞技场 '" + arenaName + "' 中存在非法的 trackSpawns 条目，已跳过: " + obj);
                continue;
            }
            World world = plugin.getServer().getWorld(worldName);
            result.add(new Location(world, (int) coords.get(0), (int) coords.get(1), (int) coords.get(2)));
        }
        return result;
    }

    private Vector[] parseTrackBounds() {
        Vector[] bounds = new Vector[4];
        List<?> raw = config.getList("trackBounds");
        if (raw == null) {
            throw new IllegalStateException("竞技场 '" + arenaName + "' 缺少 trackBounds 配置段！");
        }
        for (int i = 0; i < raw.size() && i < bounds.length; i++) {
            Object obj = raw.get(i);
            if (!(obj instanceof List<?> coords) || coords.size() != 3) {
                plugin.getLogger().warning("竞技场 '" + arenaName + "' 中存在非法的 trackBounds 条目，已跳过: " + obj);
                continue;
            }
            bounds[i] = new Vector((int) coords.get(0), (int) coords.get(1), (int) coords.get(2));
        }
        return bounds;
    }

    private List<List<Vector>> parsePaths() {
        List<List<Vector>> result = new ArrayList<>();
        List<?> raw = config.getList("paths");
        if (raw == null) {
            throw new IllegalStateException("竞技场 '" + arenaName + "' 缺少 paths 配置段！");
        }
        for (Object pathObj : raw) {
            if (!(pathObj instanceof List<?> pathList)) {
                plugin.getLogger().warning("竞技场 '" + arenaName + "' 中存在非法的 path 条目，已跳过: " + pathObj);
                continue;
            }
            List<Vector> path = new ArrayList<>();
            for (Object coordObj : pathList) {
                if (!(coordObj instanceof List<?> coordList) || coordList.size() != 3) {
                    plugin.getLogger().warning("竞技场 '" + arenaName + "' 中存在非法的路径坐标，已跳过: " + coordObj);
                    continue;
                }
                path.add(new Vector((int) coordList.get(0), (int) coordList.get(1), (int) coordList.get(2)));
            }
            if (!path.isEmpty()) result.add(path);
        }
        return result;
    }

    // ========== 只读访问器 ==========

    public boolean isEnabled() {
        return enabled;
    }

    public String getWorldName() {
        return worldName;
    }

    public Material getTowerPlaceMaterial() {
        return towerPlaceMaterial;
    }

    /** 轨道出生点（绝对坐标），数量即最大玩家数 */
    public List<Location> getTrackSpawns() {
        return trackSpawns;
    }

    /** 每个出生点的塔放置矩形边界（相对向量，Y 忽略），固定 4 个角 */
    public Vector[] getTrackBounds() {
        return trackBounds;
    }

    /** 全部怪物路径（相对向量序列） */
    public List<List<Vector>> getPaths() {
        return paths;
    }
}

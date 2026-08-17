package io.github.pako25.towerWars.Editor;

import io.github.pako25.towerWars.Player.TWPlayer;
import io.github.pako25.towerWars.TowerWars;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * 编辑器高亮粒子（每 8 tick 循环）：编辑者手持不同工具时，
 * 用粒子标出对应的配置对象——
 * 木棍=全部出生点；栅栏=出生点+4 个边界角；铁轨/中继器=路径点
 * （起点白烟、终点黑烟、中间村民粒子）。
 */
public class EditorBlockHighlight extends BukkitRunnable {

    public static EditorBlockHighlight EditorBlockHighlightFactory() {
        EditorBlockHighlight i = new EditorBlockHighlight();
        i.start();
        return i;
    }

    @Override
    public void run() {
        for (TWPlayer twPlayer : TWPlayer.getTWPlayerCollection()) {
            if (!twPlayer.isInEditor()) continue;

            Player player = twPlayer.getPlayer();
            ArenaEditor arenaEditor = twPlayer.getActiveArenaEditor();

            ItemStack item = player.getInventory().getItemInMainHand();
            if (item.getType() == Material.STICK) {
                // 木棍：高亮全部轨道出生点
                for (Location trackSpawn : arenaEditor.getTrackSpawns()) {
                    spawnHighlightParticles(trackSpawn.getBlock(), player, Particle.HAPPY_VILLAGER);
                }
            }
            if (item.getType() == Material.OAK_FENCE) {
                // 栅栏：高亮出生点（蜂蜜粒子）+ 4 个边界角
                try {
                    Location trackSpawn = arenaEditor.getTrackSpawns().getFirst();
                    spawnHighlightParticles(trackSpawn.getBlock(), player, Particle.FALLING_HONEY);
                    for (Vector trackBound : arenaEditor.getTrackBounds()) {
                        if (trackBound == null) continue;
                        spawnHighlightParticles(trackSpawn.clone().add(trackBound).getBlock(), player, Particle.HAPPY_VILLAGER);
                    }
                } catch (NoSuchElementException ignored) {
                    // 还没设置出生点：不显示
                }
            }
            if (item.getType() == Material.REPEATER || item.getType() == Material.RAIL) {
                // 铁轨/中继器：高亮当前选中路径的全部路径点
                if (arenaEditor.getTrackSpawns().isEmpty()) return;
                Location trackSpawn = arenaEditor.getTrackSpawns().getFirst();
                spawnHighlightParticles(trackSpawn.getBlock(), player, Particle.FALLING_HONEY);

                if (arenaEditor.getPaths().isEmpty()) return;
                List<Vector> path = arenaEditor.getPaths().get(arenaEditor.getSelectedPathIndex());

                if (path.size() == 1) {
                    spawnHighlightParticles(trackSpawn.clone().add(path.getFirst()).getBlock(), player, Particle.WHITE_SMOKE);
                } else {
                    for (int i = 0; i < path.size(); i++) {
                        if (i == 0) {
                            spawnHighlightParticles(trackSpawn.clone().add(path.get(i)).getBlock(), player, Particle.WHITE_SMOKE); // 起点
                        } else if (i == path.size() - 1) {
                            spawnHighlightParticles(trackSpawn.clone().add(path.get(i)).getBlock(), player, Particle.SMOKE); // 终点
                        } else {
                            spawnHighlightParticles(trackSpawn.clone().add(path.get(i)).getBlock(), player, Particle.HAPPY_VILLAGER); // 中间点
                        }
                    }
                }
            }
        }
    }

    private void spawnHighlightParticles(Block block, Player player, Particle particle) {
        player.spawnParticle(
                particle,
                block.getLocation().add(0.5, 1.0, 0.5),
                10,            // 粒子数量
                0.3, 0.3, 0.3, // 扩散范围（XYZ 偏移）
                0.05           // 速度
        );
    }

    public void start() {
        this.runTaskTimer(TowerWars.getPlugin(), 0L, 8L);
    }

    public void stop() {
        this.cancel();
    }
}

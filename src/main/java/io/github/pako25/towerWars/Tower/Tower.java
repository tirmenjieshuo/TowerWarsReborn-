package io.github.pako25.towerWars.Tower;

import com.destroystokyo.paper.entity.ai.MobGoals;
import io.github.pako25.towerWars.Arena.AntiFire;
import io.github.pako25.towerWars.Arena.Track;
import io.github.pako25.towerWars.config.TowerConfig;
import io.github.pako25.towerWars.message.Messages;
import io.github.pako25.towerWars.Tower.TowerSchemas.VillagerTower;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;

import java.util.HashSet;
import java.util.Set;

/**
 * 塔抽象基类：管理一切塔的通用生命周期。
 *
 * 设计意图：
 * 本类只保留"一座塔是什么样、如何被升级/出售/受减益影响"的通用逻辑——
 * 等级与数值、冷却与减速/眩晕/失明三组减益计时、可升级提示、出售回收等。
 * "如何攻击"不是所有塔都有的能力（村民是纯支援塔），因此攻击相关抽象
 * 已上移到 {@link AttackTower} / {@link SupportTower} 两个子类层次。
 *
 * 每个服务器 tick 的驱动点见 {@link #onTick()} 与 {@link #onNothingInRange()}：
 * 这两个钩子由 Track.tickTrack 调用，取代了原版在调用处做 instanceof 特判的做法
 * （守卫者清激光、特斯拉动画、村民增益都迁入各自覆写的 onTick）。
 */
public abstract class Tower {

    protected int level = 1;
    protected int maxLevel = 3;
    protected int prestige = 0;
    protected Location location;
    /** 塔身实体集合（多为叠罗汉的多只怪） */
    protected Set<Mob> entities = new HashSet<>();
    protected final Track track;
    /** 正在给自己提供增益的村民塔集合（用于出售时撤销增益） */
    protected Set<VillagerTower> villagerProtectors = new HashSet<>();

    protected int damage;
    protected double reload;
    protected int range;
    protected int splash;
    protected int cost;
    /** 攻击冷却（秒），攻击后按 reload/reloadBoost 充填 */
    protected double cooldown = 0;
    protected boolean immuneToDebuffs = false;
    /** 攻速加成倍率（村民 2 级提供 1.1） */
    protected float reloadBoost = 1;
    private TowerType towerType;

    // —— 三组减益计时（单位：Tick 计数，每 0.25 递减一次） ——
    private float slownessTimer = 0;
    private float slownessAmplifier = 1;
    private float stunTimer = 0;
    private float blindnessTimer = 0;
    private float blindnessAmplifier = 1F;
    /** 减速/眩晕/失明的特效指示方块（蜘蛛网）位置，由各塔子类指定高度 */
    protected Location slownessIndicatorLocation;

    // —— 可升级提示（ArmorStand 头顶 ✜ 文本） ——
    private boolean isUpgradableTextShown = false;
    private ArmorStand upgradableText;

    // —— 战斗统计（升级菜单"统计"页展示） ——
    protected int damageDealt = 0;
    protected int shots = 0;
    protected int kills = 0;

    public Tower(Location location, int level, int prestige, Track track) {
        this.location = location;
        this.level = level;
        this.prestige = prestige;
        this.track = track;
        spawn();
        setSlownessIndicatorHeight();
        initiateUpgradableText();
    }

    /** 生成塔身实体（各塔子类实现自己的造型） */
    abstract public void spawn();

    /** 出售/比赛结束时清理塔身实体与特效 */
    public abstract void cleanup();

    /** 指定减速指示方块（蜘蛛网）的高度，各塔按自身造型高度覆写 */
    protected abstract void setSlownessIndicatorHeight();

    /**
     * 每 tick 钩子：由 Track.tickTrack 在冷却检查之前对每座塔调用一次。
     * 原版在这里对守卫者/特斯拉/村民做 instanceof 特判，现改为子类覆写：
     * - VillagerTower 在此做附近塔的增益；
     * - GuardianTower 在此清空上一 tick 的激光瞄准；
     * - TeslaTower 在此驱动"通电动画"。
     */
    public void onTick() {
        // 空默认实现：攻击塔大多不需要
    }

    /**
     * 射程内没有怪但全场有怪时的钩子：塔冷却结束且射程为空时调用一次。
     * 特斯拉塔用它清除伤害叠加（目标离开射程即清空乘区）。
     */
    public void onNothingInRange() {
        // 空默认实现
    }

    /** 从 towerConfig 读取当前等级/专精的数值并应用到本塔 */
    public void applyStats(TowerType towerType) {
        this.towerType = towerType;
        TowerConfig.LevelStats levelStats = TowerConfig.levelStats(towerType, level);
        TowerConfig.PrestigeStats prestigeStats = prestige == 0 ? null : TowerConfig.prestigeStats(towerType, prestige);

        maxLevel = TowerConfig.maxLevel(towerType);

        // 塔身实体：加入防燃烧名单、无敌、不可碰撞、移除原版 AI
        entities.forEach(mob -> {
            AntiFire.getListener().add(mob);
            mob.setInvulnerable(true);
            mob.setCollidable(false);
            MobGoals mobGoals = Bukkit.getMobGoals();
            mobGoals.removeAllGoals(mob);
        });

        if (prestigeStats == null) {
            damage = levelStats.damage();
            reload = levelStats.reload();
            range = levelStats.range();
            splash = levelStats.splash();
            cost = levelStats.cost();
        } else {
            damage = prestigeStats.damage();
            reload = prestigeStats.reload();
            range = prestigeStats.range();
            splash = prestigeStats.splash();
            cost = prestigeStats.cost();
        }
    }

    /** 升一级：移除旧实体、重新生成新造型、隐藏可升级提示 */
    public void upgrade() {
        if (level < maxLevel) {
            level++;
            entities.forEach(Entity::remove);
            spawn();
            isUpgradableTextShown = false;
            upgradableText.setCustomNameVisible(false);
        }
    }

    /** 进阶为指定专精：要求已满级；level 跳到 4 表示"专精形态" */
    public void prestige(int prestigeType) {
        if (level == maxLevel) {
            level = 4;
            prestige = prestigeType;
            entities.forEach(Entity::remove);
            spawn();
            isUpgradableTextShown = false;
            upgradableText.setCustomNameVisible(false);
        }
    }

    /**
     * 冷却与减益主入口（每 tick 由 Track.tickTrack 调用）：
     * 眩晕中直接冻结；减速按倍率延长冷却；失明按概率跳过攻击。
     * 返回 true 表示本 tick 不能攻击。
     */
    public boolean isOnCooldown() {
        if (track.getGame().getTickCounter() % 4 == 0) {
            showUpgradableText();
        }
        if (stunTimer > 0) {
            stunTimer -= 0.25F;
            showStunEffectParticles();
            return true;
        }
        if (slownessTimer > 0) {
            slownessTimer -= 0.25F;
        } else {
            slownessAmplifier = 1F;
            hideSlownessIndicator();
        }
        if (cooldown > 0) {
            cooldown = cooldown - (0.25 / slownessAmplifier);
            return true;
        }
        if (blindnessTimer > 0) {
            blindnessTimer -= 0.25F;
            showBlindnessEffectParticles();
            if (Math.random() < blindnessAmplifier) {
                resetCooldown();
                return true;
            }
        }
        return false;
    }

    /** 攻击后重置冷却：reload 除以攻速加成（村民 buff 或减速影响冷却节奏） */
    protected void resetCooldown() {
        cooldown = reload / reloadBoost;
    }

    private void showSlownessIndicator() {
        slownessIndicatorLocation.getBlock().setType(Material.COBWEB);
    }

    private void hideSlownessIndicator() {
        slownessIndicatorLocation.getBlock().setType(Material.AIR);
    }

    /** 出售塔：半价回收、移除实体与特效、通知村民塔撤销增益、从赛道登记移除 */
    public void sell() {
        isUpgradableTextShown = false;
        upgradableText.remove();
        hideSlownessIndicator();
        entities.forEach(mob -> {
            AntiFire.getListener().remove(mob);
            mob.remove();
        });
        cleanup();
        for (VillagerTower villagerTower : villagerProtectors) {
            villagerTower.removeFromProtection(this);
        }
        track.cleanupSoldTower(this);
    }

    /** 村民增益：清空所有减益并记录保护者（之后对减益免疫） */
    public void applyDebuffProtection(VillagerTower villagerProtector) {
        blindnessTimer = 0;
        slownessTimer = 0;
        stunTimer = 0;
        villagerProtectors.add(villagerProtector);
        immuneToDebuffs = true;
    }

    /** 村民 2 级：攻速 +percent */
    public void applyReloadBoost(float percent) {
        reloadBoost = 1 + percent;
    }

    /** 村民塔被出售/移除时撤销它施加的全部增益 */
    public void removeVillagerBoosts(VillagerTower villagerTower) {
        villagerProtectors.remove(villagerTower);
        if (villagerProtectors.isEmpty()) {
            reloadBoost = 1;
            immuneToDebuffs = false;
        }
    }

    /** 用 100 个粒子沿射程圆周显示攻击范围（升级菜单"显示射程"按钮） */
    public void showRange() {
        int count = 100;
        double angleIncrement = (2 * Math.PI) / count;
        for (int i = 0; i < count; i++) {
            double angle = i * angleIncrement;
            double spawnX = location.x() + Math.cos(angle) * range;
            double spawnY = location.y() + 3;
            double spawnZ = location.z() + Math.sin(angle) * range;
            track.getTwPlayer().getPlayer().spawnParticle(Particle.HAPPY_VILLAGER, spawnX, spawnY, spawnZ, 3, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private void showStunEffectParticles() {
        float x = slownessIndicatorLocation.getBlockX() + 0.5F;
        float y = slownessIndicatorLocation.getBlockY() + 0.5F;
        float z = slownessIndicatorLocation.getBlockZ() + 0.5F;
        location.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, x, y, z, 3, 0.2, 0.1, 0.2, 0.0);
    }

    private void showBlindnessEffectParticles() {
        float x = slownessIndicatorLocation.getBlockX() + 0.5F;
        float y = slownessIndicatorLocation.getBlockY() + 0.5F;
        float z = slownessIndicatorLocation.getBlockZ() + 0.5F;
        location.getWorld().spawnParticle(Particle.EFFECT, x, y, z, 3, 0.2, 0.1, 0.2, 0.0);
    }

    /** 距离升级（升级塔物品右键）：付差价升级；已满级的普通塔打开升级菜单供进阶 */
    public void upgradeFromDistance() {
        if (level < maxLevel) {
            int cost = TowerConfig.buyCost(towerType, level + 1, 0) - TowerConfig.buyCost(towerType, level, 0);
            boolean success = track.getTwPlayer().buyForCoin(cost);
            if (success) {
                upgrade();
            }
        } else {
            if (maxLevel == 3) {
                track.getTwPlayer().openTowerMenu(location);
            }
        }
    }

    /** 每 1 秒检查一次玩家金币，够了就在塔顶亮出 ✜ 提示 */
    private void showUpgradableText() {
        if ((maxLevel == 2 && level == 2) || level == 4) {
            isUpgradableTextShown = false;
            upgradableText.setCustomNameVisible(false);
            return;
        }

        int cost = 0;
        if (level < maxLevel) {
            cost = TowerConfig.buyCost(towerType, level + 1, 0) - TowerConfig.buyCost(towerType, level, 0);
        } else {
            if (maxLevel == 3) {
                int cost1 = TowerConfig.buyCost(towerType, level, 1) - TowerConfig.buyCost(towerType, level, 0);
                int cost2 = TowerConfig.buyCost(towerType, level, 2) - TowerConfig.buyCost(towerType, level, 0);
                cost = Math.min(cost1, cost2);
            }
        }

        if (cost <= track.getTwPlayer().getCoin()) {
            if (!isUpgradableTextShown) {
                isUpgradableTextShown = true;
                upgradableText.setCustomNameVisible(true);
            }
        } else {
            if (isUpgradableTextShown) {
                isUpgradableTextShown = false;
                upgradableText.setCustomNameVisible(false);
            }
        }
    }

    /** 生成塔顶的可升级提示 ArmorStand（初始隐藏，金币够时显示 ✜） */
    private void initiateUpgradableText() {
        upgradableText = (ArmorStand) location.getWorld().spawnEntity(slownessIndicatorLocation.clone().add(0.5, -2, 0.5), EntityType.ARMOR_STAND);
        upgradableText.setInvulnerable(true);
        upgradableText.setInvisible(true);
        upgradableText.setGravity(false);
        upgradableText.setCustomNameVisible(false);
        upgradableText.customName(Messages.Tower.upgradeAvailableText());
    }

    // ========== 只读访问器与统计 ==========

    public TowerType getTowerType() {
        return towerType;
    }

    public int getLevel() {
        return level;
    }

    public int getPrestige() {
        return prestige;
    }

    public int getDamageDealt() {
        return damageDealt;
    }

    public int getKills() {
        return kills;
    }

    public int getShots() {
        return shots;
    }

    public int getDamage() {
        return damage;
    }

    public double getReload() {
        return reload;
    }

    public int getSplash() {
        return splash;
    }

    public int getRange() {
        return range;
    }

    public Location getLocation() {
        return location;
    }

    /** 修正累计伤害（Enderman 塔对"已死亡怪"重算伤害的补偿） */
    public void correctDamageDealt(int correction) {
        damageDealt = damageDealt - correction;
    }

    public int getCost() {
        return cost;
    }

    public void increaseKillCount() {
        kills++;
    }

    // ========== 减益入口（被怪物技能命中时调用） ==========

    /** 减速：{duration} 秒内冷却走慢（amplifier 越大越慢）；减速期间塔顶出现蜘蛛网 */
    public void applySlowness(int duration, float amplifier) {
        if (immuneToDebuffs) return;
        slownessAmplifier = 1 + amplifier;
        slownessTimer = duration;
        showSlownessIndicator();
    }

    public void applyStun(int duration) {
        if (immuneToDebuffs) return;
        stunTimer = duration;
    }

    /** 失明：{duration} 秒内每次攻击有 {amplifier} 概率落空（冷却被白白重置） */
    public void applyBlindness(int duration, float amplifier) {
        if (immuneToDebuffs) return;
        blindnessTimer = duration;
        blindnessAmplifier = amplifier;
    }

    /** 判断某实体是否属于这座塔（调试工具与拾取拦截用） */
    public boolean isEntityInTower(Mob mob) {
        return entities.contains(mob);
    }

    @Override
    public String toString() {
        // 简化为一行摘要供调试工具使用（原版的实体全量 dump 已移除）
        return "Tower{" +
                "type=" + towerType +
                ", level=" + level +
                ", prestige=" + prestige +
                ", location=" + location +
                ", player=" + track.getTwPlayer().getPlayer().getName() +
                '}';
    }
}

package io.github.pako25.towerWars.Arena;

import io.github.pako25.towerWars.Arena.MobData.MobAbilities.AOEDodge;
import io.github.pako25.towerWars.Arena.MobData.MobAbilities.AbilityTypes;
import io.github.pako25.towerWars.Arena.MobData.MobAbilities.DamageAbsorber;
import io.github.pako25.towerWars.Arena.MobData.MobAbilities.DeathAbility;
import io.github.pako25.towerWars.Arena.MobData.MobAbilities.HealAbility;
import io.github.pako25.towerWars.Arena.MobData.MobAbilities.MobAbility;
import io.github.pako25.towerWars.Arena.MobData.MobAbilities.NoKillBonusesAbility;
import io.github.pako25.towerWars.Arena.MobData.MobAbilities.OnHitAbility;
import io.github.pako25.towerWars.Arena.MobData.MobAbilities.ReduceSlowAbility;
import io.github.pako25.towerWars.Arena.MobData.MobAbilities.TickAbility;
import io.github.pako25.towerWars.Arena.MobData.MobBuilder;
import io.github.pako25.towerWars.Arena.MobData.MobNavigation;
import io.github.pako25.towerWars.Arena.MobData.MobState;
import io.github.pako25.towerWars.GameManagement.PlayerStats;
import io.github.pako25.towerWars.Player.TWPlayer;
import io.github.pako25.towerWars.Tower.Tower;
import io.github.pako25.towerWars.Tower.TowerSchemas.AttackType;
import io.github.pako25.towerWars.message.Messages;
import io.github.pako25.towerWars.util.GameRules;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 怪物运行时包装：把一只 Bukkit Mob 包装成"有血条/减速/虚弱/灼烧/技能"的
 * 塔防单位，独立维护游戏内数值（Bukkit 实体的属性只用于移动速度）。
 *
 * 设计意图：
 * TWMob 是塔与技能系统的作用对象，也是一条赛道上与 Tower 平行的另一半玩法：
 * 伤害结算（takeDamage）、逐秒效果（tickEffects：灼烧/虚弱/减速/倒退/技能）、
 * 死亡与终点判定都收拢在这里，技能通过 abilities 列表以接口方式挂载
 * （装配见 MobBuilder / MobType.createAbilities）。
 */
public class TWMob {

    /** 视觉实体（玩家能看到的那只怪） */
    private final Mob creature;
    private final Track track;
    private final Location trackSpawn;
    private final ArrayList<Vector> path;
    private final TWPlayer summonerTWPlayer;
    private final MobNavigation mobNavigation;
    /** 挂载的技能列表（按 MobType 装配） */
    private final List<MobAbility> abilities = new ArrayList<>();
    /** 导航实体（部分怪用隐形蠹虫驮行，如幽灵/鱿鱼/兔子） */
    private Mob navigatableMob;
    private final MobType mobType;

    private double speed;
    private int income;
    private int health;
    private int maxHealth;
    private int cost;
    private int burnTimer = 0;
    private int weaknessTimer = 0;
    private float weaknessAmplifier = 1;
    private boolean alive = true;
    /** 技能禁用计时（守卫者专精 2 的效果） */
    private int noSpecialAbilityTimer = 0;
    private int slownessTimer = 0;
    /** 眩晕计时（岩浆怪专精 2 的效果：眩晕中移动速度为 0） */
    private int stunTimer = 0;

    public TWMob(Track track, ArrayList<Vector> path, Location trackSpawn, MobType mobType, TWPlayer summonerTWPlayer, Mob creature) {
        this.track = track;
        this.path = path;
        this.trackSpawn = trackSpawn;
        this.summonerTWPlayer = summonerTWPlayer;
        this.creature = creature;
        this.navigatableMob = creature;
        this.mobNavigation = new MobNavigation(path, track.getTrackSpawn(), this);
        this.mobType = mobType;
        applyAttributes(mobType);
        updateHealthDisplay();
        // 延迟 3 tick 启动导航：等实体完全生成后再寻路，避免寻路起点错误
        Bukkit.getScheduler().runTaskLater(track.getPlugin(), mobNavigation::startNavigation, 3L);
    }

    /** 从召唤者的 MobState 快照本怪数值（价格/生命/收入/速度都随召唤者收入浮动） */
    private void applyAttributes(MobType mobType) {
        MobState mobState = summonerTWPlayer.getMobStates().getMobState(mobType);
        this.cost = mobState.getCost(summonerTWPlayer.getIncome());
        this.health = mobState.getHealth();
        maxHealth = this.health;
        this.income = mobState.getIncome(summonerTWPlayer.getIncome());
        this.speed = mobState.getSpeed();
    }

    /**
     * 伤害结算：AOE 可被闪避技能躲掉；命中时触发 onHit 技能；
     * 实际扣血 =（伤害 − 队友吸收）× 虚弱倍率。击杀时统计击杀数与补偿伤害。
     */
    public synchronized boolean takeDamage(int damage, Tower tower, AttackType attackType) {
        if (attackType == AttackType.AOE) {
            AOEDodge aoeDodge = (AOEDodge) getAbilityByType(AbilityTypes.AOEDODGE);
            if (aoeDodge != null && aoeDodge.dodge()) return false;
        }
        OnHitAbility onHitAbility = (OnHitAbility) getAbilityByType(AbilityTypes.HIT);
        if (onHitAbility != null) onHitAbility.onHit();

        creature.playHurtAnimation(1);
        health = (int) (health - getAbsorbedDamageFromAbsorbers(damage) * weaknessAmplifier);
        if (health <= 0) {
            tower.increaseKillCount();
            despawn(true, true);
            // 溢出伤害（把怪打穿到负数）从塔的累计伤害里扣掉，保证统计准确
            tower.correctDamageDealt(Math.abs(health));
        }
        updateHealthDisplay();
        return true;
    }

    /**
     * 吸收伤害结算：附近的"伤害吸收者"（铁傀儡类技能）替本怪挡刀。
     * 注意：本怪自己就带吸收者时不再吸收（防止自己吸自己的死循环）。
     */
    private int getAbsorbedDamageFromAbsorbers(int damage) {
        DamageAbsorber damageAbsorber = (DamageAbsorber) getAbilityByType(AbilityTypes.ABSORBER);
        if (damageAbsorber != null) return damage;

        Set<TWMob> mobSet = track.getMobsInRange(getLocation(), 5);
        for (TWMob closeMob : mobSet) {
            damage -= closeMob.absorbDamage(damage);
        }
        return damage;
    }

    /** 头顶血条：召唤者名（赛道色）+ 剩余生命 ❤ */
    public void updateHealthDisplay() {
        creature.customName(Messages.Gui.mobHealthLabel(
                summonerTWPlayer.getPlayer().getName() + " ",
                health,
                summonerTWPlayer.getTrack().getColor()));
        creature.setCustomNameVisible(true);
    }

    /** 减速：amplifier 越大越慢（Duration 秒）；有"减速抵抗"技能时按反系数抵消 */
    public void applySlowness(float amplifier, int duration) {
        slownessTimer = duration;
        ReduceSlowAbility reduceSlowAbility = (ReduceSlowAbility) getAbilityByType(AbilityTypes.REDUCEDSLOW);
        if (reduceSlowAbility != null) {
            float antiAmplifier = reduceSlowAbility.getAmplifier();
            navigatableMob.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(MobBuilder.BASE_SPEED / ((1 + amplifier) / (1 + antiAmplifier)));
        } else {
            navigatableMob.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(MobBuilder.BASE_SPEED / (1 + amplifier));
        }
    }

    /** 眩晕：移动速度归零（岩浆怪专精 2），计时结束后由 tickEffects 恢复 */
    public void applyStun(int seconds) {
        if (seconds > stunTimer) {
            stunTimer = seconds;
        }
    }

    /** 虚弱：受伤放大（percent 为 0.4 表示受伤 ×1.4），duration 秒 */
    public void applyWeakness(float percent, int seconds) {
        if (seconds > weaknessTimer) {
            weaknessTimer = seconds;
        }
        weaknessAmplifier = 1 + percent;
    }

    /** 每 0.25 秒由 Track.tickTrack 调用一次（fullsecond 时额外处理逐秒效果） */
    public void tick(boolean fullsecond) {
        if (!alive) return;

        // 兜底：血量为 0 也强制死亡（理论上 takeDamage 已处理，双保险）
        if (health <= 0) {
            alive = false;
            despawn(true, true);
        }

        // 到终点：导航完成且不在倒退状态 → 漏怪扣命
        if (mobNavigation.getNavigation() != null && mobNavigation.getNavigation().isDone() && !isWalkingBackwards()) {
            track.loseLive(this);
            boolean despawnSuccessfull = despawn(false, false);
            if (despawnSuccessfull) {
                alive = false;
            }
        }

        if (fullsecond) tickEffects();
    }

    /** 逐秒效果：灼烧掉血、虚弱/减速/倒退计时、技能禁用计时、每 3 秒触发 Tick 技能 */
    public void tickEffects() {
        if (burnTimer > 0) {
            burnTick();
            burnTimer = burnTimer - 1;
        }
        if (weaknessTimer <= 0) {
            weaknessAmplifier = 1;
        }
        if (noSpecialAbilityTimer > 0) {
            noSpecialAbilityTimer = noSpecialAbilityTimer - 1;
        }
        if (stunTimer > 0) {
            // 眩晕中：速度钉死在 0，不进入减速/恢复逻辑
            stunTimer = stunTimer - 1;
            navigatableMob.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0);
        } else if (slownessTimer > 0) {
            slownessTimer -= 1;
        } else {
            // 减速结束：恢复基础移速
            navigatableMob.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(MobBuilder.BASE_SPEED);
        }
        // 倒退计时归零后恢复前进
        if (mobNavigation.getBackwardsTimer() > 0) {
            mobNavigation.decrementBackwardsTimer();
        } else {
            if (mobNavigation.isWalkingBackwards()) {
                mobNavigation.resumeForward();
                mobNavigation.setWalkingBackwards(false);
            }
        }
        // 每 3 秒触发一次 Tick 类技能（治疗/减速光环等）
        if (track.getGame().getTickCounter() % 12 == 0) {
            TickAbility tickAbility = (TickAbility) getAbilityByType(AbilityTypes.TICK);
            if (tickAbility != null) {
                tickAbility.onTick();
            }
        }
    }

    /** 灼烧（岩浆怪专精 1）：每秒掉 5% 生命 */
    public void applyBurn(int seconds) {
        if (seconds > burnTimer) {
            burnTimer = seconds;
        }
    }

    public void burnTick() {
        health = (int) (health * 0.95);
        updateHealthDisplay();
    }

    /**
     * 移除本怪（死亡/到终点/被吸收致死共用）。
     *
     * @param killed       是否算作"被击杀"（到终点为 false）
     * @param killedByTower 是否由塔的攻击造成击杀：只有真击杀才发击杀奖励。
     *                      原版 bug：吸收者替队友挡刀致死时也发击杀金币（无击杀者），
     *                      这里把两条路径区分开，修复了该问题。
     */
    public boolean despawn(boolean killed, boolean killedByTower) {
        if (!alive) return false;

        boolean cancelDespawn = false;
        DeathAbility deathAbility = (DeathAbility) getAbilityByType(AbilityTypes.DEATH);
        if (deathAbility != null) cancelDespawn = deathAbility.onDeath(killed);
        if (cancelDespawn) return false; // 技能（分裂/重生）接管了这次死亡

        if (navigatableMob != creature) navigatableMob.remove();
        creature.remove();
        alive = false;

        if (killed && killedByTower) {
            giveKillBonuses();
        }

        return true;
    }

    /** 击杀奖励：统计 + 飘字 + 金币/收入入账（按怪价的固定比例） */
    private void giveKillBonuses() {
        if (PlayerStats.trackingEnabled)
            PlayerStats.getStats(track.getTwPlayer().getPlayer().getUniqueId()).increaseMob_kills();

        NoKillBonusesAbility noKillBonusesAbility = (NoKillBonusesAbility) getAbilityByType(AbilityTypes.NOKILLBONUSES);
        if (noKillBonusesAbility != null) return;

        int killGold = (int) Math.round(cost * GameRules.KILL_GOLD_RATIO);
        int killIncome = (int) Math.round(cost * GameRules.KILL_INCOME_RATIO);
        ArmorStand killDisplay = (ArmorStand) creature.getWorld().spawnEntity(creature.getLocation(), EntityType.ARMOR_STAND);
        Component textComponent = Messages.Gui.killReward(killGold, killIncome);

        killDisplay.customName(textComponent);
        killDisplay.setCustomNameVisible(true);
        killDisplay.setGravity(false);
        killDisplay.setInvisible(true);
        killDisplay.setInvulnerable(true);
        track.getTwPlayer().increaseCoin(killGold);
        track.getTwPlayer().increaseIncome(killIncome);
        track.getMobKillDisplays().add(killDisplay);
    }

    /** 按类型取技能（技能禁用期间一律返回 null） */
    private MobAbility getAbilityByType(AbilityTypes abilityType) {
        if (noSpecialAbilityTimer > 0) return null;
        for (MobAbility ability : abilities) {
            if (ability.isAbilityType(abilityType)) {
                return ability;
            }
        }
        return null;
    }

    /** 治疗：被治疗者自带治疗技能时不再接受外部治疗（防自我循环） */
    public void heal(float factor) {
        TickAbility tickAbility = (TickAbility) getAbilityByType(AbilityTypes.TICK);
        if (tickAbility instanceof HealAbility) return;

        health += (int) (maxHealth * factor);
        if (health > maxHealth) health = maxHealth;
        updateHealthDisplay();
    }

    /** 吸收者技能：替队友吸收伤害；自己血量不足时替队友而死 */
    public int absorbDamage(int damage) {
        DamageAbsorber damageAbsorber = (DamageAbsorber) getAbilityByType(AbilityTypes.ABSORBER);
        if (damageAbsorber != null) {
            int absorbedDamage = damageAbsorber.absorbDamage(damage);
            if (absorbedDamage > health) {
                // 挡刀致死：无击杀者，因此 killedByTower=false 不发击杀奖励
                despawn(true, false);
                return health;
            }
            health -= absorbedDamage;
            updateHealthDisplay();
            return absorbedDamage;
        }
        return 0;
    }

    // ========== 只读访问器 ==========

    public Location getLocation() {
        return creature.getLocation();
    }

    public Location getEyeLocation() {
        return creature.getEyeLocation();
    }

    public int getHealth() {
        return health;
    }

    public int getMaxHealth() {
        return maxHealth;
    }

    public boolean isAlive() {
        return alive;
    }

    public boolean hasWeakness() {
        return weaknessTimer > 0;
    }

    public void disableSpecialAbility(int duration) {
        noSpecialAbilityTimer = duration;
    }

    public Mob getCreature() {
        return creature;
    }

    /** 是否处于守卫者逼退的"倒退行走"状态 */
    public boolean isWalkingBackwards() {
        return mobNavigation.isWalkingBackwards();
    }

    public MobNavigation getMobNavigation() {
        return mobNavigation;
    }

    public double getSpeed() {
        return speed;
    }

    public List<MobAbility> getAbilities() {
        return abilities;
    }

    public Track getTrack() {
        return track;
    }

    public Plugin getPlugin() {
        return track.getPlugin();
    }

    public ArrayList<Vector> getPath() {
        return path;
    }

    public MobType getMobType() {
        return mobType;
    }

    public void setNavigatableMob(Mob mob) {
        navigatableMob = mob;
    }

    public Mob getNavigatableCreature() {
        return navigatableMob;
    }

    public TWPlayer getSummonerTWPlayer() {
        return summonerTWPlayer;
    }

    /** 比赛结束：停止导航并移除实体 */
    public void gameEnd() {
        mobNavigation.getNavigation().stop();
        if (navigatableMob != creature) navigatableMob.remove();
        creature.remove();
    }
}

package io.github.pako25.towerWars.Arena.MobData;

import com.destroystokyo.paper.entity.ai.MobGoals;
import io.github.pako25.towerWars.Arena.AntiFire;
import io.github.pako25.towerWars.Arena.MobData.MobAbilities.AOEDodge;
import io.github.pako25.towerWars.Arena.MobData.MobAbilities.BlindAbility;
import io.github.pako25.towerWars.Arena.MobData.MobAbilities.DamageAbsorber;
import io.github.pako25.towerWars.Arena.MobData.MobAbilities.HealAbility;
import io.github.pako25.towerWars.Arena.MobData.MobAbilities.MobAbility;
import io.github.pako25.towerWars.Arena.MobData.MobAbilities.NoKillBonusesAbility;
import io.github.pako25.towerWars.Arena.MobData.MobAbilities.ReduceSlowAbility;
import io.github.pako25.towerWars.Arena.MobData.MobAbilities.RespawnAbility;
import io.github.pako25.towerWars.Arena.MobData.MobAbilities.SlowAbility;
import io.github.pako25.towerWars.Arena.MobData.MobAbilities.SplitAbility;
import io.github.pako25.towerWars.Arena.MobData.MobAbilities.StunAbility;
import io.github.pako25.towerWars.Arena.MobData.MobAbilities.TPOnHit;
import io.github.pako25.towerWars.Arena.MobType;
import io.github.pako25.towerWars.Arena.TWMob;
import io.github.pako25.towerWars.Arena.Track;
import io.github.pako25.towerWars.Player.TWPlayer;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Breedable;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Sheep;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 怪物实体工厂：负责"造一只怪"的完整装配流水线——
 * 生成实体 → 应用基础属性（护甲/年龄/颜色）→ 包装成 TWMob →
 * 幽灵/鱿鱼/兔子换成"隐形蠹虫驮行"的导航体 → 按类型挂载技能。
 *
 * 设计意图：
 * 技能装配（applyAbilities）是全插件技能数值最集中的一张表：每种怪挂哪些
 * 技能、系数多少（部分吃召唤加成）都在这里一次定型，技能描述与数值保持同步。
 */
public class MobBuilder {

    /** 怪物基础移动速度（所有减速/眩晕都以它为基准折算） */
    public static final float BASE_SPEED = 0.15F;

    private MobBuilder() {
        // 工具类，禁止实例化
    }

    /** 完整装配：造实体、设属性、包 TWMob、配导航体、挂技能 */
    public static TWMob buildMob(Track track, ArrayList<Vector> path, MobType mobType, TWPlayer summoner) {
        EntityType entityType = track.getMobStates().getMobState(mobType).getEntityType();
        Mob creature = (Mob) track.getTrackSpawn().getWorld().spawnEntity(track.getTrackSpawn().clone().add(path.getFirst()), entityType);
        applyAttributes(mobType, creature);

        TWMob mob = new TWMob(track, path, track.getTrackSpawn(), mobType, summoner, creature);

        // 这三种怪没有地面行走能力（或太慢），改用隐形蠹虫驮着走
        if (mobType == MobType.GHAST || mobType == MobType.SQUID || mobType == MobType.RABBIT) {
            applyCustomNavigatableMob(creature, mob, track, path);
        }

        applyAbilities(mobType, mob, summoner.getTrack().getMobStates().getMobState(mobType));

        return mob;
    }

    /** 生成"导航体"：隐形蠹虫驮着视觉怪，导航都发生在蠹虫身上 */
    private static void applyCustomNavigatableMob(Mob mob, TWMob twMob, Track track, ArrayList<Vector> path) {
        Mob navigatable = (Mob) track.getTrackSpawn().getWorld().spawnEntity(track.getTrackSpawn().clone().add(path.getFirst()), EntityType.SILVERFISH);
        navigatable.addPassenger(mob);
        navigatable.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(BASE_SPEED);
        navigatable.setInvulnerable(true);
        navigatable.setInvisible(true);
        navigatable.setCollidable(false);
        MobGoals mobGoals = Bukkit.getMobGoals();
        mobGoals.removeAllGoals(navigatable);
        twMob.setNavigatableMob(navigatable);
    }

    /** 应用基础属性：年龄定型、特色装备（盔甲僵尸/闪电苦力怕/彩虹羊/迷你僵尸） */
    public static void applyAttributes(MobType mobType, Mob creature) {
        if (creature instanceof Ageable ageable) {
            ageable.setAdult();
            if (creature instanceof Breedable breedable) {
                breedable.setAgeLock(true);
            }
        }

        switch (mobType) {
            case LEATHER_ZOMBIE -> equipFullArmor(creature, Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS);
            case GOLD_ZOMBIE -> equipFullArmor(creature, Material.GOLDEN_HELMET, Material.GOLDEN_CHESTPLATE, Material.GOLDEN_LEGGINGS, Material.GOLDEN_BOOTS);
            case DIAMOND_ZOMBIE -> equipFullArmor(creature, Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS);
            case CHARGED_CREEPER -> ((Creeper) creature).setPowered(true);
            case RAINBOW_SHEEP -> {
                // 彩虹羊：随机染一种羊毛色（每次召唤都不同）
                Sheep sheep = (Sheep) creature;
                DyeColor[] colors = DyeColor.values();
                sheep.setColor(colors[ThreadLocalRandom.current().nextInt(colors.length)]);
            }
            case MINI_ZOMBIE -> {
                if (creature instanceof Ageable ageable) {
                    ageable.setBaby(); // 迷你僵尸：保持幼体形态
                }
            }
            default -> {
            }
        }

        creature.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(BASE_SPEED);
        AntiFire.getListener().add(creature);
        creature.setAggressive(false);
        creature.setCollidable(false);
        creature.setInvulnerable(true);
        MobGoals mobGoals = Bukkit.getMobGoals();
        mobGoals.removeAllGoals(creature);
    }

    /** 给僵尸系怪物穿整套盔甲（三种僵尸的区别只有材质） */
    private static void equipFullArmor(Mob creature, Material helmet, Material chestplate, Material leggings, Material boots) {
        creature.getEquipment().setHelmet(new ItemStack(helmet));
        creature.getEquipment().setChestplate(new ItemStack(chestplate));
        creature.getEquipment().setLeggings(new ItemStack(leggings));
        creature.getEquipment().setBoots(new ItemStack(boots));
    }

    /**
     * 技能装配表：按怪种挂载技能（数值系数已含召唤加成）。
     * 设计意图：这里是"哪种怪有什么本事"的唯一事实来源，所有技能系数
     * （概率/时长/半径）都集中在这张表里，平衡调整只改这里。
     */
    public static void applyAbilities(MobType mobType, TWMob twMob, MobState mobState) {
        List<MobAbility> abilities = twMob.getAbilities();
        switch (mobType) {
            case WOLF -> abilities.add(new AOEDodge(0.33F));
            case RABBIT -> abilities.add(new AOEDodge(0.5F));
            case WILD_CAT -> abilities.add(new AOEDodge(1F));
            case PRIEST -> abilities.add(new HealAbility(0.2F, 5, twMob));
            case HIGH_PRIEST -> abilities.add(new HealAbility(0.4F * (1 + mobState.getSummoningBonusHealingFactor()), 5, twMob));
            case ENDERMITE -> abilities.add(new TPOnHit((int) (5 * (1 + mobState.getSummoningBonusTpDistanceFactor())), twMob));
            case BLACK_SPIDER -> abilities.add(new SlowAbility(twMob, 5, 0.2F, 2));
            case SPIDER_JOCKEY -> abilities.add(new SlowAbility(twMob, 5, 0.4F * (1 + mobState.getSummoningBonusSlowFactor()), 2));
            case CREEPER -> abilities.add(new StunAbility(5, 2, twMob));
            case CHARGED_CREEPER -> abilities.add(new StunAbility((int) (8 * (1 + mobState.getSummoningBonusStunRange())), 2, twMob));
            case SQUID -> abilities.add(new BlindAbility(5, 0.33F * (1 + mobState.getSummoningBonusBlindFactor()), 5, twMob));
            case LEATHER_ZOMBIE -> abilities.add(new DamageAbsorber(0.25F, twMob));
            case GOLD_ZOMBIE -> abilities.add(new DamageAbsorber(0.6F, twMob));
            case DIAMOND_ZOMBIE -> abilities.add(new DamageAbsorber(0.8F, twMob));
            case WILD_HORSE -> abilities.add(new ReduceSlowAbility(0.5F));
            case ZOMBIE -> abilities.add(new SplitAbility(twMob));
            case DEATH_RIDER -> {
                abilities.add(new RespawnAbility(twMob));
                abilities.add(new NoKillBonusesAbility());
            }
            case GHAST -> {
                abilities.add(new NoKillBonusesAbility());
                abilities.add(new DamageAbsorber(1F, twMob));
            }
            default -> {
            }
        }
    }
}

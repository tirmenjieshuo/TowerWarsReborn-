package io.github.pako25.towerWars.Arena.MobData;

import io.github.pako25.towerWars.Arena.MobType;
import io.github.pako25.towerWars.config.MobConfig;
import io.github.pako25.towerWars.util.GameRules;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 单种怪物的运行时状态：数值 + 进化 + 召唤加成。
 *
 * 设计意图：
 * 基础数值来自 MobConfig（mobConfig.yml 的类型化解析结果）；
 * 本类负责两种"动态涨数值"机制——
 * 1. 收入进化：召唤者收入越高，高级怪的价格/收入按幂次上涨（1.7/1.5），
 *    星级 ★ 数代表进化层数；
 * 2. 召唤加成：同种怪召唤次数越多，后续召唤的该种怪越强（血/速/治疗等
 *    按召唤次数线性爬升，上限 300 次），鼓励玩家专精一路怪。
 * 原版 getSummonedBonus 用 "//" 拼接文本，现改为返回逐行 List（隐式契约消除）。
 */
public class MobState {

    /** 召唤加成封顶次数：召唤超过 300 次后不再上涨 */
    private final int maxSummonCountForBonus = 300;
    private final MobConfig.MobDefinition definition;
    private final MobType mobType;
    private final boolean isAdvanced;
    private final MobStates mobStates;

    /** 收入进化到下一形态所需的累计收入 */
    private int incomeToPrestige;
    /** 解锁进化所需的收入门槛（基础价格 × 1/5） */
    private int incomeToUnlock;
    private MobType advancedForm;
    /** 本局内该种怪被召唤的次数（用于召唤加成） */
    private int summonCount = 0;

    public MobState(MobConfig.MobDefinition definition, MobType mobType, MobStates mobStates) {
        this.definition = definition;
        this.mobType = mobType;
        this.isAdvanced = definition.isAdvanced();
        this.mobStates = mobStates;
        if (!isAdvanced) {
            // 基础怪才有进化形态：预取进化后的价格作门槛，解锁线 = 基础价 × 0.2
            advancedForm = definition.evolution();
            if (advancedForm == null) {
                throw new IllegalStateException("怪物 '" + mobType.name() + "' 缺少 evolution 配置（基础怪必须声明进化形态）！");
            }
            incomeToPrestige = MobConfig.definition(advancedForm).cost();
            incomeToUnlock = (int) (definition.cost() * GameRules.BASIC_INCOME_UNLOCK_RATIO);
        }
    }

    // ========== 数值访问（全部经过进化/加成计算） ==========

    /** 商店图标材质 */
    public org.bukkit.Material getMaterial() {
        return definition.shopMaterial();
    }

    /** 显示名（配置的中文名） */
    public String getName() {
        return definition.displayName();
    }

    /** 召唤价格：收入进化后按 1.7^层数 上涨 */
    public int getCost(int playerIncome) {
        int incomeEvolution = getIncomeEvolution(playerIncome);
        if (incomeEvolution == 0) return definition.cost();
        return (int) (definition.cost() * Math.pow(GameRules.EVOLUTION_COST_FACTOR, incomeEvolution));
    }

    /** 生命：基础 × 力量增强倍率 + 召唤加成 */
    public int getHealth() {
        return (int) (definition.health() * mobStates.getPowerCreepHealthMultiplyer()) + getSummoningBonusHealth();
    }

    /** 速度：基础 + 召唤加成 */
    public double getSpeed() {
        return definition.speed() + getSummoningBonusSpeed();
    }

    /** 送怪收入：收入进化后按 1.5^层数 上涨 + 召唤加成 */
    public int getIncome(int playerIncome) {
        int incomeEvolution = getIncomeEvolution(playerIncome);
        if (incomeEvolution == 0) return definition.income();
        return (int) (definition.income() * Math.pow(GameRules.EVOLUTION_INCOME_FACTOR, incomeEvolution)) + getSummoningBonusIncome();
    }

    public boolean isAdvanced() {
        return isAdvanced;
    }

    public int getIncomeToPrestige() {
        return incomeToPrestige;
    }

    public int getIncomeToUnlock() {
        return incomeToUnlock;
    }

    public MobType getAdvancedForm() {
        return advancedForm;
    }

    public MobType getMobType() {
        return mobType;
    }

    public int getSummonCount() {
        return summonCount;
    }

    public void incrementSummon() {
        summonCount++;
    }

    /**
     * 收入进化层数：玩家收入每越过一档（门槛按 1.7 指数增长）就进化一层，
     * 封顶 6 层。基础怪不进化（返回 0）。
     */
    private int getIncomeEvolution(int playerIncome) {
        if (!isAdvanced) return 0;
        int evolutionByIncome = 0;
        int nextEvolutionRequirement = (int) (definition.cost() * GameRules.ADVANCED_NEXT_EVOLUTION_COST_FACTOR);
        while (playerIncome > nextEvolutionRequirement) {
            evolutionByIncome++;
            nextEvolutionRequirement = (int) (nextEvolutionRequirement * GameRules.EVOLUTION_COST_FACTOR);
        }
        return Math.min(evolutionByIncome, GameRules.MAX_EVOLUTION - 1); // 最多 ★★★★★★（6 层）
    }

    /** 进化星级文本（★ 串，GUI 怪图标名后缀） */
    public String getIncomeEvolutionText(int playerIncome) {
        int incomeEvolution = getIncomeEvolution(playerIncome);
        StringBuilder incomeEvolutionText = new StringBuilder(" ");
        for (int i = 0; i < incomeEvolution; i++) {
            incomeEvolutionText.append("★");
        }
        return incomeEvolutionText.toString();
    }

    /** 达到下一收入进化档所需的收入（已封顶返回 0） */
    public int getIncomeForNextEvolutionByIncome(int playerIncome) {
        if (!isAdvanced) return 0;
        int evolutionByIncome = getIncomeEvolution(playerIncome);
        if (evolutionByIncome >= GameRules.MAX_EVOLUTION - 1) return 0;

        int nextEvolutionRequirement = (int) (definition.cost() * GameRules.ADVANCED_NEXT_EVOLUTION_COST_FACTOR);
        while (playerIncome > nextEvolutionRequirement) {
            nextEvolutionRequirement = (int) (nextEvolutionRequirement * GameRules.EVOLUTION_COST_FACTOR);
        }
        return nextEvolutionRequirement;
    }

    // ========== 召唤加成（随召唤次数爬升） ==========

    /**
     * 召唤加成描述（逐行列表，GUI 直接展示）。
     * 设计意图：原版返回 "//" 拼接的字符串、由调用方再 split，隐式契约
     * 容易漏；这里直接产出结构化行列表。
     */
    public List<String> getSummonedBonus() {
        List<String> lines = new ArrayList<>();

        int bonusHealth = getSummoningBonusHealth();
        if (bonusHealth > 0) lines.add("+" + bonusHealth + " 额外生命");

        float bonusSpeed = getSummoningBonusSpeed();
        if (bonusSpeed > 0) lines.add("+" + formatDecimal(bonusSpeed) + " 额外速度");

        int bonusIncome = getSummoningBonusIncome();
        if (bonusIncome > 0) lines.add("+" + bonusIncome + " 额外收入");

        float bonusHealing = getSummoningBonusHealingFactor();
        if (bonusHealing > 0) lines.add("额外治疗: " + percent(bonusHealing) + "%");

        float bonusSlow = getSummoningBonusSlowFactor();
        if (bonusSlow > 0) lines.add("额外减速: " + percent(bonusSlow) + "%");

        float bonusBlind = getSummoningBonusBlindFactor();
        if (bonusBlind > 0) lines.add("额外失明: " + percent(bonusBlind) + "%");

        float bonusTpDistance = getSummoningBonusTpDistanceFactor();
        if (bonusTpDistance > 0) lines.add("额外传送距离: " + percent(bonusTpDistance) + "%");

        float bonusStunRange = getSummoningBonusStunRange();
        if (bonusStunRange > 0) lines.add("额外眩晕范围: " + percent(bonusStunRange) + "%");

        if (lines.isEmpty()) lines.add("暂无召唤加成");
        return lines;
    }

    /** 浮点保留两位小数（原版 BigDecimal 逻辑保留） */
    private String formatDecimal(float value) {
        BigDecimal bd = new BigDecimal(Float.toString(value));
        return bd.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** 小数 → 百分比整数 */
    private int percent(float factor) {
        return (int) (factor * 100);
    }

    public org.bukkit.entity.EntityType getEntityType() {
        return definition.entityType();
    }

    public boolean isSummonable() {
        return definition.summonable();
    }

    /** 召唤加成系数：召唤次数按 [0, 300] 归一化 */
    private float summonAmplifier(int divisor) {
        return (float) (Math.min(summonCount, maxSummonCountForBonus)) / (maxSummonCountForBonus * divisor);
    }

    // —— 各加成维度（原版每种怪一个专属方法，保留逐项语义） ——

    private int getSummoningBonusHealth() {
        if (!(mobType == MobType.WITHER_SKELETON || mobType == MobType.GOLD_ZOMBIE || mobType == MobType.DIAMOND_ZOMBIE))
            return 0;
        return (int) (definition.health() * summonAmplifier(1));
    }

    public float getSummoningBonusSpeed() {
        if (!(mobType == MobType.RAINBOW_SHEEP || mobType == MobType.RUNNING_IRON_GOLEM || mobType == MobType.MAD_COW))
            return 0;
        return (float) (definition.speed() * summonAmplifier(3));
    }

    public int getSummoningBonusIncome() {
        if (!(mobType == MobType.PIGGY_BANK || mobType == MobType.MAD_COW)) return 0;
        return (int) (definition.income() * summonAmplifier(3));
    }

    public float getSummoningBonusHealingFactor() {
        if (!(mobType == MobType.HIGH_PRIEST)) return 0;
        return summonAmplifier(2);
    }

    public float getSummoningBonusSlowFactor() {
        if (!(mobType == MobType.SPIDER_JOCKEY)) return 0;
        return summonAmplifier(2);
    }

    public float getSummoningBonusBlindFactor() {
        if (!(mobType == MobType.SQUID)) return 0;
        return summonAmplifier(2);
    }

    public float getSummoningBonusTpDistanceFactor() {
        if (!(mobType == MobType.ENDERMITE)) return 0;
        return summonAmplifier(1);
    }

    public float getSummoningBonusStunRange() {
        if (!(mobType == MobType.CHARGED_CREEPER)) return 0;
        return summonAmplifier(3);
    }
}

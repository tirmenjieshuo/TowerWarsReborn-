package io.github.pako25.towerWars.util;

/**
 * 全局游戏规则常量。
 *
 * 设计意图：
 * 原版代码把游戏节奏、经济、进化等数值散落在十几个类里（如 Track 里 lives=20、
 * Game 里 incomeTimeout=6、TWMob 里 cost*0.17），改动一处极易漏掉关联位置。
 * 本类将这些魔法数字统一收拢为具名常量，并附中文说明，作为"平衡数值的唯一事实来源"。
 * 注意：所有常量值与原版逐字相同，仅做"数字 → 具名常量"的机械替换，不改变任何游戏行为。
 */
public final class GameRules {

    private GameRules() {
        // 工具类，禁止实例化
    }

    // ========== 时间与节奏（Game 主循环） ==========

    /** 游戏主循环的 Bukkit 调度周期（服务器 tick）。原代码 runTaskTimer(plugin, 0, 5) */
    public static final int TICKER_INTERVAL_TICKS = 5;

    /**
     * 一秒 = 多少次主循环调用。tickCounter 每 4 次调用 +1 秒（即 4×5=20 服务器 tick）。
     * 全库所有 "tickCounter % 4 == 0" 的秒级判定都以此为准。
     */
    public static final int TICKS_PER_SECOND = 4;

    /** 每 X 秒向所有存活玩家发放一次被动收入（Game.incomeTimeout） */
    public static final int INCOME_INTERVAL_SECONDS = 6;

    /** 收入计时器初始值：开局 5 秒后发放第一笔收入 */
    public static final int INCOME_INITIAL_TIMER = 5;

    /** 一局最长时长（秒），超时判平局（Game.maxTime = 60*60 = 1 小时） */
    public static final int MAX_GAME_TIME_SECONDS = 60 * 60;

    /** 每 X 次主循环调用触发一次"怪物力量增强"（600 次调用 ≈ 150 秒） */
    public static final int POWER_CREEP_INTERVAL_TICKS = 600;

    /** 每次力量增强时怪物血量的乘性增长幅度（Track.powerCreep） */
    public static final float POWER_CREEP_MULTIPLIER = 0.13F;

    // ========== 开局经济（TWPlayer.gameStart） ==========

    /** 开局金币 */
    public static final int STARTING_COINS = 75;

    /** 开局被动收入（每 6 秒） */
    public static final int STARTING_INCOME = 5;

    /** 开局召唤库存（每种怪的可用召唤次数） */
    public static final int STARTING_STOCK = 30;

    /** 库存上限 */
    public static final int MAX_STOCK = 30;

    /** 库存随时间恢复：每过这么多 tickCounter 至少 +1 库存 */
    public static final int STOCK_RECOVERY_DIVISOR = 1200;

    /** 库存单次恢复上限（防止快速恢复） */
    public static final int STOCK_RECOVERY_CAP = 5;

    // ========== 赛道与胜负（Track） ==========

    /** 每名玩家初始生命（❤） */
    public static final int STARTING_LIVES = 20;

    /** 每名玩家最多可放置的塔数量 */
    public static final int MAX_TOWERS_PER_TRACK = 60;

    /** 死亡骑士（DEATH_RIDER）漏过时一次性扣除的生命数 */
    public static final int DEATH_RIDER_LIVES_LOST = 2;

    /** 出售塔的回收比例（塔价 × 此比例 = 退款） */
    public static final double SELL_REFUND_RATIO = 0.5;

    /** 击杀金币飘字 ArmorStand 的存在时长（服务器 tick），到期自动移除 */
    public static final int KILL_DISPLAY_TICKS = 20;

    // ========== 击杀奖励（TWMob） ==========

    /** 击杀怪物获得金币 = 怪物价格 × 此比例 */
    public static final double KILL_GOLD_RATIO = 0.17;

    /** 击杀怪物获得收入 = 怪物价格 × 此比例 */
    public static final double KILL_INCOME_RATIO = 0.02;

    // ========== 进化与经济指数（MobState） ==========

    /** 收入进化时下一形态的价格倍率 */
    public static final double EVOLUTION_COST_FACTOR = 1.7;

    /** 收入进化时下一形态的收入倍率 */
    public static final double EVOLUTION_INCOME_FACTOR = 1.5;

    /** 高级形态进一步进化所需的累计收入门槛 = 基础价格 × 此系数 */
    public static final double ADVANCED_NEXT_EVOLUTION_COST_FACTOR = 16;

    /** 收入进化的最大星级（★）上限 */
    public static final int MAX_EVOLUTION = 6;

    /** 进化产物召唤价格与收入之比：收入达到价格的 1/5 时解锁下一形态 */
    public static final double BASIC_INCOME_UNLOCK_RATIO = 0.2;

    // ========== 队列与开局（GameQueue/GameManager） ==========

    /** 一局所需的最少玩家人数 */
    public static final int MIN_PLAYERS_TO_START = 2;

    /** 开局倒计时时长（秒）。原代码硬编码了 "60 seconds left" 文本但实际只有 11 秒，本常量统一为事实值 */
    public static final int START_WAIT_SECONDS = 11;

    /** 一张地图最多支持的赛道数（= 可分配的轨道颜色数） */
    public static final int MAX_TRACK_SPAWNS = 6;
}

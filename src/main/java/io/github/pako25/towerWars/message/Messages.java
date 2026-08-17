package io.github.pako25.towerWars.message;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

import java.util.List;

/**
 * 全插件用户可见文本的唯一来源（集中消息层）。
 *
 * 设计意图：
 * 原版约 140 处消息散落在 20+ 个类里直接硬编码英文，汉化时逐文件替换极易遗漏，
 * 且同样的文案（如 "Not enough gold!"）在多处重复出现却写法不一。
 * 本类把所有聊天消息、GUI 标题、物品名、lore 统一收拢为静态工厂方法，
 * 业务代码只调用方法名，汉化与文案调整只需改这一处。
 *
 * 颜色规范（全局统一，仅翻译文案不改原配色）：
 *   金币/金额 = GOLD（金色），数值 = YELLOW（黄色），标签 = GOLD/AQUA（金色/青色），
 *   成功 = GREEN（绿色），错误/危险 = RED（红色），提示 = GRAY（灰色）。
 *
 * 嵌套类按业务模块划分：{@link Lobby} 大厅、{@link Game} 对局、{@link Tower} 塔、
 * {@link Gui} 界面、{@link Editor} 竞技场编辑器、{@link Sign} 加入牌、
 * {@link Cmd} 命令、{@link Misc} 杂项（飘字/侧边栏）。
 */
public final class Messages {

    private Messages() {
        // 工具类，禁止实例化
    }

    // =====================================================================
    // 底层工具
    // =====================================================================

    /**
     * 占位消息模板：把 {0} {1} ... 顺序替换为 args，再整体上色。
     * 设计意图：动态数值（数量、秒数、金额）统一走这一个入口，避免各处拼接。
     */
    public static Component msg(String template, NamedTextColor color, Object... args) {
        String result = template;
        for (int i = 0; i < args.length; i++) {
            result = result.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return Component.text(result, color);
    }

    /**
     * 赛道颜色 → 中文名。
     * 设计意图：原版在 Track/Sidebar/Game 三处各自做 GOLD→"ORANGE" 的特判后
     * 直接拼英文字母，这里统一映射为中文，消灭重复特判。
     */
    public static String colorName(NamedTextColor color) {
        // NamedTextColor 是普通类而非枚举，无法 switch，用相等判断链（写法直白、无反射开销）
        if (color == NamedTextColor.RED) return "红色";
        if (color == NamedTextColor.BLUE) return "蓝色";
        if (color == NamedTextColor.GREEN) return "绿色";
        if (color == NamedTextColor.YELLOW) return "黄色";
        if (color == NamedTextColor.GOLD) return "金色";
        if (color == NamedTextColor.AQUA) return "青色";
        if (color == NamedTextColor.DARK_RED) return "深红";
        if (color == NamedTextColor.DARK_BLUE) return "深蓝";
        if (color == NamedTextColor.DARK_GREEN) return "深绿";
        if (color == NamedTextColor.LIGHT_PURPLE) return "紫色";
        return color.toString();
    }

    // =====================================================================
    // 大厅与队列
    // =====================================================================

    public static final class Lobby {
        private Lobby() {
        }

        /** 玩家加入队列的系统提示（含当前人数） */
        public static Component joinedQueue(int players, int max) {
            return Component.text("你已加入队列（" + players + "/" + max + "）", NamedTextColor.YELLOW);
        }

        /** 玩家加入队列的广播（其他人收到：XXX 加入了队列） */
        public static Component broadcastJoinedQueue(Component displayName, int players, int max) {
            return displayName.append(Component.text(" 加入了队列（" + players + "/" + max + "）", NamedTextColor.YELLOW));
        }

        /** 玩家离开队列的广播 */
        public static Component broadcastLeftQueue(Component displayName, int players, int max) {
            return displayName.append(Component.text(" 离开了队列（" + players + "/" + max + "）", NamedTextColor.YELLOW));
        }

        /** 自己离开队列的系统提示 */
        public static Component leftQueue() {
            return Component.text("你已离开队列。", NamedTextColor.YELLOW);
        }

        /** 开局倒计时开始的提示（{0} 为实际秒数，原版硬编码 60 与真实 11 秒不符，已修正） */
        public static Component countdownStarted(int seconds) {
            return Component.text("开局倒计时已开始，还有 " + seconds + " 秒。", NamedTextColor.YELLOW);
        }

        /** 每秒钟的倒计时提示 */
        public static Component countdown(int seconds) {
            return Component.text("游戏将在 " + seconds + " 秒后开始。", NamedTextColor.YELLOW);
        }

        /** 倒计时被取消（人数不足） */
        public static Component countdownCanceled() {
            return Component.text("开局倒计时已取消。", NamedTextColor.YELLOW);
        }

        /** 已经在一个队列里 */
        public static Component alreadyInQueue() {
            return Component.text("你已经在队列中了！", NamedTextColor.RED);
        }

        /** 不在任何队列 */
        public static Component notInQueue() {
            return Component.text("你不在队列中！", NamedTextColor.RED);
        }

        /** 强制开局但人数不足 */
        public static Component notEnoughPlayersToForceStart() {
            return Component.text("玩家人数不足，无法强制开局！", NamedTextColor.RED);
        }

        /** 竞技场不可用（不存在/禁用/进行中） */
        public static Component arenaUnavailable() {
            return Component.text("该竞技场不可用。", NamedTextColor.RED);
        }

        /** 服务器内部错误 */
        public static Component serverError() {
            return Component.text("服务器发生错误，请尝试其他竞技场。", NamedTextColor.RED);
        }

        /** 离开游戏 = 弃权 */
        public static Component forfeitedBattle() {
            return Component.text("你离开了游戏，因此输掉了这场战斗。", NamedTextColor.RED);
        }

        /** 已经在游戏中 */
        public static Component alreadyInGame() {
            return Component.text("你已经在游戏中！", NamedTextColor.RED);
        }

        /** 排队进度条文本（含剩余秒数，仅倒计时未完成时显示） */
        public static Component bossBarProgress(String arenaName, int players, int max, int countdown, boolean countingDown) {
            String suffix = countingDown ? "  开始倒计时: " + countdown : "";
            return Component.text(arenaName + "  玩家: " + players + "/" + max + suffix, NamedTextColor.YELLOW);
        }

        /** 大厅物品：离开 */
        public static Component lobbyLeaveItemName() {
            return Component.text("离开", NamedTextColor.RED);
        }

        /** 大厅物品：统计 */
        public static Component lobbyStatsItemName() {
            return Component.text("统计", NamedTextColor.YELLOW);
        }

        /** 统计追踪已关闭（点大厅统计物品时的提示） */
        public static Component statTrackingDisabled() {
            return Component.text("统计追踪已被服务器关闭。", NamedTextColor.RED);
        }
    }

    // =====================================================================
    // 对局内消息
    // =====================================================================

    public static final class Game {
        private Game() {
        }

        /** 游戏开始 */
        public static Component started() {
            return Component.text("游戏开始", NamedTextColor.GREEN);
        }

        // ---- 玩法模式提示 ----

        /** 围攻：防守者开局提示 */
        public static Component defenderStarted() {
            return Component.text("你是防守者！守住你的赛道，别让进攻者的怪突破。限时 10 分钟，守住即获胜！", NamedTextColor.GOLD);
        }

        /** 围攻：进攻者开局提示 */
        public static Component attackerStarted() {
            return Component.text("你是进攻者！你的任务是送怪突破防守者的防线——把防守者生命打到 0 即获胜！", NamedTextColor.GOLD);
        }

        /** 围攻：进攻者试图放塔/升级时提示 */
        public static Component attackerCannotPlaceTower() {
            return Component.text("进攻方无法放塔：你只能送怪进攻！", NamedTextColor.RED);
        }

        /** 合作：波次开始播报 */
        public static Component waveStarted(int wave, int maxWaves) {
            return Component.text("第 " + wave + "/" + maxWaves + " 波怪物来袭！", NamedTextColor.GOLD);
        }

        /** 失败标题 */
        public static Component youLostTitle() {
            return Component.text("你输了", NamedTextColor.RED);
        }

        /** 胜利标题 */
        public static Component youWonTitle() {
            return Component.text("你赢了！", NamedTextColor.GOLD);
        }

        /** 你死了（赛道已关闭后的操作提示） */
        public static Component youAreDead() {
            return Component.text("你已经死了。", NamedTextColor.RED);
        }

        /** 被动收入到账 */
        public static Component passiveIncome(int amount) {
            return Component.text("你获得了 ", NamedTextColor.YELLOW)
                    .append(Component.text("+" + amount, NamedTextColor.GOLD))
                    .append(Component.text(" 金币（被动收入）！", NamedTextColor.YELLOW));
        }

        /** 怪物力量增强广播 */
        public static Component monstersGettingStronger() {
            return Component.text("怪物正在变强！", NamedTextColor.DARK_RED);
        }

        /** 漏怪送命：你给了对方 1 心（{0} 为对方赛道颜色中文名） */
        public static Component gaveLive(String colorName) {
            return Component.text("你给了 ", NamedTextColor.GREEN)
                    .append(Component.text("1", NamedTextColor.RED))
                    .append(Component.text("❤ 给 ", NamedTextColor.GREEN))
                    .append(Component.text(colorName, NamedTextColor.GOLD));
        }

        /** 偷到命：你从 {0}（{1} 怪）那里偷了 1 心 */
        public static Component stoleLive(String colorName, String mobName) {
            return Component.text("你从 ", NamedTextColor.GREEN)
                    .append(Component.text(colorName, NamedTextColor.GOLD))
                    .append(Component.text("（", NamedTextColor.GRAY))
                    .append(Component.text(mobName, NamedTextColor.YELLOW))
                    .append(Component.text("）手中偷了 1❤！", NamedTextColor.GREEN));
        }

        /** 某条赛道被淘汰的广播：{0} 已被淘汰！ */
        public static Component trackEliminated(String colorName) {
            return Component.text(colorName, NamedTextColor.GOLD)
                    .append(Component.text(" 已被淘汰！", NamedTextColor.RED));
        }
    }

    // =====================================================================
    // 塔相关
    // =====================================================================

    public static final class Tower {
        private Tower() {
        }

        /** 塔位已满 */
        public static Component maxTowersReached() {
            return Component.text("你已放置了最大数量的塔！", NamedTextColor.RED);
        }

        /** 放置成功：你放置了 {0}，花费 {1} 金币（{2}/{3}） */
        public static Component placedTower(Component towerName, int cost, int count, int max) {
            return Component.text("你放置了塔: ", NamedTextColor.GREEN)
                    .append(towerName)
                    .append(Component.text("，花费 ", NamedTextColor.GREEN))
                    .append(Component.text(cost, NamedTextColor.GOLD))
                    .append(Component.text(" 金币（", NamedTextColor.GREEN))
                    .append(Component.text(count + "/" + max, NamedTextColor.GRAY))
                    .append(Component.text("）", NamedTextColor.GREEN));
        }

        /** 金币不足 */
        public static Component notEnoughGold() {
            return Component.text("金币不足！", NamedTextColor.RED);
        }

        /** 塔头顶的"可升级"提示 */
        public static Component upgradeAvailableText() {
            return Component.text("✜", NamedTextColor.GREEN)
                    .append(Component.text(" 可升级！ ", NamedTextColor.WHITE))
                    .append(Component.text("✜", NamedTextColor.GREEN));
        }
    }

    // =====================================================================
    // GUI 界面
    // =====================================================================

    public static final class Gui {
        private Gui() {
        }

        // ---- 放置塔菜单 ----

        /** 放置塔菜单标题 */
        public static Component placeTowerInventoryTitle() {
            return Component.text("放置塔", NamedTextColor.WHITE);
        }

        /** 塔图标名：{0} 为塔基础名，{1} 为等级数字 */
        public static Component towerDisplayName(String towerName, int level) {
            return Component.text(towerName + " Lv." + level, NamedTextColor.YELLOW);
        }

        /** 塔图标名：专精形态（使用配置中的专精全名） */
        public static Component prestigeDisplayName(String prestigeName) {
            return Component.text(prestigeName, NamedTextColor.YELLOW);
        }

        /** lore 标签：伤害 */
        public static Component loreDamage() {
            return Component.text("伤害: ", NamedTextColor.AQUA);
        }

        /** lore 标签：射速 */
        public static Component loreReload() {
            return Component.text("射速: ", NamedTextColor.AQUA);
        }

        /** lore 标签：范围 */
        public static Component loreRange() {
            return Component.text("范围: ", NamedTextColor.AQUA);
        }

        /** lore 标签：溅射 */
        public static Component loreSplash() {
            return Component.text("溅射: ", NamedTextColor.AQUA);
        }

        /** lore 标签：价格 */
        public static Component loreCost() {
            return Component.text("价格: ", NamedTextColor.AQUA);
        }

        /** lore 标签：特殊效果 */
        public static Component loreSpecial() {
            return Component.text("特殊: ", NamedTextColor.LIGHT_PURPLE);
        }

        /** lore 数值行（黄色数值） */
        public static Component loreValue(Object value) {
            return Component.text(String.valueOf(value), NamedTextColor.YELLOW);
        }

        /** lore 多行文本（青色，逐行传入） */
        public static Component loreDescriptionLine(String line) {
            return Component.text(line, NamedTextColor.AQUA);
        }

        // ---- 召唤怪菜单 ----

        /** 召唤怪菜单标题 */
        public static Component summonMobInventoryTitle() {
            return Component.text("召唤怪", NamedTextColor.WHITE);
        }

        /** 怪图标名：{0} 怪名 + {1} 进化星级文本（★） */
        public static Component mobDisplayName(String mobName, String evolutionStars) {
            return Component.text(mobName + evolutionStars, NamedTextColor.AQUA);
        }

        /** lore 标签：生命 */
        public static Component loreHealth() {
            return Component.text("生命: ", NamedTextColor.AQUA);
        }

        /** lore 标签：速度 */
        public static Component loreSpeed() {
            return Component.text("速度: ", NamedTextColor.AQUA);
        }

        /** lore 标签：收入 */
        public static Component loreIncome() {
            return Component.text("收入: ", NamedTextColor.AQUA);
        }

        /** lore 标签：库存 */
        public static Component loreStock() {
            return Component.text("库存: ", NamedTextColor.AQUA);
        }

        /** lore 标签：已召唤 */
        public static Component loreSummoned() {
            return Component.text("已召唤: ", NamedTextColor.AQUA);
        }

        /** lore 标签：召唤加成 */
        public static Component loreSummonedBonus() {
            return Component.text("召唤加成: ", NamedTextColor.YELLOW);
        }

        /** 已达最大进化 */
        public static Component maxEvolutionReached() {
            return Component.text("你已达到最高进化形态！", NamedTextColor.YELLOW);
        }

        /** 还差 {0} 收入即可进化 */
        public static Component evolveAtIncome(int income) {
            return Component.text("收入达到 " + income + " 即可进化！", NamedTextColor.YELLOW);
        }

        // ---- 召唤加成（怪物 lore 内容） ----

        /** 召唤加成：+{0} 额外生命 */
        public static Component bonusHealth(int amount) {
            return Component.text("+" + amount + " 额外生命", NamedTextColor.GREEN);
        }

        /** 召唤加成：+{0} 额外速度 */
        public static Component bonusSpeed(String amount) {
            return Component.text("+" + amount + " 额外速度", NamedTextColor.GREEN);
        }

        /** 召唤加成：+{0} 额外收入 */
        public static Component bonusIncome(int amount) {
            return Component.text("+" + amount + " 额外收入", NamedTextColor.GREEN);
        }

        /** 召唤加成：额外治疗 {0}% */
        public static Component bonusHealing(int percent) {
            return Component.text("额外治疗: " + percent + "%", NamedTextColor.GREEN);
        }

        /** 召唤加成：额外减速 {0}% */
        public static Component bonusSlow(int percent) {
            return Component.text("额外减速: " + percent + "%", NamedTextColor.GREEN);
        }

        /** 召唤加成：额外失明 {0}% */
        public static Component bonusBlind(int percent) {
            return Component.text("额外失明: " + percent + "%", NamedTextColor.GREEN);
        }

        /** 召唤加成：额外传送距离 {0}% */
        public static Component bonusTpDistance(int percent) {
            return Component.text("额外传送距离: " + percent + "%", NamedTextColor.GREEN);
        }

        /** 召唤加成：额外眩晕范围 {0}% */
        public static Component bonusStunRange(int percent) {
            return Component.text("额外眩晕范围: " + percent + "%", NamedTextColor.GREEN);
        }

        /** 暂无召唤加成 */
        public static Component noBonusYet() {
            return Component.text("暂无召唤加成。", NamedTextColor.GRAY);
        }

        // ---- 升级菜单 ----

        /** 升级菜单标题 */
        public static Component upgradeInventoryTitle() {
            return Component.text("升级塔", NamedTextColor.WHITE);
        }

        /** lore 标签：DPS（每秒伤害，游戏通用缩写，保留英文术语） */
        public static Component loreDps() {
            return Component.text("DPS: ", NamedTextColor.AQUA);
        }

        /** 显示射程按钮 */
        public static Component showRange() {
            return Component.text("显示射程", NamedTextColor.GREEN);
        }

        /** 显示射程按钮说明 */
        public static Component showRangeLore() {
            return Component.text("用粒子显示塔的射程范围，持续 3 秒！", NamedTextColor.GREEN);
        }

        /** 统计按钮 */
        public static Component statsButton() {
            return Component.text("统计", NamedTextColor.YELLOW);
        }

        /** 统计 lore 标签：发射次数 */
        public static Component loreShots() {
            return Component.text("发射次数: ", NamedTextColor.AQUA);
        }

        /** 统计 lore 标签：总伤害 */
        public static Component loreDamageDealt() {
            return Component.text("总伤害: ", NamedTextColor.AQUA);
        }

        /** 统计 lore 标签：击杀数 */
        public static Component loreKills() {
            return Component.text("击杀数: ", NamedTextColor.AQUA);
        }

        /** 升级按钮提示：花费 {0} 金币升级 */
        public static Component upgradeFor(int cost) {
            return Component.text("花费 " + cost + " 金币升级", NamedTextColor.GOLD);
        }

        /** 升级按钮的操作提示 */
        public static Component loreClickToUpgrade() {
            return Component.text("点击 -> 升级到下一等级", NamedTextColor.GRAY);
        }

        /** 出售按钮的操作提示 */
        public static Component loreClickToSell() {
            return Component.text("点击 -> 出售", NamedTextColor.GRAY);
        }

        /** 专精按钮的操作提示 */
        public static Component loreClickToPrestige() {
            return Component.text("点击 -> 进阶为专精形态", NamedTextColor.GRAY);
        }

        /** 放置塔菜单物品的操作提示 */
        public static Component loreClickToPlace() {
            return Component.text("点击 -> 放置这座塔", NamedTextColor.GRAY);
        }

        /** 召唤怪菜单物品的操作提示 */
        public static Component loreClickToSummon() {
            return Component.text("点击 -> 送出这只怪物", NamedTextColor.GRAY);
        }

        /** 已满级 */
        public static Component maxLevel() {
            return Component.text("已达最高等级", NamedTextColor.RED);
        }

        /** 属性变化对比行：{0} >>> {1}（旧值/新值） */
        public static Component statChange(Object oldValue, Object newValue) {
            return Component.text(String.valueOf(oldValue), NamedTextColor.YELLOW)
                    .append(Component.text(" >>> ", NamedTextColor.GRAY))
                    .append(Component.text(String.valueOf(newValue), NamedTextColor.GREEN));
        }

        /** 出售按钮 */
        public static Component sellButton() {
            return Component.text("出售", NamedTextColor.GREEN);
        }

        /** 出售按钮说明：卖出 {0} 金币 */
        public static Component sellFor(int amount) {
            return Component.text("以 " + amount + " 金币出售！", NamedTextColor.GREEN);
        }

        /** 替换按钮 */
        public static Component replaceButton() {
            return Component.text("替换这座塔！", NamedTextColor.GOLD);
        }

        /** 替换按钮说明：出售它以购买新塔 */
        public static Component replaceLore() {
            return Component.text("出售它以购买新塔！", NamedTextColor.GOLD);
        }

        /** 专精按钮说明：每座塔只能选择一个专精 */
        public static Component onePrestigePerTower() {
            return Component.text("每座塔只能选择一种专精！", NamedTextColor.YELLOW);
        }

        // ---- 统计菜单 ----

        /** 统计面板标题 */
        public static Component statsInventoryTitle() {
            return Component.text("统计面板", NamedTextColor.WHITE);
        }

        /** 统计菜单物品名与对应中文 */
        public static Component statsGamesWon() {
            return Component.text("胜场", NamedTextColor.GREEN);
        }

        public static Component statsGamesLost() {
            return Component.text("败场", NamedTextColor.RED);
        }

        public static Component statsMobKills() {
            return Component.text("怪物击杀", NamedTextColor.DARK_GREEN);
        }

        public static Component statsTowersPlaced() {
            return Component.text("放置的塔", NamedTextColor.BLUE);
        }

        public static Component statsGoldSpent() {
            return Component.text("花费的金币", NamedTextColor.GOLD);
        }

        public static Component statsMobsSent() {
            return Component.text("送出的怪物", NamedTextColor.DARK_PURPLE);
        }

        /** 统计物品第一行：你的数值 */
        public static Component statsYourValue(Object value) {
            return Component.text("你: " + value, NamedTextColor.WHITE);
        }

        // ---- 游戏内物品 ----

        public static Component itemPlaceTower() {
            return Component.text("放置塔", NamedTextColor.GREEN);
        }

        /** 放置塔物品的用法说明（游戏开始发放时写入 lore） */
        public static Component itemPlaceTowerLore() {
            return Component.text("右键 -> 对准可放塔方块放置新塔", NamedTextColor.GRAY);
        }

        /** 放置塔物品的用法说明第 2 行（对准已有塔打开菜单） */
        public static Component itemPlaceTowerLore2() {
            return Component.text("右键 -> 对准已放置的塔打开塔菜单", NamedTextColor.GRAY);
        }

        /** 右键"放置塔"物品但准星没对准可放塔方块时提示怎么用 */
        public static Component rightClickOnPlaceBlock(Material material) {
            return Component.text("请右键对准可放塔方块（当前竞技场: " + material + "）才能打开放置菜单", NamedTextColor.RED);
        }

        /** 位置在轨道边界外，无法放塔 */
        public static Component outsideTrackBounds() {
            return Component.text("该位置在轨道边界外，无法放置塔", NamedTextColor.RED);
        }

        public static Component itemSummonMob() {
            return Component.text("召唤怪", NamedTextColor.GREEN);
        }

        /** 召唤怪物品的用法说明 */
        public static Component itemSummonMobLore() {
            return Component.text("右键 -> 打开召唤怪菜单送出怪物", NamedTextColor.GRAY);
        }

        public static Component itemUpgradeTower() {
            return Component.text("升级塔", NamedTextColor.GREEN);
        }

        /** 升级塔物品的用法说明 */
        public static Component itemUpgradeTowerLore() {
            return Component.text("右键 -> 对准塔身升级（金币自动扣除）", NamedTextColor.GRAY);
        }

        // ---- 侧边栏 ----

        public static Component sidebarTitle() {
            // 原版为纯黄色 "TOWERWARS"，不加粗保持视觉一致
            return Component.text("塔战", NamedTextColor.YELLOW);
        }

        public static Component sidebarTimer() {
            return Component.text("时间: ", NamedTextColor.YELLOW);
        }

        public static Component sidebarGold() {
            return Component.text("金币: ", NamedTextColor.YELLOW);
        }

        public static Component sidebarIncome() {
            return Component.text("收入: ", NamedTextColor.YELLOW);
        }

        public static Component sidebarNextIncome() {
            return Component.text("下次收入: ", NamedTextColor.YELLOW);
        }

        // ---- 击杀飘字（怪物头顶） ----

        /** 击杀奖励飘字：+{0} 金币（+{1} 收入） */
        public static Component killReward(int gold, int income) {
            return Component.text("+", NamedTextColor.GOLD)
                    .append(Component.text(gold, NamedTextColor.GOLD))
                    .append(Component.text(" 金币（+", NamedTextColor.YELLOW))
                    .append(Component.text(income, NamedTextColor.YELLOW))
                    .append(Component.text(" 收入）", NamedTextColor.YELLOW));
        }

        /** 怪物头顶生命条：拥有者名（赛道色）+ 剩余生命 ❤ */
        public static Component mobHealthLabel(String ownerName, int health, NamedTextColor ownerColor) {
            return Component.text(ownerName, ownerColor)
                    .append(Component.text(health, NamedTextColor.WHITE))
                    .append(Component.text(" ❤", NamedTextColor.RED));
        }
    }

    // =====================================================================
    // 竞技场编辑器
    // =====================================================================

    public static final class Editor {
        private Editor() {
        }

        public static Component instructionsBookTitle() {
            return Component.text("使用说明", NamedTextColor.GOLD);
        }

        /** 编辑器选项菜单标题 */
        public static Component editorOptionsMenuTitle() {
            return Component.text("编辑器选项", NamedTextColor.WHITE);
        }

        /** 材质设置成功 */
        public static Component materialSetSuccessfully() {
            return Component.text("材质设置成功。", NamedTextColor.GREEN);
        }

        public static Component editorOptionsItem() {
            return Component.text("编辑器选项", NamedTextColor.RED);
        }

        /** 编辑器指南针的用法说明（右键打开选项菜单） */
        public static Component editorOptionsItemLore() {
            return Component.text("右键 -> 打开编辑器选项菜单", NamedTextColor.GRAY);
        }

        /** 编辑器菜单：测试竞技场按钮 */
        public static Component testArenaItem() {
            return Component.text("测试竞技场", NamedTextColor.GOLD);
        }

        /** 编辑器菜单：测试按钮说明 */
        public static Component testArenaItemLore() {
            return Component.text("点击 -> 保存配置并单人测试开局", NamedTextColor.GRAY);
        }

        public static Component exitItem() {
            return Component.text("退出", NamedTextColor.RED);
        }

        public static Component discardItem() {
            return Component.text("放弃修改", NamedTextColor.RED);
        }

        /** 退出物品说明：左键保存 / 潜行+右键放弃 */
        public static Component exitLoreSave() {
            return Component.text("左键 -> 保存", NamedTextColor.GRAY);
        }

        public static Component exitLoreDiscard() {
            return Component.text("潜行 + 右键 -> 放弃", NamedTextColor.GRAY);
        }

        public static Component carefullyReadInstructions() {
            return Component.text("请仔细阅读使用说明！", NamedTextColor.RED);
        }

        public static Component someoneElseEditing() {
            return Component.text("已有其他玩家正在配置这个竞技场。", NamedTextColor.RED);
        }

        public static Component editingExistingArena() {
            return Component.text("该竞技场已存在，你正在编辑现有竞技场。", NamedTextColor.YELLOW);
        }

        public static Component creatingNewArena(String arenaName) {
            return Component.text("正在创建新竞技场 '" + arenaName + "'", NamedTextColor.YELLOW);
        }

        public static Component configurationSaved() {
            return Component.text("配置已保存。", NamedTextColor.GREEN);
        }

        public static Component changesDiscarded() {
            return Component.text("修改已放弃。", NamedTextColor.YELLOW);
        }

        public static Component quitEditingMode() {
            return Component.text("已退出编辑模式。", NamedTextColor.GREEN);
        }

        public static Component configurationInvalid() {
            return Component.text("你的配置无效。", NamedTextColor.RED);
        }

        public static Component boundsNotRectangle() {
            return Component.text("轨道边界无法构成矩形！", NamedTextColor.YELLOW);
        }

        public static Component pathTooShort() {
            return Component.text("每条路径至少需要 3 个路径点。", NamedTextColor.YELLOW);
        }

        public static Component pathPointsTooFar() {
            return Component.text("相邻路径点最多只能在一个坐标方向上不同。", NamedTextColor.YELLOW);
        }

        public static Component worldDoesNotExist() {
            return Component.text("你提供的世界不存在。", NamedTextColor.YELLOW);
        }

        public static Component addedTrackSpawn(org.bukkit.util.Vector location) {
            return Component.text("已添加新的轨道出生点: " + location, NamedTextColor.GREEN);
        }

        public static Component trackSpawnAlreadyExists() {
            return Component.text("这个位置已经是轨道出生点了！", NamedTextColor.YELLOW);
        }

        public static Component addedWaypoint() {
            return Component.text("已添加新的路径点。", NamedTextColor.GREEN);
        }

        public static Component removedPath(int waypoints) {
            return Component.text("已删除含 " + waypoints + " 个路径点的路径。", NamedTextColor.YELLOW);
        }

        public static Component selectedPath(int index, int waypoints) {
            return Component.text("已选择第 " + index + " 条路径（含 " + waypoints + " 个路径点）。", NamedTextColor.GREEN);
        }

        public static Component needTrackSpawnFirst() {
            return Component.text("请先配置一个轨道出生点！", NamedTextColor.YELLOW);
        }

        public static Component needPathFirst() {
            return Component.text("请先创建（并选择）一条路径！", NamedTextColor.YELLOW);
        }

        public static Component needWorldNameFirst() {
            return Component.text("请先设置世界名称！", NamedTextColor.RED);
        }

        public static Component worldMismatch(String worldName) {
            return Component.text("该位置所在世界与设置的 worldName 不一致！（" + worldName + "）", NamedTextColor.RED);
        }

        public static Component waypointAlreadyExists() {
            return Component.text("这里已经是路径点了。", NamedTextColor.YELLOW);
        }

        public static Component noPathsLeft() {
            return Component.text("没有可删除的路径了！", NamedTextColor.YELLOW);
        }

        public static Component removedTrackSpawn(org.bukkit.util.Vector location) {
            return Component.text("已删除轨道出生点: " + location, NamedTextColor.YELLOW);
        }

        public static Component removedTrackBound(org.bukkit.util.Vector location) {
            return Component.text("已删除轨道边界: " + location, NamedTextColor.YELLOW);
        }

        public static Component trackBoundAlreadyExists() {
            return Component.text("这个位置已经是轨道边界了！", NamedTextColor.YELLOW);
        }

        public static Component allTrackSpawnsSet() {
            return Component.text("你已经设置了全部轨道出生点！", NamedTextColor.YELLOW);
        }

        public static Component allTrackBoundsSet() {
            return Component.text("你已经设置了全部轨道边界！", NamedTextColor.YELLOW);
        }

        public static Component discardHint() {
            return Component.text("要放弃修改请潜行右键。", NamedTextColor.YELLOW);
        }

        public static Component answerFirst() {
            return Component.text("请先在聊天框中输入材质名！", NamedTextColor.YELLOW);
        }

        public static Component noTrackSpawnsLeft() {
            return Component.text("没有剩余的轨道出生点位置了！", NamedTextColor.RED);
        }

        public static Component noTrackBoundsLeft() {
            return Component.text("没有可删除的轨道边界了！", NamedTextColor.RED);
        }

        public static Component needOneTrackSpawnFirst() {
            return Component.text("请先配置至少一个轨道出生点！", NamedTextColor.RED);
        }

        public static Component needTrackSpawn() {
            return Component.text("请先设置一个轨道出生点！", NamedTextColor.RED);
        }

        public static Component waypointAddError() {
            return Component.text("添加路径点时发生数组越界错误，请联系开发者。", NamedTextColor.RED);
        }

        public static Component waypointRemoveError() {
            return Component.text("删除路径点时发生数组越界错误，请联系开发者。", NamedTextColor.RED);
        }

        public static Component errorOccurred() {
            return Component.text("发生错误。", NamedTextColor.RED);
        }

        public static Component invalidMaterial() {
            return Component.text("你提供的材质无效。", NamedTextColor.RED);
        }

        /** 聊天标题：请在聊天框输入材质名 */
        public static Component writeMaterialInChatTitle() {
            return Component.text("请在聊天框输入材质名", NamedTextColor.GOLD);
        }

        public static Component enterMaterialPrompt() {
            return Component.text("请在聊天框中输入你想用作塔放置方块的材质名。", NamedTextColor.GRAY);
        }

        // ---- 编辑器物品 ----

        public static Component trackSpawnConfigurator() {
            return Component.text("轨道出生点配置器", NamedTextColor.GOLD);
        }

        public static Component trackBoundsConfigurator() {
            return Component.text("轨道边界配置器", NamedTextColor.GOLD);
        }

        public static Component waypointConfigurator() {
            return Component.text("路径点配置器", NamedTextColor.GOLD);
        }

        public static Component pathSelectionTool() {
            return Component.text("路径选择工具", NamedTextColor.GOLD);
        }

        // ---- 编辑器选项菜单 ----

        public static Component configureWorldName() {
            return Component.text("配置世界名称", NamedTextColor.WHITE);
        }

        public static Component configureTrackSpawns() {
            return Component.text("配置轨道出生点", NamedTextColor.WHITE);
        }

        public static Component configureTrackBounds() {
            return Component.text("配置轨道边界", NamedTextColor.WHITE);
        }

        public static Component configureTrackPaths() {
            return Component.text("配置轨道路径", NamedTextColor.WHITE);
        }

        public static Component setPregameLobbySpawn() {
            return Component.text("设置开局前大厅位置", NamedTextColor.WHITE);
        }

        public static Component towerPlaceBlock() {
            return Component.text("塔放置方块", NamedTextColor.WHITE);
        }

        public static Component toggleEnabledStatus() {
            return Component.text("切换启用状态", NamedTextColor.WHITE);
        }

        public static Component enabled() {
            return Component.text("已启用", NamedTextColor.GREEN);
        }

        public static Component disabled() {
            return Component.text("已禁用", NamedTextColor.RED);
        }

        public static Component configured(int count, int max) {
            return Component.text("已配置: " + count + "/" + max, NamedTextColor.GOLD);
        }

        /** 编辑器物品 lore 系列（左键行为说明） */
        public static Component loreLeftClickSetNew() {
            return Component.text("左键 -> 设置新位置", NamedTextColor.GRAY);
        }

        public static Component loreLeftClickGetTools() {
            return Component.text("左键 -> 获取编辑工具", NamedTextColor.GRAY);
        }

        public static Component loreLeftClickSetNewAtCurrentWorld() {
            return Component.text("左键 -> 设为当前世界", NamedTextColor.GRAY);
        }

        public static Component loreLeftClickSetNewMaterial() {
            return Component.text("左键 -> 设置新材质", NamedTextColor.GRAY);
        }

        public static Component loreLeftClickGetEditStick() {
            return Component.text("左键 -> 获取编辑木棍", NamedTextColor.GRAY);
        }

        public static Component loreLeftClickGetEditFence() {
            return Component.text("左键 -> 获取编辑栅栏", NamedTextColor.GRAY);
        }

        public static Component loreLeftClickToSetNewTrackSpawn() {
            return Component.text("左键 -> 设置新的轨道出生点", NamedTextColor.GRAY);
        }

        public static Component loreLeftClickToSetNewTrackBound() {
            return Component.text("左键 -> 设置新的轨道边界", NamedTextColor.GRAY);
        }

        public static Component loreLeftClickToSetNewWaypoint() {
            return Component.text("左键 -> 设置新的路径点", NamedTextColor.GRAY);
        }

        public static Component loreLeftClickToChangePath() {
            return Component.text("左键 -> 切换路径", NamedTextColor.GRAY);
        }

        public static Component loreRightClickToRemoveSelectedPath() {
            return Component.text("右键 -> 删除选中的路径", NamedTextColor.GRAY);
        }

        public static Component loreWithPathSelectionTool() {
            return Component.text("配合路径选择工具使用！", NamedTextColor.GRAY);
        }
    }

    // =====================================================================
    // 加入牌
    // =====================================================================

    public static final class Sign {
        private Sign() {
        }

        /** 加入牌第 4 行：已禁用（第 1 行 [TOWERWARS] 是解析键，保持 ASCII） */
        public static Component signDisabled() {
            return Component.text("已禁用", NamedTextColor.RED);
        }

        /** 加入牌第 2 行：操作提示（规格化时写入） */
        public static Component signClickToJoin() {
            return Component.text("右键点击加入", NamedTextColor.DARK_AQUA);
        }

        /** 加入牌第 4 行：已满 */
        public static Component signFull() {
            return Component.text("已满", NamedTextColor.RED);
        }

        /** 加入牌第 4 行：排队人数 */
        public static Component signQueueCount(int players, int max) {
            return Component.text(players + "/" + max, NamedTextColor.GREEN);
        }

        /** 右键受保护的牌子 */
        public static Component signProtected() {
            return Component.text("这个牌子受 TowerWars 保护！", NamedTextColor.RED);
        }

        public static Component arenaDoesNotExist(String arenaName) {
            return Component.text("竞技场 \"" + arenaName + "\" 不存在。", NamedTextColor.RED);
        }

        public static Component noPermissionUseSign() {
            return Component.text("没有权限！如果你认为这是误判，请联系服务器管理员授予 towerwars.usesign 权限！", NamedTextColor.RED);
        }

        public static Component arenaFull() {
            return Component.text("竞技场已满！", NamedTextColor.RED);
        }

        public static Component arenaDisabled() {
            return Component.text("该竞技场已禁用！", NamedTextColor.RED);
        }
    }

    // =====================================================================
    // 命令
    // =====================================================================

    public static final class Cmd {
        private Cmd() {
        }

        public static Component usage() {
            return Component.text("用法: /towerwars <join|leave|forcestart|arena>", NamedTextColor.YELLOW);
        }

        public static Component noPermission() {
            return Component.text("没有权限。", NamedTextColor.RED);
        }

        public static Component onlyPlayers() {
            return Component.text("只有玩家可以使用该命令。", NamedTextColor.RED);
        }

        public static Component invalidArguments(String usage) {
            return Component.text("参数无效。用法: " + usage, NamedTextColor.RED);
        }

        public static Component notInGame() {
            return Component.text("你不在游戏中！", NamedTextColor.RED);
        }

        public static Component arenaDoesNotExist() {
            return Component.text("竞技场不存在。", NamedTextColor.RED);
        }

        public static Component arenaDisabled() {
            return Component.text("该竞技场已禁用。", NamedTextColor.RED);
        }

        public static Component arenaOccupied() {
            return Component.text("该竞技场已被占用。", NamedTextColor.RED);
        }

        public static Component inArenaConfigMode() {
            return Component.text("你正处于竞技场配置模式，请先退出该模式再试。", NamedTextColor.RED);
        }

        public static Component gameOngoing() {
            return Component.text("游戏正在进行中。", NamedTextColor.RED);
        }

        public static Component needToBeInGame() {
            return Component.text("你需要先在游戏中！", NamedTextColor.RED);
        }

        public static Component unknownSubcommand(String subcommand) {
            return Component.text("未知子命令: " + subcommand, NamedTextColor.RED);
        }

        public static Component cantOpenEditorInGame() {
            return Component.text("游戏中无法打开竞技场编辑器。", NamedTextColor.RED);
        }

        public static Component alreadyInEditingMode() {
            return Component.text("你已在编辑模式中，请先保存当前配置。", NamedTextColor.RED);
        }

        public static Component notInEditingMode() {
            return Component.text("你不在编辑模式中。", NamedTextColor.RED);
        }

        public static Component loadedArenas() {
            return Component.text("已加载的竞技场:", NamedTextColor.GOLD);
        }

        public static Component helpLine1() {
            return Component.text("使用 /towerwars join <竞技场> 加入游戏", NamedTextColor.GRAY);
        }

        public static Component helpLine2() {
            return Component.text("使用 /towerwars leave 离开游戏", NamedTextColor.GRAY);
        }

        public static Component helpLine3() {
            return Component.text("使用 /towerwars arena list 查看可用竞技场", NamedTextColor.GRAY);
        }

        public static Component debugStickName() {
            return Component.text("调试木棍", NamedTextColor.GOLD);
        }

        /** 调试木棍的用法说明（右键查看塔归属） */
        public static Component debugStickLore() {
            return Component.text("右键 -> 查看视线内实体/方块属于哪座塔", NamedTextColor.GRAY);
        }

        /** 未知玩法模式提示 */
        public static Component invalidMode(String raw) {
            return Component.text("未知玩法模式: '" + raw + "'（可用: classic / siege / coop）", NamedTextColor.RED);
        }

        /** 单人测试模式开局提示 */
        public static Component testModeStarted(String arenaName) {
            return Component.text("已进入测试模式：单人测试竞技场 \"" + arenaName + "\"。", NamedTextColor.GOLD)
                    .appendNewline()
                    .append(Component.text("送出的怪会从你自己的赛道出发，用于验证路径与防守。用 /towerwars leave 结束测试。", NamedTextColor.GRAY));
        }

        // ---- 竞技场状态词（arena list 显示） ----

        public static Component arenaStatusEnabled() {
            return Component.text("已启用", NamedTextColor.GREEN);
        }

        public static Component arenaStatusDisabled() {
            return Component.text("已禁用", NamedTextColor.RED);
        }

        public static Component arenaStatusFree() {
            return Component.text("空闲", NamedTextColor.GREEN);
        }

        public static Component arenaStatusOccupied() {
            return Component.text("进行中", NamedTextColor.RED);
        }
    }

    // =====================================================================
    // 调试工具（DebugStickListener 等）
    // =====================================================================

    public static final class Debug {
        private Debug() {
        }

        public static Component noEntityHits() {
            return Component.text("未命中实体，开始检查方块。", NamedTextColor.GRAY);
        }

        public static Component entityBelongsToTower(Object tower) {
            return Component.text("实体属于塔: " + tower, NamedTextColor.GREEN);
        }

        public static Component entityDoesNotBelongToTower() {
            return Component.text("实体不属于任何塔。", NamedTextColor.RED);
        }

        public static Component blockBelongsToTower(Object tower) {
            return Component.text("方块属于塔: " + tower, NamedTextColor.GREEN);
        }

        public static Component blockNotOccupied() {
            return Component.text("该方块没有被塔占用。", NamedTextColor.RED);
        }

        public static Component teslaDebugInfo(Object info) {
            return Component.text("特斯拉塔调试信息: " + info, NamedTextColor.GRAY);
        }
    }
}

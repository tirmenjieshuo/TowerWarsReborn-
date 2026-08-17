package io.github.pako25.towerWars.Arena;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * 怪物出生队列：延迟 2 tick（0.5 秒）出怪。
 *
 * 设计意图：
 * 被召唤的怪不立即入场——先入队，等 2 个 tick 后再真正"激活"（放进
 * activeMobs 参与结算）。这个延迟让同一批次召唤的怪错开生成瞬间，
 * 也保证 TWMob 构造完成（含导航初始化）后才进入主循环。
 * status > 1 即出队：status 每 tick +1，第 2 次 tick 后出队。
 */
public class MobQueue {

    private final ArrayList<QueuedMob> mobList = new ArrayList<>();

    /** 入队：包装成带延迟计数的队列项 */
    public void add(TWMob mob) {
        mobList.add(new QueuedMob(mob));
    }

    /** 每 tick 调用：到期的怪出队并返回（未到期的继续等待） */
    public ArrayList<TWMob> tick() {
        ArrayList<TWMob> out = new ArrayList<>();
        Iterator<QueuedMob> iterator = mobList.iterator();
        while (iterator.hasNext()) {
            QueuedMob queuedMob = iterator.next();
            queuedMob.increaseStatus();
            if (queuedMob.status > 1) { // 第 2 次 tick 到期
                out.add(queuedMob.mob);
                iterator.remove();
            }
        }
        return out;
    }

    public int size() {
        return mobList.size();
    }
}

/** 队列项：TWMob + 延迟计数（status=0 入队，status>1 出队） */
class QueuedMob {
    public final TWMob mob;
    public int status = 0;

    public QueuedMob(TWMob mob) {
        this.mob = mob;
    }

    public void increaseStatus() {
        status++;
    }
}

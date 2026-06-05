package com.danmaku.flow.engine

import com.danmaku.flow.model.ActiveDanmaku

/**
 * 活动弹幕存储
 *
 * 管理当前帧中正在显示或即将显示的弹幕。
 * 对应融合方案 4.6 节和 9.1.1 第 4 步
 */
class ActiveDanmakuStore {

    private val active = mutableListOf<ActiveDanmaku>()

    /** 添加一条活动弹幕 */
    fun add(item: ActiveDanmaku) {
        active.add(item)
    }

    /** 移除已离场的弹幕 */
    fun removeExpired(currentMs: Long) {
        active.removeAll { it.endMs <= currentMs }
    }

    /** 在 seek 后清空全部 */
    fun clear() {
        active.clear()
    }

    /** 获取当前活动弹幕的只读快照 */
    fun snapshot(): List<ActiveDanmaku> = active.toList()

    /** 当前活动数量 */
    fun size(): Int = active.size

    /** 是否存在某 ID */
    fun contains(id: Long): Boolean = active.any { it.item.id == id }
}

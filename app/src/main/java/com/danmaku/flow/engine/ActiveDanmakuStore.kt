package com.danmaku.flow.engine

import com.danmaku.flow.model.ActiveDanmaku
import com.danmaku.flow.model.DanmakuItem
import com.danmaku.flow.model.DanmakuType
import com.danmaku.flow.model.RenderStyleSnapshot

/**
 * 活动弹幕存储（P1 对象池版）
 *
 * 管理当前帧中正在显示或即将显示的弹幕。
 * P1 升级：
 * - 对象池复用 ActiveDanmaku，减少 GC 压力
 * - 低内存时主动缩减池大小
 *
 * 对应融合方案 4.6 节和 9.2 第 4 项
 */
class ActiveDanmakuStore {

    private val active = mutableListOf<ActiveDanmaku>()

    /** 对象池 */
    private val pool = mutableListOf<ActiveDanmaku>()
    private val maxPoolSize = 64

    /** 添加一条活动弹幕（优先从池中取） */
    fun add(item: ActiveDanmaku) {
        active.add(item)
    }

    /**
     * 从对象池获取或创建一个 ActiveDanmaku
     * 减少 new 分配次数
     */
    fun obtain(
        item: DanmakuItem,
        startMs: Long,
        endMs: Long,
        trackIndex: Int,
        styleSnapshot: RenderStyleSnapshot
    ): ActiveDanmaku {
        if (pool.isNotEmpty()) {
            val recycled = pool.removeAt(pool.size - 1)
            recycled.item = item
            recycled.startMs = startMs
            recycled.endMs = endMs
            recycled.trackIndex = trackIndex
            recycled.styleSnapshot = styleSnapshot
            return recycled
        }
        return ActiveDanmaku(item, startMs, endMs, trackIndex, styleSnapshot)
    }

    /** 归还到对象池 */
    private fun recycle(danmaku: ActiveDanmaku) {
        if (pool.size < maxPoolSize) {
            pool.add(danmaku)
        }
    }

    /** 移除已离场的弹幕（归还到对象池） */
    fun removeExpired(currentMs: Long) {
        val iterator = active.iterator()
        while (iterator.hasNext()) {
            val item = iterator.next()
            if (item.endMs <= currentMs) {
                recycle(item)
                iterator.remove()
            }
        }
    }

    /** 在 seek 后清空全部（归还到对象池） */
    fun clear() {
        for (item in active) {
            recycle(item)
        }
        active.clear()
    }

    /** 获取当前活动弹幕的只读快照 */
    fun snapshot(): List<ActiveDanmaku> = active.toList()

    /** 当前活动数量 */
    fun size(): Int = active.size

    /** 是否存在某 ID */
    fun contains(id: Long): Boolean = active.any { it.item.id == id }

    /**
     * 移除优先级最低的一条活动弹幕（方案 4.5 节裁剪策略）
     *
     * 裁剪顺序：
     * 1. 保留高优先级弹幕
     * 2. 保留 Special 类型弹幕（字幕池/特殊池）
     * 3. 在剩余普通弹幕中移除优先级最低的
     *
     * @return 被移除的弹幕，null 表示没有可移除的
     */
    fun removeLowestPriority(): ActiveDanmaku? {
        if (active.isEmpty()) return null

        // 找到非 Special 类型中优先级最低的
        var lowestIdx = -1
        var lowestPriority = Int.MAX_VALUE

        for (i in active.indices) {
            val item = active[i]
            // 跳过 Special 类型（保留字幕池/特殊池）
            if (item.item.type == com.danmaku.flow.model.DanmakuType.Special) continue
            if (item.item.priority < lowestPriority) {
                lowestPriority = item.item.priority
                lowestIdx = i
            }
        }

        if (lowestIdx < 0) return null

        val removed = active.removeAt(lowestIdx)
        recycle(removed)
        return removed
    }

    /**
     * 低内存时缩减对象池
     * 系统回调 onTrimMemory(TRIM_MEMORY_RUNNING_LOW) 时调用
     */
    fun trimPool() {
        val keepSize = maxPoolSize / 4
        while (pool.size > keepSize) {
            pool.removeAt(pool.size - 1)
        }
    }

    /** 池中缓存的对象数量 */
    fun poolSize(): Int = pool.size
}

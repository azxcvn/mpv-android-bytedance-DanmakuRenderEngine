package com.danmaku.flow.parser

import com.danmaku.flow.model.DanmakuItem

/**
 * 时间索引
 *
 * 按 1000ms 建 bucket，做到近似 O(1) 定位时间窗口。
 * 对应融合方案 4.2 节
 */
class DanmakuTimeIndex(
    private val sortedItems: List<DanmakuItem>
) {
    companion object {
        /** bucket 大小（毫秒） */
        const val BUCKET_SIZE_MS = 1000L
    }

    /** bucket index -> 该 bucket 内的弹幕列表 */
    private val buckets: Map<Long, List<DanmakuItem>>

    init {
        buckets = sortedItems.groupBy { it.timelineMs / BUCKET_SIZE_MS }
    }

    /**
     * 查找 [startMs, endMs) 时间窗口内的弹幕
     */
    fun query(startMs: Long, endMs: Long): List<DanmakuItem> {
        val startBucket = startMs / BUCKET_SIZE_MS
        val endBucket = (endMs - 1) / BUCKET_SIZE_MS
        val result = mutableListOf<DanmakuItem>()
        for (bucket in startBucket..endBucket) {
            buckets[bucket]?.let { items ->
                for (item in items) {
                    if (item.timelineMs in startMs until endMs) {
                        result.add(item)
                    }
                }
            }
        }
        return result
    }

    /** 总弹幕数 */
    val size: Int get() = sortedItems.size
}

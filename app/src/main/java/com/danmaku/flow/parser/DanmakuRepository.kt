package com.danmaku.flow.parser

import com.danmaku.flow.model.DanmakuItem

/**
 * 弹幕数据仓库
 *
 * 持有解析后的弹幕数据和时间索引，供引擎查询。
 */
class DanmakuRepository {

    private var items: List<DanmakuItem> = emptyList()
    private var index: DanmakuTimeIndex? = null

    /** 加载弹幕列表并建立索引 */
    fun load(newItems: List<DanmakuItem>) {
        items = newItems.sortedBy { it.timelineMs }
        index = DanmakuTimeIndex(items)
    }

    /** 查询时间窗口 */
    fun query(startMs: Long, endMs: Long): List<DanmakuItem> {
        return index?.query(startMs, endMs) ?: emptyList()
    }

    /** 总弹幕数 */
    fun size(): Int = items.size

    /** 清空 */
    fun clear() {
        items = emptyList()
        index = null
    }

    fun isEmpty(): Boolean = items.isEmpty()
}

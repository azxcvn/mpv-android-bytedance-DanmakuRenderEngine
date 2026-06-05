package com.danmaku.flow.engine

import com.danmaku.flow.model.ActiveDanmaku
import com.danmaku.flow.model.DanmakuItem
import com.danmaku.flow.model.DanmakuType
import com.danmaku.flow.model.GlobalDanmakuStyle
import com.danmaku.flow.model.RenderStyleSnapshot
import com.danmaku.flow.model.TrackState

/**
 * 轨道分配器
 *
 * 为新弹幕分配可用轨道，采用「最早可用」策略（P0 基础版）。
 * 对应融合方案 4.4 节和 9.1.1 第 4 步
 */
class TrackAllocator(private val trackCount: Int) {

    private val tracks = Array(trackCount) { TrackState(trackIndex = it) }

    /**
     * 为滚动弹幕分配轨道
     * @param currentMs 当前时间
     * @param itemWidth 该弹幕宽度 px（用于预测追尾）
     * @param screenWidth 屏幕宽度
     * @param scrollDurationMs 滚动时长
     * @return 轨道索引，-1 表示无可用轨道
     */
    fun allocateScroll(
        currentMs: Long,
        itemWidth: Float,
        screenWidth: Int,
        scrollDurationMs: Long
    ): Int {
        for (track in tracks) {
            if (currentMs >= track.occupiedUntilMs) {
                track.occupiedUntilMs = currentMs + scrollDurationMs
                return track.trackIndex
            }
        }
        return -1 // 无可用轨道
    }

    /**
     * 为固定弹幕分配轨道
     * @param currentMs 当前时间
     * @param displayDurationMs 显示时长
     * @param fromTop true=从顶部开始分配，false=从底部
     */
    fun allocateFixed(currentMs: Long, displayDurationMs: Long, fromTop: Boolean): Int {
        val start = if (fromTop) 0 else trackCount - 1
        val end = if (fromTop) trackCount else -1
        val step = if (fromTop) 1 else -1

        var i = start
        while (i != end) {
            if (currentMs >= tracks[i].occupiedUntilMs) {
                tracks[i].occupiedUntilMs = currentMs + displayDurationMs
                return tracks[i].trackIndex
            }
            i += step
        }
        return -1
    }

    /** 在 seek 后重置所有轨道 */
    fun reset() {
        for (track in tracks) {
            track.occupiedUntilMs = 0L
            track.lastItemId = null
        }
    }
}

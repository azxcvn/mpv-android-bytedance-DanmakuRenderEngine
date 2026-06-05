package com.danmaku.flow.engine

import com.danmaku.flow.model.DensityMode
import com.danmaku.flow.model.TrackState

/**
 * 轨道分配器（P1 轨道压力模型）
 *
 * 升级为融合方案 4.4 节描述的「轨道压力模型 + 预测占用窗口」。
 * 替换 P0 的「最早可用」策略，引入：
 * - 追尾安全判断：根据新旧弹幕速度差预测是否会追尾
 * - 密度模式感知：三档模式下安全间距不同
 * - 字幕安全区：底部预留区域避免遮挡 mpv 字幕
 *
 * 对应融合方案 9.2 第 1、2、5 项
 */
class TrackAllocator(
    private val trackCount: Int,
    private val isBottom: Boolean = false
) {

    private val tracks = Array(trackCount) { TrackState(trackIndex = it) }
    private val scrollTrackOrder = MutableList(trackCount) { it }

    /** 密度模式（影响安全间距） */
    private var densityMode = DensityMode.Balanced

    /** 底部字幕安全区高度（px），仅对底部轨道有效 */
    private var subtitleSafeHeightPx = 0f

    /** 滚动轨道分配起始偏移（每次分配后递增，避免永远从顶部开始填） */
    private var scrollStartOffset = (0 until trackCount).random()

    /** 当前允许参与分配的轨道数量上限，默认全部开放。 */
    private var activeTrackLimit = trackCount

    init {
        reshuffleScrollTracks()
    }

    companion object {
        /** 各密度模式对应的安全间距系数（相对于屏幕宽度） */
        private const val SAFETY_MARGIN_STRICT = 0.12f
        private const val SAFETY_MARGIN_BALANCED = 0.06f
        private const val SAFETY_MARGIN_CROWDED = 0.01f

        /** 底部字幕安全区默认高度 dp */
        private const val SUBTITLE_SAFE_HEIGHT_DP = 60f
    }

    /**
     * 设置密度模式
     */
    fun setDensityMode(mode: DensityMode) {
        densityMode = mode
    }

    /**
     * 设置字幕安全区高度
     * @param heightPx 安全区高度（px），0 表示不预留
     */
    fun setSubtitleSafeHeight(heightPx: Float) {
        subtitleSafeHeightPx = heightPx
    }

    fun setActiveTrackLimit(limit: Int) {
        activeTrackLimit = limit.coerceIn(1, trackCount)
        scrollStartOffset %= activeTrackLimit
    }

    /**
     * 为滚动弹幕分配轨道（压力模型版）
     *
     * 不再简单取最早可用轨道，而是：
     * 1. 遍历所有轨道，计算「压力得分」
     * 2. 压力得分 = 预测追尾风险 + 等待时长
     * 3. 选择压力最低的轨道
     * 4. 如果所有轨道都有追尾风险，返回 -1
     *
     * @param currentMs 当前时间
     * @param newItemWidth 新弹幕宽度 px
     * @param screenWidth 屏幕宽度 px
     * @param scrollDurationMs 新弹幕滚动时长 ms
     * @return 轨道索引，-1 表示无可用轨道
     */
    fun allocateScroll(
        currentMs: Long,
        newItemWidth: Float,
        screenWidth: Int,
        scrollDurationMs: Long
    ): Int {
        val safetyFraction = when (densityMode) {
            DensityMode.Strict -> SAFETY_MARGIN_STRICT
            DensityMode.Balanced -> SAFETY_MARGIN_BALANCED
            DensityMode.Crowded -> SAFETY_MARGIN_CROWDED
        }
        val safetyGapPx = screenWidth * safetyFraction

        var bestTrack = -1
        var bestScore = Float.MAX_VALUE
        val activeTrackCount = effectiveScrollTrackCount()
        var fallbackTrack = -1
        var fallbackScore = Float.MAX_VALUE

        // 从轮转偏移开始遍历，避免永远从顶部开始填（解决"递增趋势"问题）
        for (offset in 0 until activeTrackCount) {
            val orderIndex = (scrollStartOffset + offset) % activeTrackCount
            val trackIndex = scrollTrackOrder[orderIndex]
            val track = tracks[trackIndex]
            // 轨道完全空闲
            if (currentMs >= track.occupiedUntilMs) {
                bestTrack = track.trackIndex
                bestScore = -1f // 空闲轨道最优
                break
            }

            // 轨道被占用，计算追尾风险
            val lastExitMs = track.lastItemExitTimeMs
            val lastWidth = track.lastItemWidthPx

            val safeAllocatableMs = lastExitMs - (scrollDurationMs * safetyGapPx / (screenWidth + lastWidth)).toLong()

            if (currentMs >= safeAllocatableMs) {
                // 安全，可以分配
                val pressure = 0f
                if (pressure < bestScore) {
                    bestScore = pressure
                    bestTrack = track.trackIndex
                }
            } else {
                // 有追尾风险，但在拥挤模式下尽量保留轨道，不轻易拒绝弹幕
                if (densityMode == DensityMode.Crowded) {
                    val waitMs = (safeAllocatableMs - currentMs).coerceAtLeast(0L)
                    val pressure = waitMs.toFloat() / scrollDurationMs.coerceAtLeast(1L)
                    if (pressure < fallbackScore) {
                        fallbackScore = pressure
                        fallbackTrack = track.trackIndex
                    }
                }
            }
        }

        if (bestTrack < 0 && densityMode == DensityMode.Crowded) {
            bestTrack = fallbackTrack
        }

        // 找到了可分配的轨道
        if (bestTrack >= 0) {
            tracks[bestTrack].occupiedUntilMs = currentMs + scrollDurationMs
            tracks[bestTrack].lastItemWidthPx = newItemWidth
            tracks[bestTrack].lastItemExitTimeMs = currentMs + scrollDurationMs
            // 轮转：沿打散后的顺序继续遍历，避免持续从屏幕顶部向下填充
            val bestOrderIndex = scrollTrackOrder.indexOf(bestTrack).coerceAtLeast(0)
            scrollStartOffset = (bestOrderIndex + 1) % activeTrackCount
        }

        return bestTrack
    }

    /**
     * 为固定弹幕分配轨道
     *
     * @param currentMs 当前时间
     * @param displayDurationMs 显示时长
     * @param fromTop true=从顶部开始分配，false=从底部
     * @return 轨道索引，-1 表示无可用轨道
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

    /**
     * 获取字幕安全区偏移量（底部轨道专用）
     *
     * 底部轨道的弹幕应向上偏移，避免遮挡 mpv 原生字幕。
     * @return Y 轴偏移量 px
     */
    fun getSubtitleSafeOffset(): Float {
        return if (isBottom && subtitleSafeHeightPx > 0f) subtitleSafeHeightPx else 0f
    }

    /** 在 seek 后重置所有轨道 */
    fun reset() {
        for (track in tracks) {
            track.occupiedUntilMs = 0L
            track.lastItemId = null
            track.lastItemWidthPx = 0f
            track.lastItemExitTimeMs = 0L
        }
        reshuffleScrollTracks()
    }

    private fun reshuffleScrollTracks() {
        scrollTrackOrder.shuffle()
        scrollStartOffset = (0 until trackCount).random()
    }

    private fun effectiveScrollTrackCount(): Int {
        return activeTrackLimit.coerceIn(1, trackCount)
    }
}

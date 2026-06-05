package com.danmaku.flow.engine

import com.danmaku.flow.model.ActiveDanmaku
import com.danmaku.flow.model.DanmakuItem
import com.danmaku.flow.model.DanmakuRenderItem
import com.danmaku.flow.model.DanmakuType
import com.danmaku.flow.model.DensityMode
import com.danmaku.flow.model.GlobalDanmakuStyle
import com.danmaku.flow.model.RenderStyleSnapshot
import com.danmaku.flow.bridge.api.PlayerEvent
import com.danmaku.flow.parser.DanmakuRepository

/**
 * 弹幕时间轴引擎（P1 工程强化版）
 *
 * P0 基础上增加：
 * 1. 轨道压力模型（替换最早可用策略）
 * 2. 预测占用窗口 + 追尾安全判断
 * 3. 密度控制三档模式
 * 4. 对象池复用（通过 ActiveDanmakuStore.obtain()）
 * 5. 字幕安全区（底部轨道预留）
 * 6. 帧预算感知降级
 * 7. 配置渐进式生效
 *
 * 对应融合方案 9.2 第 1~7 项
 */
class DanmakuTimelineEngine : DanmakuEngine {

    companion object {
        /** 小幅时间抖动容忍值，避免偶发回退被误判为 seek */
        const val CLOCK_JITTER_TOLERANCE_MS = 80L
        /** seek 后重建跨度 ms（方案 4.6 节建议 3000ms） */
        const val SEEK_REBUILD_MS = 3000L
        /** 滚动弹幕默认停留时长 ms */
        const val SCROLL_DURATION_MS = 4000L
        /** 固定弹幕默认停留时长 ms */
        const val FIXED_DURATION_MS = 3000L
        /** 滚动轨道数 */
        const val SCROLL_TRACK_COUNT = 28
        /** 顶部固定轨道数 */
        const val TOP_TRACK_COUNT = 6
        /** 底部固定轨道数 */
        const val BOTTOM_TRACK_COUNT = 6
        /** 底部字幕安全区高度 dp */
        const val SUBTITLE_SAFE_HEIGHT_DP = 60f
    }

    private var repository: DanmakuRepository? = null
    private var screenWidth = 1080
    private var screenHeight = 1920
    private var densityDpi = 2.0f // 粗略 dp->px 系数

    private lateinit var scrollAllocator: TrackAllocator
    private lateinit var topAllocator: TrackAllocator
    private lateinit var bottomAllocator: TrackAllocator
    private val activeStore = ActiveDanmakuStore()

    private var currentStyle = GlobalDanmakuStyle()
    private var isSeeking = false
    private var lastPositionMs = -1L
    private var seekPositionMs = 0L

    // P1: 密度模式
    private var densityMode = DensityMode.Balanced

    // P1: 帧预算感知
    private var isDegraded = false

    private val allocatorsInitialized: Boolean
        get() = ::scrollAllocator.isInitialized && ::topAllocator.isInitialized && ::bottomAllocator.isInitialized

    override fun initialize(repository: DanmakuRepository, screenWidth: Int, screenHeight: Int) {
        this.repository = repository
        this.screenWidth = screenWidth
        this.screenHeight = screenHeight

        scrollAllocator = TrackAllocator(SCROLL_TRACK_COUNT, isBottom = false)
        topAllocator = TrackAllocator(TOP_TRACK_COUNT, isBottom = false)
        bottomAllocator = TrackAllocator(BOTTOM_TRACK_COUNT, isBottom = true)

        // 底部轨道设置字幕安全区
        val safePx = SUBTITLE_SAFE_HEIGHT_DP * densityDpi
        bottomAllocator.setSubtitleSafeHeight(safePx)
        applyStyleToAllocators()

        activeStore.clear()
        lastPositionMs = -1L
        isSeeking = false
    }

    override fun setScreenSize(width: Int, height: Int) {
        if (width > 0 && height > 0 && (width != screenWidth || height != screenHeight)) {
            screenWidth = width
            screenHeight = height
        }
    }

    override fun onClockTick(positionMs: Long) {
        val repo = repository ?: return

        if (!isSeeking && lastPositionMs >= 0L && positionMs + CLOCK_JITTER_TOLERANCE_MS < lastPositionMs) {
            isSeeking = true
        }

        if (!isSeeking && lastPositionMs >= 0L && positionMs < lastPositionMs) {
            lastPositionMs = positionMs
            return
        }

        // 首帧、seek 后或时间倒退时，按当前时间点重建可见活动窗口，避免未来弹幕提前占轨。
        if (isSeeking || lastPositionMs < 0 || positionMs < lastPositionMs) {
            isSeeking = false
            activeStore.clear()
            scrollAllocator.reset()
            topAllocator.reset()
            bottomAllocator.reset()
            rebuildActiveWindow(positionMs, repo)
            lastPositionMs = positionMs
            return
        }

        // 正常推进
        activeStore.removeExpired(positionMs)

        // 只孵化上一帧到当前帧之间真正到点的弹幕，避免不可见未来弹幕挤占活动池。
        if (positionMs > lastPositionMs) {
            spawnItems(lastPositionMs + 1L, positionMs, repo)
        }

        lastPositionMs = positionMs
    }

    private fun rebuildActiveWindow(positionMs: Long, repo: DanmakuRepository) {
        val lookbackMs = maxOf(
            SEEK_REBUILD_MS,
            (SCROLL_DURATION_MS / currentStyle.speedFactor).toLong(),
            FIXED_DURATION_MS
        )
        val startMs = (positionMs - lookbackMs).coerceAtLeast(0L)
        spawnItems(startMs, positionMs, repo)
    }

    private fun trackLineHeight(textSizePx: Float, strokeWidthPx: Float): Float {
        return textSizePx + strokeWidthPx * 4f + 12f
    }

    private fun updateScrollTrackCapacity(textSizePx: Float, strokeWidthPx: Float) {
        if (!::scrollAllocator.isInitialized) return
        val lineHeight = trackLineHeight(textSizePx, strokeWidthPx)
        val availableHeight = (screenHeight - 16f).coerceAtLeast(lineHeight)
        val trackLimit = (availableHeight / lineHeight).toInt().coerceIn(1, SCROLL_TRACK_COUNT)
        scrollAllocator.setActiveTrackLimit(trackLimit)
    }

    /**
     * 在指定时间窗口内孵化新弹幕
     */
    private fun spawnItems(startMs: Long, endMs: Long, repo: DanmakuRepository) {
        val items = repo.query(startMs, endMs)

        // P1: 帧预算降级时减少新弹幕数量
        val maxSpawnPerTick = if (isDegraded) 5 else Int.MAX_VALUE
        var spawned = 0

        for (item in items) {
            if (spawned >= maxSpawnPerTick) break
            if (activeStore.contains(item.id)) continue

            // P1: 密度控制（方案 4.5 节）
            // 裁剪顺序：保留高优先级弹幕 → 保留特殊池 → 剩余普通弹幕做裁剪
            val effectiveMax = when (densityMode) {
                DensityMode.Strict -> (currentStyle.maxVisibleCount * 0.6f).toInt()
                DensityMode.Balanced -> currentStyle.maxVisibleCount
                DensityMode.Crowded -> (currentStyle.maxVisibleCount * 2.4f).toInt()
            }
            if (activeStore.size() >= effectiveMax) {
                when (densityMode) {
                    DensityMode.Strict -> {
                        // 严格模式：新弹幕优先级高于活动弹幕中最低的，则替换
                        if (!tryReplaceLowest(item)) break
                    }
                    DensityMode.Balanced -> {
                        // 平衡模式：同上，但更宽松
                        if (!tryReplaceLowest(item)) break
                    }
                    DensityMode.Crowded -> {
                        // 拥挤模式：允许更高峰值，只在极限情况下才替换低优先级
                        if (activeStore.size() >= (effectiveMax * 1.5f).toInt()) {
                            if (!tryReplaceLowest(item)) break
                        }
                    }
                }
            }

            val displayMs = when (item.type) {
                DanmakuType.ScrollRtl -> (SCROLL_DURATION_MS / currentStyle.speedFactor).toLong()
                DanmakuType.TopFixed, DanmakuType.BottomFixed -> FIXED_DURATION_MS
                DanmakuType.Special -> SCROLL_DURATION_MS
            }

            val textSizePx = item.textSizeSp * currentStyle.scale * 2.5f
            val strokeWidthPx = currentStyle.strokeWidthDp * 1.5f

            if (item.type == DanmakuType.ScrollRtl) {
                updateScrollTrackCapacity(textSizePx, strokeWidthPx)
            }

            // P1: 估算弹幕宽度用于追尾判断
            val estimatedTextWidth = estimateTextWidth(item)

            val trackIndex = when (item.type) {
                DanmakuType.ScrollRtl -> scrollAllocator.allocateScroll(
                    item.timelineMs, estimatedTextWidth, screenWidth, displayMs
                )
                DanmakuType.TopFixed -> topAllocator.allocateFixed(
                    item.timelineMs, displayMs, fromTop = true
                )
                DanmakuType.BottomFixed -> bottomAllocator.allocateFixed(
                    item.timelineMs, displayMs, fromTop = false
                )
                DanmakuType.Special -> -1
            }

            if (trackIndex < 0) continue

            val styleSnapshot = RenderStyleSnapshot(
                // 方案 5.3 节：大小、速度仅影响新入场弹幕，直接用目标值
                textSizePx = textSizePx,
                alpha = currentStyle.alpha, // 透明度立即全局生效
                strokeWidthPx = strokeWidthPx,
                speedFactor = currentStyle.speedFactor
            )

            // P1: 使用对象池获取 ActiveDanmaku
            val active = activeStore.obtain(
                item = item,
                startMs = item.timelineMs,
                endMs = item.timelineMs + displayMs,
                trackIndex = trackIndex,
                styleSnapshot = styleSnapshot
            )

            activeStore.add(active)
            spawned++
        }
    }

    /**
     * 估算弹幕文本宽度（px）
     * P1 用于追尾安全判断，不需要精确值
     */
    private fun estimateTextWidth(item: DanmakuItem): Float {
        val textSizePx = item.textSizeSp * currentStyle.scale * 2.5f
        // 粗略估算：每个字符约 0.6 * textSizePx，中文字符约 1.0 * textSizePx
        var charCount = 0f
        for (c in item.text) {
            charCount += if (c.code > 0x7F) 1.0f else 0.6f
        }
        return charCount * textSizePx
    }

    /**
     * 尝试用新弹幕替换活动中优先级最低的弹幕（方案 4.5 节）
     *
     * 裁剪策略：
     * 1. 保留高优先级弹幕
     * 2. 保留 Special 类型（字幕池/特殊池）
     * 3. 新弹幕优先级 >= 活动中最低优先级时，替换
     *
     * @return true 表示成功替换，新弹幕可以入场
     */
    private fun tryReplaceLowest(newItem: DanmakuItem): Boolean {
        val removed = activeStore.removeLowestPriority() ?: return false
        // 新弹幕优先级必须不低于被移除的
        return newItem.priority >= removed.item.priority
    }

    override fun onPlayerEvent(event: PlayerEvent) {
        when (event) {
            is PlayerEvent.SeekStarted -> {
                isSeeking = true
            }
            is PlayerEvent.SeekEnded -> {
                isSeeking = true
            }
            else -> {}
        }
    }

    override fun updateStyle(style: GlobalDanmakuStyle) {
        currentStyle = style
        densityMode = style.densityMode
        if (!allocatorsInitialized) {
            return
        }
        applyStyleToAllocators()
    }

    private fun applyStyleToAllocators() {
        if (!allocatorsInitialized) return
        scrollAllocator.setDensityMode(densityMode)
        topAllocator.setDensityMode(densityMode)
        bottomAllocator.setDensityMode(densityMode)
    }

    override fun snapshot(): List<DanmakuRenderItem> {
        val now = lastPositionMs
        val results = mutableListOf<DanmakuRenderItem>()

        // P1: 获取底部字幕安全区偏移
        val bottomSafeOffset = bottomAllocator.getSubtitleSafeOffset()

        for (active in activeStore.snapshot()) {
            val elapsedMs = now - active.startMs
            if (elapsedMs < 0) continue

            val x: Float
            val y: Float

            when (active.item.type) {
                DanmakuType.ScrollRtl -> {
                    val textWidthEstimate = estimateTextWidth(active.item)
                    val scrollDistance = screenWidth.toFloat() + textWidthEstimate
                    val duration = active.endMs - active.startMs
                    val progress = (elapsedMs.toFloat() / duration).coerceIn(0f, 1f)
                    val lineHeight = trackLineHeight(
                        active.styleSnapshot.textSizePx,
                        active.styleSnapshot.strokeWidthPx
                    )
                    x = screenWidth - progress * scrollDistance
                    y = (8f + active.trackIndex * lineHeight).coerceAtMost(
                        screenHeight - lineHeight - 8f
                    )
                }
                DanmakuType.TopFixed -> {
                    val lineHeight = trackLineHeight(
                        active.styleSnapshot.textSizePx,
                        active.styleSnapshot.strokeWidthPx
                    )
                    x = (screenWidth / 2).toFloat()
                    y = 8f + active.trackIndex * lineHeight
                }
                DanmakuType.BottomFixed -> {
                    x = (screenWidth / 2).toFloat()
                    // 从屏幕底部向上排列，确保不超出下边界
                    val lineHeight = trackLineHeight(
                        active.styleSnapshot.textSizePx,
                        active.styleSnapshot.strokeWidthPx
                    )
                    val bottomAnchor = screenHeight - bottomSafeOffset - 8f
                    val rankFromBottom = (BOTTOM_TRACK_COUNT - 1 - active.trackIndex).coerceAtLeast(0)
                    val rawY = bottomAnchor - (rankFromBottom + 1) * lineHeight
                    // clamp: 确保弹幕不超出屏幕
                    y = rawY.coerceIn(
                        8f,
                        screenHeight - lineHeight - 8f
                    )
                }
                DanmakuType.Special -> continue
            }

            results.add(
                DanmakuRenderItem(
                    id = active.item.id,
                    text = active.item.text,
                    x = x,
                    y = y,
                    alpha = active.styleSnapshot.alpha,
                    styleKey = "${active.item.textSizeSp}_${active.styleSnapshot.textSizePx.toInt()}_${active.item.color}",
                    type = active.item.type,
                    color = active.item.color,
                    textSizePx = active.styleSnapshot.textSizePx,
                    strokeWidthPx = active.styleSnapshot.strokeWidthPx
                )
            )
        }

        return results
    }

    /**
     * P1: 通知引擎当前帧预算状态
     * 由 MpvClockBridge 或 CanvasRenderer 调用
     */
    fun setFrameBudgetDegraded(degraded: Boolean) {
        isDegraded = degraded
    }

    /**
     * P1: 低内存时通知对象池缩减
     */
    fun onTrimMemory() {
        activeStore.trimPool()
    }

    override fun clear() {
        activeStore.clear()
        scrollAllocator.reset()
        topAllocator.reset()
        bottomAllocator.reset()
        lastPositionMs = -1L
    }

    override fun release() {
        clear()
    }
}

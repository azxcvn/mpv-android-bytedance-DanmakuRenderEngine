package com.danmaku.flow.engine

import com.danmaku.flow.model.ActiveDanmaku
import com.danmaku.flow.model.DanmakuItem
import com.danmaku.flow.model.DanmakuRenderItem
import com.danmaku.flow.model.DanmakuType
import com.danmaku.flow.model.GlobalDanmakuStyle
import com.danmaku.flow.model.RenderStyleSnapshot
import com.danmaku.flow.parser.DanmakuRepository
import com.danmakuplayer.bridge.PlayerEvent

/**
 * 弹幕时间轴引擎
 *
 * P0 首版实现，完成以下职责：
 * 1. 根据当前播放时间，从 Repository 查询应活动的弹幕
 * 2. 给新弹幕分配轨道
 * 3. 计算弹幕在当前时刻的位置
 * 4. seek 后重建活动池
 *
 * 对应融合方案 9.1.1 第 4 步
 */
class DanmakuTimelineEngine : DanmakuEngine {

    // 配置参数
    companion object {
        /** 预读窗口 ms */
        const val PREFETCH_MS = 2000L
        /** 活动扫描跨度 ms（当前时间前后） */
        const val ACTIVE_SCAN_MS = 800L
        /** seek 后重建跨度 ms */
        const val SEEK_REBUILD_MS = 3000L
        /** 滚动弹幕默认停留时长 ms */
        const val SCROLL_DURATION_MS = 8000L
        /** 固定弹幕默认停留时长 ms */
        const val FIXED_DURATION_MS = 3000L
        /** 滚动轨道数 */
        const val SCROLL_TRACK_COUNT = 12
        /** 顶部固定轨道数 */
        const val TOP_TRACK_COUNT = 4
        /** 底部固定轨道数 */
        const val BOTTOM_TRACK_COUNT = 4
    }

    private var repository: DanmakuRepository? = null
    private var screenWidth = 1080
    private var screenHeight = 1920

    private lateinit var scrollAllocator: TrackAllocator
    private lateinit var topAllocator: TrackAllocator
    private lateinit var bottomAllocator: TrackAllocator
    private val activeStore = ActiveDanmakuStore()

    private var currentStyle = GlobalDanmakuStyle()
    private var isSeeking = false
    private var lastPositionMs = -1L
    private var seekPositionMs = 0L

    override fun initialize(repository: DanmakuRepository, screenWidth: Int, screenHeight: Int) {
        this.repository = repository
        this.screenWidth = screenWidth
        this.screenHeight = screenHeight
        this.scrollAllocator = TrackAllocator(SCROLL_TRACK_COUNT)
        this.topAllocator = TrackAllocator(TOP_TRACK_COUNT)
        this.bottomAllocator = TrackAllocator(BOTTOM_TRACK_COUNT)
    }

    override fun onClockTick(positionMs: Long) {
        val repo = repository ?: return

        // seek 后重建
        if (isSeeking) {
            isSeeking = false
            activeStore.clear()
            scrollAllocator.reset()
            topAllocator.reset()
            bottomAllocator.reset()
            lastPositionMs = positionMs
            spawnItems(positionMs, positionMs + SEEK_REBUILD_MS, repo)
            return
        }

        // 正常推进
        activeStore.removeExpired(positionMs)

        // 预读：查询 [positionMs, positionMs + PREFETCH_MS) 的新弹幕
        if (positionMs > lastPositionMs) {
            spawnItems(lastPositionMs.coerceAtLeast(positionMs), positionMs + PREFETCH_MS, repo)
        }

        lastPositionMs = positionMs
    }

    /**
     * 在指定时间窗口内孵化新弹幕
     */
    private fun spawnItems(startMs: Long, endMs: Long, repo: DanmakuRepository) {
        val items = repo.query(startMs, endMs)

        for (item in items) {
            if (activeStore.contains(item.id)) continue

            val displayMs = when (item.type) {
                DanmakuType.ScrollRtl -> (SCROLL_DURATION_MS / currentStyle.speedFactor).toLong()
                DanmakuType.TopFixed, DanmakuType.BottomFixed -> FIXED_DURATION_MS
                DanmakuType.Special -> SCROLL_DURATION_MS // P0 不处理
            }

            val trackIndex = when (item.type) {
                DanmakuType.ScrollRtl -> scrollAllocator.allocateScroll(
                    item.timelineMs, 0f, screenWidth, displayMs
                )
                DanmakuType.TopFixed -> topAllocator.allocateFixed(
                    item.timelineMs, displayMs, fromTop = true
                )
                DanmakuType.BottomFixed -> bottomAllocator.allocateFixed(
                    item.timelineMs, displayMs, fromTop = false
                )
                DanmakuType.Special -> -1
            }

            if (trackIndex < 0) continue // 无可用轨道

            // 密度控制：超过上限时跳过
            if (activeStore.size() >= currentStyle.maxVisibleCount) break

            val styleSnapshot = RenderStyleSnapshot(
                textSizePx = item.textSizeSp * currentStyle.scale * 2.5f, // sp -> px 粗略
                alpha = currentStyle.alpha,
                strokeWidthPx = currentStyle.strokeWidthDp * 1.5f, // dp -> px 粗略
                speedFactor = currentStyle.speedFactor
            )

            val active = ActiveDanmaku(
                item = item,
                startMs = item.timelineMs,
                endMs = item.timelineMs + displayMs,
                trackIndex = trackIndex,
                styleSnapshot = styleSnapshot
            )

            activeStore.add(active)
        }
    }

    override fun onPlayerEvent(event: PlayerEvent) {
        when (event) {
            is PlayerEvent.SeekStarted -> {
                isSeeking = true
            }
            is PlayerEvent.SeekEnded -> {
                isSeeking = true // 标记下一帧重建
            }
            else -> {}
        }
    }

    override fun updateStyle(style: GlobalDanmakuStyle) {
        currentStyle = style
    }

    override fun snapshot(): List<DanmakuRenderItem> {
        val now = lastPositionMs
        val results = mutableListOf<DanmakuRenderItem>()

        for (active in activeStore.snapshot()) {
            val elapsedMs = now - active.startMs
            if (elapsedMs < 0) continue // 还没到

            val x: Float
            val y: Float

            when (active.item.type) {
                DanmakuType.ScrollRtl -> {
                    // 从右到左滚动：x = screenWidth - (elapsed / duration) * (screenWidth + textWidth)
                    // P0 简化：假设文字宽度 200px，动画从右边缘到左边缘
                    val textWidthEstimate = 200f
                    val scrollDistance = screenWidth.toFloat() + textWidthEstimate
                    val progress = (elapsedMs.toFloat() / (active.endMs - active.startMs)).coerceIn(0f, 1f)
                    x = screenWidth - progress * scrollDistance
                    y = active.trackIndex * (active.styleSnapshot.textSizePx + 8f)
                }
                DanmakuType.TopFixed -> {
                    x = (screenWidth / 2).toFloat()
                    y = active.trackIndex * (active.styleSnapshot.textSizePx + 8f)
                }
                DanmakuType.BottomFixed -> {
                    x = (screenWidth / 2).toFloat()
                    y = screenHeight - (BOTTOM_TRACK_COUNT - active.trackIndex) * (active.styleSnapshot.textSizePx + 8f)
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

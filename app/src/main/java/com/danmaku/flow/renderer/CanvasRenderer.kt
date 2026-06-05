package com.danmaku.flow.renderer

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import com.danmaku.flow.bridge.api.DanmakuOverlayHost
import com.danmaku.flow.bridge.api.DanmakuRenderConfig
import com.danmaku.flow.bridge.api.DanmakuRenderer
import com.danmaku.flow.model.DanmakuRenderItem
import com.danmaku.flow.model.DanmakuType
import com.danmaku.flow.model.FrameStats

/**
 * Canvas 弹幕渲染器（P1 帧预算版）
 *
 * 首版渲染实现，使用 Canvas + GlyphCache。
 * P1 增加帧预算监控与自动降级。
 *
 * 对应融合方案 5.1 节和 9.2 第 6 项
 */
class CanvasRenderer : DanmakuRenderer {

    private var host: DanmakuOverlayHost? = null
    private var config = DanmakuRenderConfig()

    private val glyphCache = GlyphCache()
    private val measureCache = TextMeasureCache()

    /** P1: 帧预算监控器 */
    private val frameBudgetMonitor = FrameBudgetMonitor()

    /** 实际画布尺寸（每帧从 Canvas 获取） */
    var canvasWidth = 0
        private set
    var canvasHeight = 0
        private set

    private val clearPaint = Paint().apply {
        isAntiAlias = true
    }

    /** 复用的测量 Paint，避免每帧每条弹幕 new Paint */
    private val measurePaint = Paint()

    override fun initialize(host: DanmakuOverlayHost) {
        this.host = host
    }

    fun prefetch(items: List<DanmakuRenderItem>, limit: Int = 24) {
        var prefetched = 0
        val seen = HashSet<String>()
        for (item in items) {
            if (prefetched >= limit) break
            if (item.text.isBlank()) continue
            val key = item.styleKey
            if (!seen.add(key)) continue
            glyphCache.prefetch(item.text, item.textSizePx, item.color, item.strokeWidthPx)
            prefetched++
        }
    }

    override fun render(frameTimeMs: Long, items: List<DanmakuRenderItem>) {
        val h = host ?: return
        val canvas = h.lockCanvas() ?: return

        try {
            canvasWidth = canvas.width
            canvasHeight = canvas.height

            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

            val startTimeNs = System.nanoTime()

            // P1: 降级时限制渲染数量
            val renderItems = if (frameBudgetMonitor.isDegraded && items.size > 30) {
                // 降级模式：只渲染前 30 条（优先保留已绘制的）
                items.take(30)
            } else {
                items
            }

            for (item in renderItems) {
                if (item.alpha <= 0f) continue
                if (item.text.isBlank()) continue

                val effectiveAlpha = item.alpha * config.alpha
                val effectiveTextSize = item.textSizePx * config.scale
                val effectiveStroke = item.strokeWidthPx * config.scale

                when (item.type) {
                    DanmakuType.ScrollRtl -> {
                        // 滚动弹幕不需要居中，直接用 x 坐标，跳过测量
                        glyphCache.draw(
                            canvas,
                            item.text,
                            item.x,
                            item.y + effectiveTextSize,
                            effectiveTextSize,
                            item.color,
                            effectiveStroke,
                            effectiveAlpha
                        )
                    }
                    DanmakuType.TopFixed, DanmakuType.BottomFixed -> {
                        val textWidth = measureTextWidth(item.text, effectiveTextSize)
                        val centerX = item.x - textWidth / 2f
                        glyphCache.draw(
                            canvas,
                            item.text,
                            centerX,
                            item.y + effectiveTextSize,
                            effectiveTextSize,
                            item.color,
                            effectiveStroke,
                            effectiveAlpha
                        )
                    }
                    DanmakuType.Special -> { }
                }
            }

            // P1: 记录帧耗时
            val renderTimeNs = System.nanoTime() - startTimeNs
            frameBudgetMonitor.recordFrame(renderTimeNs)

        } finally {
            h.unlockCanvasAndPost(canvas)
        }
    }

    override fun updateConfig(config: DanmakuRenderConfig) {
        val oldScale = this.config.scale
        this.config = config
        if (oldScale != config.scale) {
            glyphCache.clear()
            measureCache.clear()
        }
    }

    override fun release() {
        glyphCache.clear()
        measureCache.clear()
        frameBudgetMonitor.reset()
        host = null
    }

    override fun getFrameStats(): FrameStats = frameBudgetMonitor.getStats()

    /** 获取帧预算监控器引用（供引擎查询降级状态） */
    fun getFrameBudgetMonitor(): FrameBudgetMonitor = frameBudgetMonitor

    private fun measureTextWidth(text: String, textSizePx: Float): Float {
        measurePaint.textSize = textSizePx
        val result = measureCache.measure(text, textSizePx, measurePaint)
        return result.width
    }

    /** 获取最近一帧渲染耗时（ms） */
    fun getLastRenderTimeMs(): Float = frameBudgetMonitor.lastFrameMs
}

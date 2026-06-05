package com.danmaku.flow.renderer

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import com.danmaku.flow.model.DanmakuRenderItem
import com.danmaku.flow.model.DanmakuType
import com.danmakuplayer.bridge.DanmakuOverlayHost
import com.danmakuplayer.bridge.DanmakuRenderConfig
import com.danmakuplayer.bridge.DanmakuRenderer

/**
 * Canvas 弹幕渲染器
 *
 * 首版渲染实现，使用 Canvas + GlyphCache。
 * 对应融合方案 5.1 节和 9.1.1 第 5 步
 *
 * 施工顺序（按方案建议）：
 * 1. 先让 drawText 版跑通位置 ✓
 * 2. 再补文本测量缓存 ✓
 * 3. 再补 GlyphCache 预烘焙 ✓
 * 4. 最后再补对象池和预算统计（P1）
 */
class CanvasRenderer : DanmakuRenderer {

    private var host: DanmakuOverlayHost? = null
    private var config = DanmakuRenderConfig()

    private val glyphCache = GlyphCache()
    private val measureCache = TextMeasureCache()

    // 帧预算监控（P1 再细化）
    private var lastRenderTimeNs = 0L
    private var frameBudgetExceededCount = 0

    private val clearPaint = Paint().apply {
        isAntiAlias = true
    }

    override fun initialize(host: DanmakuOverlayHost) {
        this.host = host
    }

    override fun render(frameTimeMs: Long, items: List<DanmakuRenderItem>) {
        val h = host ?: return
        val canvas = h.lockCanvas() ?: return

        try {
            // 清空为透明
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

            val startTimeNs = System.nanoTime()

            // 绘制所有弹幕
            for (item in items) {
                if (item.alpha <= 0f) continue
                if (item.text.isBlank()) continue

                val effectiveAlpha = item.alpha * config.alpha
                val effectiveTextSize = item.textSizePx * config.scale
                val effectiveStroke = item.strokeWidthPx * config.scale

                when (item.type) {
                    DanmakuType.ScrollRtl -> {
                        // 从右向左滚动：x 由引擎计算
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
                    DanmakuType.TopFixed -> {
                        // 顶部居中
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
                    DanmakuType.BottomFixed -> {
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
                    DanmakuType.Special -> {
                        // P0 不处理
                    }
                }
            }

            lastRenderTimeNs = System.nanoTime() - startTimeNs
        } finally {
            h.unlockCanvasAndPost(canvas)
        }
    }

    override fun updateConfig(config: DanmakuRenderConfig) {
        val oldScale = this.config.scale
        this.config = config
        // 配置变化时清空缓存，渐进重建
        if (oldScale != config.scale) {
            glyphCache.clear()
            measureCache.clear()
        }
    }

    override fun release() {
        glyphCache.clear()
        measureCache.clear()
        host = null
    }

    private fun measureTextWidth(text: String, textSizePx: Float): Float {
        val paint = Paint().apply {
            this.textSize = textSizePx
        }
        val result = measureCache.measure(text, textSizePx, paint)
        return result.width
    }

    /** 帧预算监控（P1 完善） */
    fun getLastRenderTimeMs(): Float = lastRenderTimeNs / 1_000_000f
}

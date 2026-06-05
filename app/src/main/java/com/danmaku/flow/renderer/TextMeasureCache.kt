package com.danmaku.flow.renderer

import android.graphics.Paint
import android.text.StaticLayout
import android.text.TextPaint
import com.danmaku.flow.model.DanmakuRenderItem

/**
 * 文本测量缓存
 *
 * 避免每帧重复测量同一文字。
 * 对应融合方案 5.1 节和 9.1.1 第 5 步
 */
class TextMeasureCache {

    private data class MeasureKey(
        val text: String,
        val textSizePx: Float
    )

    data class MeasureResult(
        val width: Float,
        val height: Float
    )

    private val cache = HashMap<MeasureKey, MeasureResult>(64)

    /**
     * 测量文本宽高（带缓存）
     */
    fun measure(text: String, textSizePx: Float, paint: Paint): MeasureResult {
        val key = MeasureKey(text, textSizePx)
        cache[key]?.let { return it }

        paint.textSize = textSizePx
        val width = paint.measureText(text)
        val fm = paint.fontMetrics
        val height = fm.descent - fm.ascent

        val result = MeasureResult(width, height)
        cache[key] = result
        return result
    }

    fun clear() {
        cache.clear()
    }

    fun size(): Int = cache.size
}

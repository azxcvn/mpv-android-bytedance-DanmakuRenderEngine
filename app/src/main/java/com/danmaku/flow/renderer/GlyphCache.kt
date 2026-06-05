package com.danmaku.flow.renderer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint

/**
 * GlyphCache — 文本预烘焙缓存
 *
 * 将弹幕文本预渲染到 Bitmap，避免每帧 drawText。
 * 对应融合方案 5.1 节和 9.1.1 第 5 步
 */
class GlyphCache {

    private data class CacheKey(
        val text: String,
        val textSizePx: Int,
        val color: Int
    )

    internal data class CacheEntry(
        val bitmap: Bitmap,
        val width: Int,
        val height: Int
    )

    private val cache = HashMap<CacheKey, CacheEntry>(128)

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.DEFAULT
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.BLACK
    }

    /** 复用的绘制 Paint，避免每帧每条弹幕 new Paint */
    private val drawPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    /**
     * 获取预烘焙 Bitmap（不存在则创建）
     */
    internal fun getOrBake(
        text: String,
        textSizePx: Float,
        color: Int,
        strokeWidthPx: Float
    ): CacheEntry {
        val key = CacheKey(text, textSizePx.toInt(), color)
        cache[key]?.let { return it }

        // 计算文字尺寸
        textPaint.textSize = textSizePx
        textPaint.color = color
        val textWidth = textPaint.measureText(text)
        val fm = textPaint.fontMetrics
        val textHeight = fm.descent - fm.ascent
        val padding = strokeWidthPx * 2 + 4

        val bmpWidth = (textWidth + padding * 2).toInt().coerceAtLeast(1)
        val bmpHeight = (textHeight + padding * 2).toInt().coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(bmpWidth, bmpHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 描边（在文字下面）
        if (strokeWidthPx > 0f) {
            strokePaint.textSize = textSizePx
            strokePaint.strokeWidth = strokeWidthPx * 2
            strokePaint.typeface = Typeface.DEFAULT
            canvas.drawText(text, padding, padding - fm.ascent, strokePaint)
        }

        // 文字
        canvas.drawText(text, padding, padding - fm.ascent, textPaint)

        val entry = CacheEntry(bitmap, bmpWidth, bmpHeight)
        cache[key] = entry
        return entry
    }

    /**
     * 直接在目标 Canvas 上绘制（使用缓存 Bitmap）
     */
    fun draw(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        textSizePx: Float,
        color: Int,
        strokeWidthPx: Float,
        alpha: Float
    ) {
        val entry = getOrBake(text, textSizePx, color, strokeWidthPx)
        drawPaint.alpha = (alpha * 255).toInt().coerceIn(0, 255)
        canvas.drawBitmap(entry.bitmap, x, y - (entry.height / 2f), drawPaint)
    }

    fun clear() {
        for (entry in cache.values) {
            entry.bitmap.recycle()
        }
        cache.clear()
    }

    fun size(): Int = cache.size
}

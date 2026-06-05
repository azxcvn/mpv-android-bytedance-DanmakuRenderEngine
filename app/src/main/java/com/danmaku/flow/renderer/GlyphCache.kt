package com.danmaku.flow.renderer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
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
        val color: Int,
        val strokeWidthPx: Int
    )

    internal data class CacheEntry(
        val bitmap: Bitmap,
        val width: Int,
        val height: Int,
        val baselineOffset: Float
    )

    private val cache = HashMap<CacheKey, CacheEntry>(128)

    /** 缓存上限，超过时触发 LRU 淘汰（防止大量不同颜色/字号导致 OOM） */
    private val maxCacheSize = 300

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
        val key = CacheKey(text, textSizePx.toInt(), color, strokeWidthPx.toInt())
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
        val baselineOffset = padding - fm.ascent

        // 描边（在文字下面）
        if (strokeWidthPx > 0f) {
            strokePaint.textSize = textSizePx
            strokePaint.strokeWidth = strokeWidthPx * 2
            strokePaint.typeface = Typeface.DEFAULT
            canvas.drawText(text, padding, baselineOffset, strokePaint)
        }

        // 文字
        canvas.drawText(text, padding, baselineOffset, textPaint)

        val entry = CacheEntry(bitmap, bmpWidth, bmpHeight, baselineOffset)

        // 缓存淘汰：超过上限时清空一半（简单 LRU 近似）
        if (cache.size >= maxCacheSize) {
            val evictCount = maxCacheSize / 2
            val iterator = cache.entries.iterator()
            var evicted = 0
            while (iterator.hasNext() && evicted < evictCount) {
                iterator.next().value.bitmap.recycle()
                iterator.remove()
                evicted++
            }
        }

        cache[key] = entry
        return entry
    }

    fun prefetch(
        text: String,
        textSizePx: Float,
        color: Int,
        strokeWidthPx: Float
    ) {
        if (text.isBlank()) return
        getOrBake(text, textSizePx, color, strokeWidthPx)
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
        canvas.drawBitmap(entry.bitmap, x, y - entry.baselineOffset, drawPaint)
    }

    fun clear() {
        for (entry in cache.values) {
            entry.bitmap.recycle()
        }
        cache.clear()
    }

    fun size(): Int = cache.size
}

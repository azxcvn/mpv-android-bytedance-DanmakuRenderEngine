/*
 * Copyright (C) 2022 ByteDance Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.bytedance.danmaku.render.engine.render.draw.text

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.text.TextUtils
import com.bytedance.danmaku.render.engine.control.DanmakuConfig
import com.bytedance.danmaku.render.engine.render.draw.DrawItem
import com.bytedance.danmaku.render.engine.utils.DRAW_TYPE_TEXT

/**
 * Created by dss886 on 2019/4/19.
 *
 * FontMetrics: Top - Ascent - Baseline - Descent - Bottom
 *
 * In ASCII or common Asia characters,
 * the space between Top and Ascent are usually unused,
 * which causes the text to be visually not centered.
 *
 * Turn TextData.includeFontPadding to false will cut the space between Top and Ascent.
 */
open class TextDrawItem: DrawItem<TextData>() {

    private val mTextPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private val mUnderlinePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)

    /** Bitmap 缓存：将文本（描边+填充）光栅化后缓存，后续帧直接 drawBitmap */
    private var mCacheBitmap: Bitmap? = null
    /** 缓存对应的配置指纹，配置变化时需要重建缓存 */
    private var mCacheKey: Long = 0L

    override fun getDrawType(): Int {
        return DRAW_TYPE_TEXT
    }

    override fun onBindData(data: TextData) {
        mTextPaint.flags = Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG
        mUnderlinePaint.flags = Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG
        recycleBitmapCache()
    }

    override fun onMeasure(config: DanmakuConfig) {
        if (!TextUtils.isEmpty(data?.text)) {
            mTextPaint.textSize = data?.textSize ?: config.text.size
            width = mTextPaint.measureText(data?.text)
            val includeFontPadding = data?.includeFontPadding ?: config.text.includeFontPadding
            height = getFontHeight(includeFontPadding, mTextPaint)
            // 配置变化后清除缓存
            val newKey = buildCacheKey(config)
            if (newKey != mCacheKey) {
                recycleBitmapCache()
                mCacheKey = newKey
            }
        } else {
            width = 0F
            height = 0F
        }
    }

    override fun onDraw(canvas: Canvas, config: DanmakuConfig) {
        val text = data?.text
        if (TextUtils.isEmpty(text)) return

        val bitmapW = (width + (data?.textStrokeWidth ?: config.text.strokeWidth) + 2f).toInt()
        val bitmapH = (height + (data?.textStrokeWidth ?: config.text.strokeWidth) + 2f).toInt()
        if (bitmapW <= 0 || bitmapH <= 0) return

        // 尝试使用缓存
        var bitmap = mCacheBitmap
        if (bitmap == null || bitmap.isRecycled) {
            bitmap = buildBitmapCache(bitmapW, bitmapH, config)
            mCacheBitmap = bitmap
        }

        if (bitmap != null && !bitmap.isRecycled) {
            // 描边可能导致文字偏移，补偿 strokeWidth/2
            val offsetX = (data?.textStrokeWidth ?: config.text.strokeWidth) / 2f
            canvas.drawBitmap(bitmap, x - offsetX, y, null)
        } else {
            // fallback：缓存创建失败时走原始绘制路径
            drawTextDirect(canvas, mTextPaint, config)
        }

        drawUnderline(canvas, mTextPaint, mUnderlinePaint, config)
    }

    override fun recycle() {
        super.recycle()
        mTextPaint.reset()
        mUnderlinePaint.reset()
        recycleBitmapCache()
        mCacheKey = 0L
    }

    /** 构建 Bitmap 缓存 */
    private fun buildBitmapCache(bitmapW: Int, bitmapH: Int, config: DanmakuConfig): Bitmap? {
        return try {
            val bitmap = Bitmap.createBitmap(bitmapW, bitmapH, Bitmap.Config.ARGB_8888)
            val cacheCanvas = Canvas(bitmap)
            val offsetX = (data?.textStrokeWidth ?: config.text.strokeWidth) / 2f

            data?.text?.let { text ->
                // draw stroke
                (data?.textStrokeWidth ?: config.text.strokeWidth).takeIf { it > 0 }?.let { sw ->
                    mTextPaint.style = Paint.Style.STROKE
                    mTextPaint.color = data?.textStrokeColor ?: config.text.strokeColor
                    mTextPaint.typeface = data?.typeface ?: config.text.typeface
                    mTextPaint.textSize = data?.textSize ?: config.text.size
                    mTextPaint.strokeWidth = sw
                    val baseline = getBaseline(data?.includeFontPadding ?: true, 0f, mTextPaint)
                    cacheCanvas.drawText(text, offsetX, baseline, mTextPaint)
                }
                // draw fill
                mTextPaint.style = Paint.Style.FILL
                mTextPaint.color = data?.textColor ?: config.text.color
                mTextPaint.typeface = data?.typeface ?: config.text.typeface
                mTextPaint.textSize = data?.textSize ?: config.text.size
                mTextPaint.strokeWidth = 0f
                val includeFontPadding = data?.includeFontPadding ?: config.text.includeFontPadding
                val baseline = getBaseline(includeFontPadding, 0f, mTextPaint)
                cacheCanvas.drawText(text, offsetX, baseline, mTextPaint)
            }
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    /** 生成配置指纹，用于检测是否需要重建缓存 */
    private fun buildCacheKey(config: DanmakuConfig): Long {
        var key = (data?.textSize ?: config.text.size).toBits().toLong()
        key = key * 31 + (data?.textColor ?: config.text.color).toLong()
        key = key * 31 + (data?.textStrokeColor ?: config.text.strokeColor).toLong()
        key = key * 31 + (data?.textStrokeWidth ?: config.text.strokeWidth).toBits().toLong()
        key = key * 31 + (data?.text?.hashCode()?.toLong() ?: 0L)
        return key
    }

    private fun recycleBitmapCache() {
        mCacheBitmap?.let {
            if (!it.isRecycled) it.recycle()
        }
        mCacheBitmap = null
    }

    /**
     * 原始绘制路径（fallback）
     */
    private fun drawTextDirect(canvas: Canvas, paint: Paint, config: DanmakuConfig) {
        data?.text?.let { text ->
            // draw stroke
            (data?.textStrokeWidth ?: config.text.strokeWidth).takeIf { it > 0 }?.let { width ->
                paint.style = Paint.Style.STROKE
                paint.color = data?.textStrokeColor ?: config.text.strokeColor
                paint.typeface = data?.typeface ?: config.text.typeface
                paint.textSize = data?.textSize ?: config.text.size
                paint.strokeWidth = width
                val baseline = getBaseline(data?.includeFontPadding ?: true, y, paint)
                canvas.drawText(text, x, baseline, paint)
            }
            // draw drawText
            paint.style = Paint.Style.FILL
            paint.color = data?.textColor ?: config.text.color
            paint.typeface = data?.typeface ?: config.text.typeface
            paint.textSize = data?.textSize ?: config.text.size
            paint.strokeWidth = 0f
            val includeFontPadding = data?.includeFontPadding ?: config.text.includeFontPadding
            val baseline = getBaseline(includeFontPadding, y, paint)
            canvas.drawText(text, x, baseline, paint)
        }
    }

    private fun drawUnderline(canvas: Canvas, textPaint: Paint, underlinePaint: Paint,config: DanmakuConfig) {
        takeIf { data?.hasUnderline == true }?.let {
            val includeFontPadding = data?.includeFontPadding ?: config.text.includeFontPadding
            val underlineY = y + getFontHeight(includeFontPadding, textPaint) + config.underline.marginTop
            // draw underline stroke
            takeIf {config.underline.strokeWidth > 0 }?.let {
                underlinePaint.style = Paint.Style.STROKE
                underlinePaint.color = config.underline.strokeColor
                underlinePaint.strokeWidth = config.underline.strokeWidth
                canvas.drawRect(x, underlineY, x + width, underlineY + config.underline.width, underlinePaint)
            }
            // draw underline
            underlinePaint.style = Paint.Style.FILL
            underlinePaint.color = data?.textColor ?: config.underline.color
            underlinePaint.strokeWidth = 0f
            canvas.drawRect(x, underlineY, x + width, underlineY + config.underline.width, underlinePaint)
        }
    }

    private fun getFontHeight(includeFontPadding: Boolean, paint: Paint): Float {
        return if (includeFontPadding)
            paint.fontMetrics.bottom - paint.fontMetrics.top
        else
            paint.fontMetrics.bottom - paint.fontMetrics.ascent
    }

    private fun getBaseline(includeFontPadding: Boolean, top: Float, paint: Paint): Float {
        return if (includeFontPadding) top - paint.fontMetrics.top else top - paint.fontMetrics.ascent
    }

}
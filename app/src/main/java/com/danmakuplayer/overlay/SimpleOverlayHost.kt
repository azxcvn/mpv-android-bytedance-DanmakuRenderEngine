package com.danmakuplayer.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.danmaku.flow.bridge.api.DanmakuOverlayHost

/**
 * 弹幕叠加层宿主实现
 *
 * 使用透明 SurfaceView，通过 setZOrderOnTop(true) 确保在视频 SurfaceView 之上。
 * 首版用于验证叠加层能正常工作，后续可由正式实现替换。
 */
class SimpleOverlayHost(
    context: Context
) : SurfaceView(context), DanmakuOverlayHost, SurfaceHolder.Callback {

    private var holderReady = false
    private val bgPaint = Paint().apply { isAntiAlias = true }

    /** Surface 就绪回调 */
    var onSurfaceReady: (() -> Unit)? = null

    init {
        // 确保叠加层在视频 SurfaceView 之上
        setZOrderOnTop(true)
        holder.setFormat(android.graphics.PixelFormat.TRANSPARENT)
        holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        holderReady = true
        // 首次绘制测试图案
        drawTestPattern()
        // 通知 Surface 已就绪
        onSurfaceReady?.invoke()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {}
    override fun surfaceDestroyed(holder: SurfaceHolder) { holderReady = false }

    // === DanmakuOverlayHost 实现 ===

    override fun width(): Int = if (holderReady) holder.surfaceFrame.width() else 0
    override fun height(): Int = if (holderReady) holder.surfaceFrame.height() else 0

    override fun invalidateFrame() {
        if (holderReady) {
            val canvas = lockCanvas()
            if (canvas != null) {
                // 清空为透明
                canvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
                unlockCanvasAndPost(canvas)
            }
        }
    }

    override fun lockCanvas(): Canvas? {
        return if (holderReady) holder.lockCanvas() else null
    }

    override fun unlockCanvasAndPost(canvas: Canvas) {
        holder.unlockCanvasAndPost(canvas)
    }

    /**
     * 绘制测试图案 — 用于验证叠加层正常工作
     * 在屏幕四角画四个彩色半透明圆点
     */
    fun drawTestPattern() {
        val canvas = lockCanvas() ?: return
        canvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)

        val w = width().toFloat()
        val h = height().toFloat()
        if (w <= 0 || h <= 0) { unlockCanvasAndPost(canvas); return }

        val radius = 30f
        val margin = 60f

        // 左上角 — 红色
        bgPaint.color = Color.argb(180, 255, 80, 80)
        canvas.drawCircle(margin, margin, radius, bgPaint)

        // 右上角 — 绿色
        bgPaint.color = Color.argb(180, 80, 255, 80)
        canvas.drawCircle(w - margin, margin, radius, bgPaint)

        // 左下角 — 蓝色
        bgPaint.color = Color.argb(180, 80, 80, 255)
        canvas.drawCircle(margin, h - margin, radius, bgPaint)

        // 右上角 — 黄色
        bgPaint.color = Color.argb(180, 255, 255, 80)
        canvas.drawCircle(w - margin, h - margin, radius, bgPaint)

        // 中央文字
        bgPaint.color = Color.argb(200, 255, 255, 255)
        bgPaint.textSize = 36f
        bgPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("弹幕叠加层 ✓", w / 2, h / 2, bgPaint)

        unlockCanvasAndPost(canvas)
    }
}

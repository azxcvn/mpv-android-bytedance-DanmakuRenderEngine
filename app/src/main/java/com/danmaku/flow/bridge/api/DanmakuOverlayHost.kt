package com.danmaku.flow.bridge.api

import android.graphics.Canvas

/**
 * 弹幕叠加层宿主
 *
 * 弹幕渲染器通过此接口获取画布和尺寸信息，
 * 不直接依赖 View、SurfaceView 或 TextureView。
 *
 * 对应融合方案 7.1 节
 */
interface DanmakuOverlayHost {
    /** 叠加层宽度（像素） */
    fun width(): Int

    /** 叠加层高度（像素） */
    fun height(): Int

    /** 请求刷新下一帧 */
    fun invalidateFrame()

    /**
     * 获取 Canvas 进行绘制
     * @return 如果 Canvas 可用返回它，否则返回 null
     */
    fun lockCanvas(): Canvas?

    /** 释放 Canvas */
    fun unlockCanvasAndPost(canvas: Canvas)
}

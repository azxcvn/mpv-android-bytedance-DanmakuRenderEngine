package com.danmaku.flow.bridge.api

import com.danmaku.flow.model.DanmakuRenderItem
import com.danmaku.flow.model.FrameStats

/**
 * 弹幕渲染器接口
 *
 * 从第一天就抽象好，首版用 CanvasRenderer，
 * 后续可替换为 SdfGlRenderer 而不影响上层。
 *
 * 对应融合方案 5.2 节
 */
interface DanmakuRenderer {
    fun initialize(host: DanmakuOverlayHost)
    fun render(frameTimeMs: Long, items: List<DanmakuRenderItem>)
    fun updateConfig(config: DanmakuRenderConfig)
    fun release()

    /** 获取帧性能统计（P1） */
    fun getFrameStats(): FrameStats? = null
}

/**
 * 渲染配置
 *
 * P1 扩展：增加降级和渐进生效相关配置
 */
data class DanmakuRenderConfig(
    val scale: Float = 1f,
    val alpha: Float = 1f,
    /** 最大同屏渲染数（降级时引擎可能减少传入的 items） */
    val maxRenderCount: Int = 120,
    /** 是否启用帧预算监控 */
    val enableFrameBudget: Boolean = true
)

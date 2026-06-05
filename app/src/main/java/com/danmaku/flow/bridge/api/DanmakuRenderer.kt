package com.danmaku.flow.bridge.api

import com.danmaku.flow.model.DanmakuRenderItem

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
}

/**
 * 渲染配置
 */
data class DanmakuRenderConfig(
    val scale: Float = 1f,
    val alpha: Float = 1f
)

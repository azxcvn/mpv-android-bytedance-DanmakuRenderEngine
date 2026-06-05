package com.danmaku.flow.model

/**
 * 渲染样式快照
 *
 * 在弹幕入场时捕获样式状态，避免运行时全局配置变更导致所有弹幕瞬间跳变。
 * 对应融合方案 4.7 节
 */
data class RenderStyleSnapshot(
    /** 渲染字号 px */
    val textSizePx: Float,
    /** 透明度 0~1 */
    val alpha: Float,
    /** 描边宽度 px */
    val strokeWidthPx: Float,
    /** 速度倍率 */
    val speedFactor: Float
)

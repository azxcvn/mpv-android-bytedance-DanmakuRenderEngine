package com.danmaku.flow.model

/**
 * 可渲染快照
 *
 * 由时间轴与布局层输出，渲染层只消费这个。
 * 对应融合方案 2.3.2 节
 */
data class DanmakuRenderItem(
    val id: Long,
    val text: String,
    /** 当前帧的 X 坐标（像素） */
    val x: Float,
    /** 当前帧的 Y 坐标（像素） */
    val y: Float,
    /** 透明度 0~1 */
    val alpha: Float,
    /** 样式键（用于 GlyphCache 缓存命中） */
    val styleKey: String,
    val type: DanmakuType,
    /** 原始颜色 */
    val color: Int,
    /** 渲染后字号（px） */
    val textSizePx: Float,
    /** 描边宽度（px） */
    val strokeWidthPx: Float
)

package com.danmakuplayer.model

/**
 * 统一弹幕数据模型
 *
 * 静态源数据，表示一条弹幕从文件解析出来的信息。
 * 对应融合方案 4.1 节
 */
data class DanmakuItem(
    val id: Long,
    /** 出现时间（毫秒） */
    val timelineMs: Long,
    val type: DanmakuType,
    val text: String,
    /** 十进制颜色值，含 alpha */
    val color: Int,
    /** 原始字号（sp） */
    val textSizeSp: Float,
    val source: DanmakuSourceType,
    /** 优先级，数值越大越优先保留 */
    val priority: Int = 0
)

package com.danmaku.flow.model

/**
 * 活动弹幕
 *
 * 表示一条正在屏幕中显示或即将显示的弹幕。
 * 对应融合方案 4.7 节
 */
data class ActiveDanmaku(
    /** 原始弹幕数据 */
    val item: DanmakuItem,
    /** 开始显示时间（ms） */
    val startMs: Long,
    /** 结束显示时间（ms） */
    val endMs: Long,
    /** 分配到的轨道索引 */
    val trackIndex: Int,
    /** 入场时捕获的样式快照 */
    val styleSnapshot: RenderStyleSnapshot
)

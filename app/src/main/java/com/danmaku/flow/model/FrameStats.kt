package com.danmaku.flow.model

/**
 * 帧性能统计
 *
 * 由渲染层产出，供控制层和调试面板消费。
 * 放在 model 包避免 bridge/api 反向依赖 renderer。
 */
data class FrameStats(
    /** 最近一帧耗时（ms） */
    val lastFrameMs: Float,
    /** 最近 N 帧平均耗时（ms） */
    val averageFrameMs: Float,
    /** 帧预算（ms） */
    val budgetMs: Float,
    /** 当前是否处于降级状态 */
    val isDegraded: Boolean,
    /** 连续超预算帧数 */
    val overBudgetCount: Int
)

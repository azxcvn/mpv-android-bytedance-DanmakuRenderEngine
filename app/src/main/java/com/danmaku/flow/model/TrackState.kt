package com.danmaku.flow.model

/**
 * 轨道状态
 *
 * 对应融合方案 4.7 节。
 * P1 增加轨道压力模型所需的预测字段。
 */
data class TrackState(
    /** 轨道索引 */
    val trackIndex: Int,
    /** 该轨道被占用直到此时间（ms），超过此时间才可分配新弹幕 */
    var occupiedUntilMs: Long = 0L,
    /** 最近一条弹幕 ID */
    var lastItemId: Long? = null,
    /** 最近一条弹幕宽度 px（用于追尾安全判断） */
    var lastItemWidthPx: Float = 0f,
    /** 最近一条弹幕预计完全离场的时间 ms */
    var lastItemExitTimeMs: Long = 0L
)

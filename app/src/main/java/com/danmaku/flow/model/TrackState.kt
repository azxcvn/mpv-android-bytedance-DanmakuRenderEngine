package com.danmaku.flow.model

/**
 * 轨道状态
 *
 * 对应融合方案 4.7 节
 */
data class TrackState(
    /** 轨道索引 */
    val trackIndex: Int,
    /** 该轨道被占用直到此时间（ms），超过此时间才可分配新弹幕 */
    var occupiedUntilMs: Long = 0L,
    /** 最近一条弹幕 ID */
    var lastItemId: Long? = null
)

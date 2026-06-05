package com.danmaku.flow.model

/**
 * 活动弹幕
 *
 * 表示一条正在屏幕中显示或即将显示的弹幕。
 * P1 将字段改为 var 以支持对象池复用。
 *
 * 对应融合方案 4.7 节和 9.2 第 4 项
 */
class ActiveDanmaku(
    /** 原始弹幕数据（对象池复用时可变） */
    var item: DanmakuItem,
    /** 开始显示时间（ms） */
    var startMs: Long,
    /** 结束显示时间（ms） */
    var endMs: Long,
    /** 分配到的轨道索引 */
    var trackIndex: Int,
    /** 入场时捕获的样式快照 */
    var styleSnapshot: RenderStyleSnapshot
)

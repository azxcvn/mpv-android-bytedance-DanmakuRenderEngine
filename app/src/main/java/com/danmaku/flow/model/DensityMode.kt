package com.danmaku.flow.model

/**
 * 密度控制模式
 *
 * 对应融合方案 4.5 节的三档模式
 */
enum class DensityMode {
    /** 严格模式：宁可丢弃，不允许明显重叠 */
    Strict,
    /** 平衡模式（默认）：保证阅读体验与显示量平衡 */
    Balanced,
    /** 拥挤模式：优先显示更多弹幕，容忍较小安全间距 */
    Crowded
}

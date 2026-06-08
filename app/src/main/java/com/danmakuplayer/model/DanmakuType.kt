package com.danmakuplayer.model

/**
 * 弹幕类型
 * 对应融合方案 4.1 节
 */
enum class DanmakuType {
    /** 从右向左滚动 */
    ScrollRtl,
    /** 顶部固定 */
    TopFixed,
    /** 底部固定 */
    BottomFixed,
    /** 特殊弹幕（P0 不实现，保留定义） */
    Special
}

package com.danmakuplayer.bridge.api

/**
 * 播放器时钟提供者
 *
 * 弹幕引擎通过此接口获取当前播放时间与状态，
 * 不直接依赖 mpv 或任何具体播放器实现。
 *
 * 对应融合方案 4.3 节
 */
interface PlayerClockProvider {
    /** 当前播放位置（毫秒） */
    fun currentPositionMs(): Long

    /** 是否正在播放 */
    fun isPlaying(): Boolean

    /** 当前播放倍速 */
    fun playbackSpeed(): Float
}

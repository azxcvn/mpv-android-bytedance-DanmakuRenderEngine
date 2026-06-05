package com.danmakuplayer.bridge

/**
 * 播放器事件
 *
 * 弹幕引擎通过 [PlayerEventSource] 监听这些事件来驱动内部状态。
 */
sealed class PlayerEvent {
    /** 播放位置变化 */
    data class PositionChanged(val positionMs: Long) : PlayerEvent()

    /** 播放/暂停状态变化 */
    data class PlayStateChanged(val isPlaying: Boolean) : PlayerEvent()

    /** 倍速变化 */
    data class SpeedChanged(val speed: Float) : PlayerEvent()

    /** 开始拖动/跳转 */
    data object SeekStarted : PlayerEvent()

    /** 跳转完成 */
    data object SeekEnded : PlayerEvent()

    /** 播放结束 */
    data object PlaybackEnded : PlayerEvent()
}

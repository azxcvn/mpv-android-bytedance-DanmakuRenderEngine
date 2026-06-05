package com.danmakuplayer.bridge

/**
 * 播放器事件源
 *
 * 弹幕引擎通过此接口订阅播放器事件（seek、暂停等），
 * 不直接依赖具体播放器实现。
 *
 * 对应融合方案 4.3 节
 */
interface PlayerEventSource {
    fun addListener(listener: PlayerEventListener)
    fun removeListener(listener: PlayerEventListener)
}

/**
 * 播放器事件监听器
 */
interface PlayerEventListener {
    fun onPlayerEvent(event: PlayerEvent)
}

package com.danmaku.flow.engine

import android.view.Choreographer
import com.danmaku.flow.model.DanmakuRenderItem
import com.danmakuplayer.bridge.PlayerClockProvider
import com.danmakuplayer.bridge.PlayerEvent
import com.danmakuplayer.bridge.PlayerEventSource
import com.danmakuplayer.bridge.PlayerEventListener

/**
 * Mpv 时钟桥接
 *
 * 用 Choreographer 驱动引擎时钟，保证与渲染帧同步。
 * 每帧从 PlayerClockProvider 读取时间，推送给 DanmakuEngine。
 *
 * 对应融合方案 4.3 节和 9.1.1 第 6 步
 */
class MpvClockBridge(
    private val clockProvider: PlayerClockProvider,
    private val eventSource: PlayerEventSource,
    private val engine: DanmakuEngine
) {
    private var running = false
    private var renderCallback: ((List<DanmakuRenderItem>) -> Unit)? = null

    private val choreographer = Choreographer.getInstance()

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return

            val positionMs = clockProvider.currentPositionMs()

            // 推送时钟
            engine.onClockTick(positionMs)

            // 获取快照并渲染
            val snapshot = engine.snapshot()
            renderCallback?.invoke(snapshot)

            // 注册下一帧
            choreographer.postFrameCallback(this)
        }
    }

    private val playerListener = object : PlayerEventListener {
        override fun onPlayerEvent(event: PlayerEvent) {
            engine.onPlayerEvent(event)
        }
    }

    /**
     * 启动时钟桥接
     * @param onRender 每帧渲染回调
     */
    fun start(onRender: (List<DanmakuRenderItem>) -> Unit) {
        if (running) return
        running = true
        renderCallback = onRender
        eventSource.addListener(playerListener)
        choreographer.postFrameCallback(frameCallback)
    }

    /** 暂停 */
    fun pause() {
        running = false
        choreographer.removeFrameCallback(frameCallback)
    }

    /** 恢复 */
    fun resume() {
        if (!running) {
            running = true
            choreographer.postFrameCallback(frameCallback)
        }
    }

    /** 停止并释放 */
    fun stop() {
        running = false
        choreographer.removeFrameCallback(frameCallback)
        eventSource.removeListener(playerListener)
        renderCallback = null
    }
}

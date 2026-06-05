package com.danmaku.flow.bridge.mpv

import android.view.Choreographer
import com.danmaku.flow.bridge.api.PlayerClockProvider
import com.danmaku.flow.bridge.api.PlayerEvent
import com.danmaku.flow.bridge.api.PlayerEventSource
import com.danmaku.flow.bridge.api.PlayerEventListener
import com.danmaku.flow.engine.DanmakuEngine
import com.danmaku.flow.model.DanmakuRenderItem

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
    private var canvasSizeProvider: (() -> Pair<Int, Int>)? = null

    private val choreographer = Choreographer.getInstance()

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return

            val positionMs = clockProvider.currentPositionMs()

            // 每帧同步实际画布尺寸到引擎（在 snapshot 前）
            canvasSizeProvider?.invoke()?.let { (w, h) ->
                if (w > 0 && h > 0) engine.setScreenSize(w, h)
            }

            // 推送时钟（每帧都推，保证时间准确）
            engine.onClockTick(positionMs)

            // 获取快照并渲染（跟随屏幕刷新率，不人为限帧）
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
     * @param onCanvasSize 获取实际画布尺寸的回调
     */
    fun start(
        onRender: (List<DanmakuRenderItem>) -> Unit,
        onCanvasSize: () -> Pair<Int, Int> = { Pair(0, 0) }
    ) {
        if (running) return
        running = true
        renderCallback = onRender
        canvasSizeProvider = onCanvasSize
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
        canvasSizeProvider = null
    }
}

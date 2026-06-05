package com.danmaku.flow.controller

import com.danmaku.flow.bridge.api.DanmakuOverlayHost
import com.danmaku.flow.bridge.api.PlayerClockProvider
import com.danmaku.flow.bridge.api.PlayerEvent
import com.danmaku.flow.bridge.api.PlayerEventSource
import com.danmaku.flow.bridge.mpv.MpvClockBridge
import com.danmaku.flow.engine.DanmakuEngine
import com.danmaku.flow.engine.DanmakuTimelineEngine
import com.danmaku.flow.model.GlobalDanmakuStyle
import com.danmaku.flow.parser.BilibiliXmlParser
import com.danmaku.flow.parser.DanmakuRepository
import com.danmaku.flow.model.FrameStats
import com.danmaku.flow.renderer.CanvasRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

/**
 * 弹幕控制器实现（P1 工程强化版）
 *
 * P0 基础上增加：
 * - 帧预算监控联动引擎降级
 * - 低内存回调（对象池缩减）
 * - 渐进式配置生效
 *
 * 对应融合方案 7.2 节和 9.2 第 3~7 项
 */
class DanmakuControllerImpl(
    private val clockProvider: PlayerClockProvider,
    private val eventSource: PlayerEventSource
) : DanmakuController {

    private val repository = DanmakuRepository()
    private val engine: DanmakuEngine = DanmakuTimelineEngine()
    private val renderer = CanvasRenderer()
    private var clockBridge: MpvClockBridge? = null
    private var overlayHost: DanmakuOverlayHost? = null
    private var visible = true
    private var source: DanmakuDataSource? = null

    override fun attach(host: DanmakuOverlayHost) {
        overlayHost = host
        renderer.initialize(host)
    }

    override fun detach() {
        clockBridge?.stop()
        clockBridge = null
        overlayHost = null
    }

    override fun setSource(source: DanmakuDataSource) {
        this.source = source
    }

    override fun load() {
        val src = source ?: return
        val host = overlayHost ?: return

        // 停止之前的桥接（防止重复加载）
        clockBridge?.stop()
        clockBridge = null

        // 异步解析
        CoroutineScope(Dispatchers.IO).launch {
            val parser = BilibiliXmlParser()
            val items = when (src) {
                is DanmakuDataSource.FilePath -> {
                    val file = File(src.path)
                    if (file.exists()) parser.parse(FileInputStream(file)) else emptyList()
                }
                is DanmakuDataSource.Stream -> parser.parse(src.inputStream)
            }

            repository.load(items)

            withContext(Dispatchers.Main) {
                engine.initialize(repository, host.width(), host.height())
                engine.onClockTick(clockProvider.currentPositionMs())
                renderer.prefetch(engine.snapshot())

                val bridge = MpvClockBridge(clockProvider, eventSource, engine)
                clockBridge = bridge

                bridge.start(
                    onRender = { snapshot ->
                        if (visible) {
                            renderer.render(clockProvider.currentPositionMs(), snapshot)

                            // P1: 帧预算监控 — 将降级状态同步给引擎
                            val monitor = renderer.getFrameBudgetMonitor()
                            if (engine is DanmakuTimelineEngine) {
                                engine.setFrameBudgetDegraded(monitor.isDegraded)
                            }
                        }
                    },
                    onCanvasSize = { Pair(renderer.canvasWidth, renderer.canvasHeight) }
                )
            }
        }
    }

    override fun clear() {
        engine.clear()
        repository.clear()
    }

    override fun play() {
        clockBridge?.resume()
    }

    override fun pause() {
        clockBridge?.pause()
    }

    override fun seekTo(positionMs: Long) {
        engine.onPlayerEvent(PlayerEvent.SeekStarted)
        engine.onPlayerEvent(PlayerEvent.SeekEnded)
    }

    override fun updateStyle(style: GlobalDanmakuStyle) {
        engine.updateStyle(style)
    }

    override fun setVisibility(visible: Boolean) {
        this.visible = visible
        if (!visible) {
            overlayHost?.invalidateFrame()
        }
    }

    /**
     * P1: 低内存回调
     *
     * 在 Activity/Fragment 的 onTrimMemory 中调用，
     * 通知引擎缩减对象池，释放缓存。
     */
    fun onTrimMemory() {
        if (engine is DanmakuTimelineEngine) {
            engine.onTrimMemory()
        }
    }

    /**
     * P1: 获取帧性能统计
     *
     * 可用于调试面板显示当前帧耗时、是否降级等信息。
     */
    fun getFrameStats(): FrameStats = renderer.getFrameStats()

    override fun release() {
        clockBridge?.stop()
        clockBridge = null
        renderer.release()
        engine.release()
        repository.clear()
    }
}

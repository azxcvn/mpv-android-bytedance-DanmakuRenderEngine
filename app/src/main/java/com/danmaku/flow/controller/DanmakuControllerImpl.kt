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
import com.danmaku.flow.renderer.CanvasRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

/**
 * 弹幕控制器实现
 *
 * 负责组装各模块并向宿主暴露 API。
 * 对应融合方案 7.2 节和 9.1.1 第 7 步
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
                // 初始化引擎
                engine.initialize(repository, host.width(), host.height())

                // 创建并启动时钟桥接
                val bridge = MpvClockBridge(clockProvider, eventSource, engine)
                clockBridge = bridge

                bridge.start(
                    onRender = { snapshot ->
                        if (visible) {
                            renderer.render(clockProvider.currentPositionMs(), snapshot)
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
            overlayHost?.invalidateFrame() // 清空画布
        }
    }

    override fun release() {
        clockBridge?.stop()
        clockBridge = null
        renderer.release()
        engine.release()
        repository.clear()
    }
}

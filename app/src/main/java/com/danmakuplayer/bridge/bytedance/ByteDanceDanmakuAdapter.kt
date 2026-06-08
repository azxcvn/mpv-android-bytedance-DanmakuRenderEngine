package com.danmakuplayer.bridge.bytedance

import android.util.Log
import com.bytedance.danmaku.render.engine.DanmakuView
import com.bytedance.danmaku.render.engine.control.DanmakuController
import com.danmakuplayer.bridge.api.PlayerClockProvider
import com.danmakuplayer.bridge.api.PlayerEvent
import com.danmakuplayer.bridge.api.PlayerEventListener
import com.danmakuplayer.bridge.api.PlayerEventSource
import com.danmakuplayer.model.DanmakuItem
import com.danmakuplayer.model.DanmakuType
import com.danmakuplayer.model.DensityMode
import com.danmakuplayer.model.GlobalDanmakuStyle
import com.bytedance.danmaku.render.engine.render.draw.text.TextData
import android.graphics.Color
import com.bytedance.danmaku.render.engine.utils.LAYER_TYPE_SCROLL
import com.bytedance.danmaku.render.engine.utils.LAYER_TYPE_TOP_CENTER
import com.bytedance.danmaku.render.engine.utils.LAYER_TYPE_BOTTOM_CENTER

/**
 * 字节跳动 DanmakuRenderEngine 适配器
 *
 * 负责：
 * 1. 将 BilibiliXmlParser 解析出的 DanmakuItem 转换为 ByteDance TextData
 * 2. 桥接 mpv 播放器事件到 ByteDance DanmakuController
 * 3. 映射样式配置
 */
class ByteDanceDanmakuAdapter(
    private val clockProvider: PlayerClockProvider,
    private val eventSource: PlayerEventSource
) {

    companion object {
        private const val TAG = "ByteDanceDanmakuAdapter"
        /** 字节 demo 默认 moveTime=8000ms，弹幕从右到左滚完全程的时间 */
        private const val DEFAULT_MOVE_TIME = 8000L
        /** 字节 demo 默认字号 48px */
        private const val DEFAULT_TEXT_SIZE = 48f
        /** 字节 demo 默认行高 54px */
        private const val DEFAULT_LINE_HEIGHT = 54f
        /** 字节 demo 默认行间距 18px */
        private const val DEFAULT_LINE_MARGIN = 18f
        /** 字节 demo 默认描边 2.75px */
        private const val DEFAULT_STROKE_WIDTH = 2.75f
    }

    private var danmakuView: DanmakuView? = null
    private var controller: DanmakuController? = null
    private var isAttached = false
    private var dataLoaded = false
    private var currentStyle = GlobalDanmakuStyle()
    private var visible = true

    private val playerListener = object : PlayerEventListener {
        override fun onPlayerEvent(event: PlayerEvent) {
            when (event) {
                is PlayerEvent.PlayStateChanged -> {
                    if (event.isPlaying) {
                        controller?.start(clockProvider.currentPositionMs())
                    } else {
                        controller?.pause()
                    }
                }
                is PlayerEvent.SeekStarted -> {
                    controller?.pause()
                }
                is PlayerEvent.SeekEnded -> {
                    controller?.clear()
                    if (clockProvider.isPlaying()) {
                        controller?.start(clockProvider.currentPositionMs())
                    }
                }
                is PlayerEvent.SpeedChanged -> {
                    controller?.config?.common?.playSpeed = (event.speed * 100).toInt()
                    // Re-sync time anchor after speed change
                    if (clockProvider.isPlaying()) {
                        controller?.pause()
                        controller?.start(clockProvider.currentPositionMs())
                    }
                }
                is PlayerEvent.PlaybackEnded -> {
                    controller?.pause()
                }
                is PlayerEvent.PositionChanged -> {
                    // No-op: ByteDance engine tracks time internally via wall clock
                }
            }
        }
    }

    fun attach(view: DanmakuView) {
        danmakuView = view
        controller = view.controller
        isAttached = true
        eventSource.addListener(playerListener)
        applyStyle(currentStyle)
    }

    fun detach() {
        eventSource.removeListener(playerListener)
        controller?.stop()
        controller = null
        danmakuView = null
        isAttached = false
        dataLoaded = false
    }

    fun loadData(items: List<DanmakuItem>) {
        val ctrl = controller ?: return
        val textDataList = items.map { convertToTextData(it) }
            .sortedBy { it.showAtTime }
        ctrl.setData(textDataList)
        dataLoaded = true

        // If player is already playing, start immediately
        if (clockProvider.isPlaying()) {
            ctrl.start(clockProvider.currentPositionMs())
        }

        Log.i(TAG, "Loaded ${textDataList.size} danmaku items")
    }

    fun updateStyle(style: GlobalDanmakuStyle) {
        currentStyle = style
        if (isAttached) {
            applyStyle(style)
        }
    }

    fun setVisibility(v: Boolean) {
        visible = v
        danmakuView?.visibility = if (v) android.view.View.VISIBLE else android.view.View.GONE
    }

    fun play() {
        controller?.start(clockProvider.currentPositionMs())
    }

    fun pause() {
        controller?.pause()
    }

    fun seekTo(positionMs: Long) {
        controller?.clear()
        if (clockProvider.isPlaying()) {
            controller?.start(positionMs)
        }
    }

    fun clear() {
        controller?.clear()
    }

    fun release() {
        detach()
    }

    private fun convertToTextData(item: DanmakuItem): TextData {
        return TextData().apply {
            text = item.text
            showAtTime = item.timelineMs
            textColor = item.color
            // 不在此处设 textSize，让 config.text.size 全局统一控制
            // 每条弹幕的 textSize 留 null 即可走 config 默认值
            layerType = when (item.type) {
                DanmakuType.ScrollRtl -> LAYER_TYPE_SCROLL
                DanmakuType.TopFixed -> LAYER_TYPE_TOP_CENTER
                DanmakuType.BottomFixed -> LAYER_TYPE_BOTTOM_CENTER
                DanmakuType.Special -> LAYER_TYPE_SCROLL
            }
        }
    }

    private fun applyStyle(style: GlobalDanmakuStyle) {
        val ctrl = controller ?: return
        val config = ctrl.config

        // === 字号 ===
        // scale=1.0 → 48px（字节默认），scale=2.0 → 96px，scale=0.5 → 24px
        val textSize = DEFAULT_TEXT_SIZE * style.scale
        config.text.size = textSize

        // === 行高 ===
        // 行高随字号等比缩放，保证文字不被裁切
        val lineHeight = (textSize + 8f).coerceAtLeast(DEFAULT_LINE_HEIGHT * style.scale)
        config.scroll.lineHeight = lineHeight
        config.top.lineHeight = lineHeight
        config.bottom.lineHeight = lineHeight

        // === 行间距 ===
        config.scroll.lineMargin = DEFAULT_LINE_MARGIN
        config.top.lineMargin = DEFAULT_LINE_MARGIN
        config.bottom.lineMargin = DEFAULT_LINE_MARGIN

        // === 速度 ===
        // speedFactor=1.0 → 8000ms（字节默认），2.0 → 4000ms（快），0.3 → 26666ms（慢）
        config.scroll.moveTime = (DEFAULT_MOVE_TIME / style.speedFactor).toLong()
            .coerceIn(2000L, 30000L)

        // === 透明度 ===
        // alpha 0.0~1.0 → ByteDance 0~255，应用到 View.alpha
        config.common.alpha = (style.alpha * 255).toInt().coerceIn(0, 255)

        // === 描边 ===
        // strokeWidthDp=2.0 → 2.75px（字节默认），0 → 0px，5.0 → 6.875px
        config.text.strokeWidth = style.strokeWidthDp * DEFAULT_STROKE_WIDTH / 2f
        // 描边颜色使用不透明黑色，避免与 View.alpha 叠加导致透明度异常
        // （引擎默认 Color.argb(97,0,0,0) 本身就是半透明的，再乘 View.alpha 会更透明）
        config.text.strokeColor = Color.argb(255, 0, 0, 0)

        // === 密度/缓冲区优化（影响帧率） ===
        // 减少缓冲区大小可降低每帧排版计算量，提升帧率
        when (style.densityMode) {
            DensityMode.Strict -> {
                config.scroll.bufferSize = 4
                config.scroll.itemMargin = 48f
                config.top.bufferSize = 2
                config.bottom.bufferSize = 2
            }
            DensityMode.Balanced -> {
                config.scroll.bufferSize = 8
                config.scroll.itemMargin = 24f
                config.top.bufferSize = 4
                config.bottom.bufferSize = 4
            }
            DensityMode.Crowded -> {
                config.scroll.bufferSize = 16
                config.scroll.itemMargin = 12f
                config.top.bufferSize = 6
                config.bottom.bufferSize = 6
            }
        }

        // === 空屏暂停刷新 ===
        config.common.pauseInvalidateWhenBlank = true

        // === 更新行数 ===
        danmakuView?.let { view ->
            if (view.height > 0) {
                updateLineCount(view.height)
            }
        }
    }

    /**
     * Call this when the DanmakuView has been laid out and we know its height.
     * Calculates and sets the optimal line count to fill the screen.
     */
    fun updateLineCount(viewHeight: Int) {
        val ctrl = controller ?: return
        val config = ctrl.config
        val lineHeight = config.scroll.lineHeight
        val lineMargin = config.scroll.lineMargin
        val marginTop = config.scroll.marginTop

        val availableHeight = viewHeight - marginTop
        val autoScrollLines = if (lineHeight + lineMargin > 0) {
            (availableHeight / (lineHeight + lineMargin)).toInt().coerceIn(1, 30)
        } else {
            4
        }

        // 如果用户设了最大行数则取较小值，否则自动填满
        val scrollLineCount = if (currentStyle.scrollMaxLines > 0) {
            autoScrollLines.coerceAtMost(currentStyle.scrollMaxLines)
        } else {
            autoScrollLines
        }
        config.scroll.lineCount = scrollLineCount

        // 顶部/底部固定弹幕
        val autoFixedLines = (autoScrollLines / 4).coerceIn(1, 6)
        config.top.lineCount = if (currentStyle.topMaxLines > 0) {
            autoFixedLines.coerceAtMost(currentStyle.topMaxLines)
        } else {
            autoFixedLines
        }
        config.bottom.lineCount = if (currentStyle.bottomMaxLines > 0) {
            autoFixedLines.coerceAtMost(currentStyle.bottomMaxLines)
        } else {
            autoFixedLines
        }
        config.top.lineHeight = config.scroll.lineHeight
        config.bottom.lineHeight = config.scroll.lineHeight

        Log.d(TAG, "View height=$viewHeight, lineHeight=$lineHeight, scrollLines=$scrollLineCount, topLines=${config.top.lineCount}, bottomLines=${config.bottom.lineCount}")
    }
}

package com.danmakuplayer.manager

import android.content.Context
import android.os.SystemClock
import android.view.Surface
import `is`.xyz.mpv.MPV
import `is`.xyz.mpv.MPVNode
import com.danmaku.flow.bridge.api.PlayerClockProvider
import com.danmaku.flow.bridge.api.PlayerEvent
import com.danmaku.flow.bridge.api.PlayerEventSource
import com.danmaku.flow.bridge.api.PlayerEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * mpv 播放器封装层 + 桥接层实现
 *
 * 实现 [PlayerClockProvider] 和 [PlayerEventSource]，
 * 供弹幕引擎获取播放时钟和订阅播放器事件。
 *
 * 对应融合方案中的「播放器桥接层」
 */
class MpvPlayerManager(private val context: Context) :
    PlayerClockProvider, PlayerEventSource {

    private var mpv: MPV? = null

    /** 暴露 MPV 实例给 SurfaceView 绑定 */
    val mpvInstance: MPV? get() = mpv

    // 事件监听器
    private val listeners = mutableListOf<PlayerEventListener>()

    // === 状态暴露 ===

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _isAnime4KEnabled = MutableStateFlow(false)
    val isAnime4KEnabled: StateFlow<Boolean> = _isAnime4KEnabled.asStateFlow()

    private var clockAnchorPositionMs = 0L
    private var clockAnchorRealtimeNs = 0L
    private var lastClockOutputMs = 0L
    private var lastClockOutputRealtimeNs = 0L

    // PlayerClockProvider 实现
    override fun currentPositionMs(): Long {
        val nowNs = SystemClock.elapsedRealtimeNanos()
        if (!_isPlaying.value) {
            return updateClockOutput(_currentPositionMs.value, nowNs)
        }

        if (clockAnchorRealtimeNs == 0L) {
            return updateClockOutput(_currentPositionMs.value, nowNs)
        }

        val elapsedMs = ((nowNs - clockAnchorRealtimeNs) / 1_000_000.0 * _playbackSpeed.value).toLong()
        val predictedPositionMs = clockAnchorPositionMs + elapsedMs
        if (lastClockOutputRealtimeNs == 0L) {
            return updateClockOutput(predictedPositionMs.coerceAtLeast(_currentPositionMs.value), nowNs)
        }

        val outputElapsedMs = ((nowNs - lastClockOutputRealtimeNs) / 1_000_000.0 * _playbackSpeed.value).toLong()
        val maxAdvanceMs = lastClockOutputMs + outputElapsedMs + 8L
        val smoothedPositionMs = predictedPositionMs
            .coerceAtLeast(lastClockOutputMs)
            .coerceAtMost(maxAdvanceMs.coerceAtLeast(_currentPositionMs.value))

        return updateClockOutput(smoothedPositionMs, nowNs)
    }
    override fun isPlaying(): Boolean = _isPlaying.value
    override fun playbackSpeed(): Float = _playbackSpeed.value

    private val _playbackSpeed = MutableStateFlow(1f)

    // PlayerEventSource 实现
    override fun addListener(listener: PlayerEventListener) {
        synchronized(listeners) { listeners.add(listener) }
    }
    override fun removeListener(listener: PlayerEventListener) {
        synchronized(listeners) { listeners.remove(listener) }
    }
    private fun notifyListeners(event: PlayerEvent) {
        synchronized(listeners) { listeners.toList() }.forEach { it.onPlayerEvent(event) }
    }

    private val _videoWidth = MutableStateFlow(0)
    val videoWidth: StateFlow<Int> = _videoWidth.asStateFlow()

    private val _videoHeight = MutableStateFlow(0)
    val videoHeight: StateFlow<Int> = _videoHeight.asStateFlow()

    // === 生命周期 ===

    /**
     * 初始化 mpv 实例
     */
    fun initialize() {
        val instance = MPV()
        instance.create(context)
        instance.setOptionString("config", "yes")
        instance.setOptionString("hwdec", "auto")
        instance.setOptionString("gpu-context", "android")
        instance.setOptionString("vo", "gpu")
        instance.setOptionString("profile", "gpu-hq")
        instance.setOptionString("cache", "yes")
        instance.setOptionString("demuxer-max-bytes", "150M")
        instance.setOptionString("demuxer-max-back-bytes", "75M")
        instance.init()

        // 观察关键属性
        instance.observeProperty("time-pos", MPV.mpvFormat.MPV_FORMAT_DOUBLE)
        instance.observeProperty("duration", MPV.mpvFormat.MPV_FORMAT_DOUBLE)
        instance.observeProperty("pause", MPV.mpvFormat.MPV_FORMAT_FLAG)
        instance.observeProperty("speed", MPV.mpvFormat.MPV_FORMAT_DOUBLE)
        instance.observeProperty("video-params", MPV.mpvFormat.MPV_FORMAT_NODE)
        instance.observeProperty("eof-reached", MPV.mpvFormat.MPV_FORMAT_FLAG)

        // 注册事件观察者
        instance.addObserver(object : MPV.EventObserver {
            override fun eventProperty(property: String) {}
            override fun eventProperty(property: String, value: Long) {
                onPropertyChanged(property, value)
            }
            override fun eventProperty(property: String, value: Boolean) {
                onPropertyChanged(property, value)
            }
            override fun eventProperty(property: String, value: String) {
                onPropertyChanged(property, value)
            }
            override fun eventProperty(property: String, value: Double) {
                onPropertyChanged(property, value)
            }
            override fun eventProperty(property: String, value: MPVNode) {
                onPropertyChanged(property, value)
            }
            override fun event(eventId: Int, data: MPVNode) {
                onMpvEvent(eventId)
            }
        })

        mpv = instance
    }

    /**
     * 绑定 Surface（由 BaseMPVView/SurfaceView 提供）
     */
    fun attachSurface(surface: Surface) {
        mpv?.attachSurface(surface)
    }

    /**
     * 解绑 Surface
     */
    fun detachSurface() {
        mpv?.let {
            it.setPropertyString("vo", "null")
            it.detachSurface()
        }
    }

    // === 播放控制 ===

    /**
     * 加载并播放视频文件
     */
    fun loadFile(path: String) {
        mpv?.command("loadfile", path)
        // mpv 默认以 pause=true 初始化，需要手动取消暂停
        mpv?.setPropertyBoolean("pause", false)
        _isPlaying.value = true
    }

    /**
     * 加载并播放网络流
     */
    fun loadUrl(url: String) {
        mpv?.command("loadfile", url)
        mpv?.setPropertyBoolean("pause", false)
        _isPlaying.value = true
    }

    fun play() {
        mpv?.setPropertyBoolean("pause", false)
        _isPlaying.value = true
    }

    fun pause() {
        mpv?.setPropertyBoolean("pause", true)
        _isPlaying.value = false
    }

    fun togglePlayPause() {
        if (_isPlaying.value) pause() else play()
    }

    /**
     * 快进/快退（相对 seek）
     */
    fun seekBy(offsetMs: Long) {
        notifyListeners(PlayerEvent.SeekStarted)
        mpv?.command("seek", "${offsetMs / 1000f}", "relative")
        notifyListeners(PlayerEvent.SeekEnded)
    }

    /**
     * 跳转到指定位置（绝对 seek）
     */
    fun seekTo(positionMs: Long) {
        notifyListeners(PlayerEvent.SeekStarted)
        mpv?.command("seek", "${positionMs / 1000f}", "absolute")
        notifyListeners(PlayerEvent.SeekEnded)
    }

    fun stepForward() {
        mpv?.command("frame-step")
    }

    fun stepBackward() {
        mpv?.command("frame-back-step")
    }

    // === Anime4K ===

    /**
     * 切换 Anime4K 超分开关
     *
     * 注意：实际使用需要将 Anime4K.glsl 放在设备可访问路径，
     * 首次开启时通过 change-list 添加 shader。
     * 此处为占位实现，后续可扩展为实际 shader 管理。
     */
    fun toggleAnime4K() {
        val enabled = !_isAnime4KEnabled.value
        val instance = mpv ?: return

        if (enabled) {
            // 启用 Anime4K：添加 shader（路径按实际部署位置修改）
            instance.command(
                "change-list", "glsl-shaders", "append",
                context.filesDir.resolve("anime4k/Anime4K_Clamp_HQ.glsl").absolutePath
            )
            instance.command(
                "change-list", "glsl-shaders", "append",
                context.filesDir.resolve("anime4k/Anime4K_Restore_CNN_Soft.glsl").absolutePath
            )
            instance.command(
                "change-list", "glsl-shaders", "append",
                context.filesDir.resolve("anime4k/Anime4K_Upscale_CNN_x2.glsl").absolutePath
            )
            instance.command(
                "change-list", "glsl-shaders", "append",
                context.filesDir.resolve("anime4k/Anime4K_AutoDownscalePre_x2.glsl").absolutePath
            )
            instance.command(
                "change-list", "glsl-shaders", "append",
                context.filesDir.resolve("anime4k/Anime4K_Denoise_Bilateral_Moderate.glsl").absolutePath
            )
        } else {
            // 禁用：清空所有 shader
            instance.command("change-list", "glsl-shaders", "clr", "")
        }

        _isAnime4KEnabled.value = enabled
    }

    // === 属性 ===

    fun getCurrentPosition(): Long = mpv?.getPropertyDouble("time-pos")?.let {
        (it * 1000).toLong()
    } ?: 0L

    // === 释放 ===

    fun release() {
        mpv?.let { instance ->
            instance.setPropertyBoolean("pause", true)
            instance.destroy()
        }
        mpv = null
        clockAnchorPositionMs = 0L
        clockAnchorRealtimeNs = 0L
        lastClockOutputMs = 0L
        lastClockOutputRealtimeNs = 0L
        _isPlaying.value = false
        _currentPositionMs.value = 0
        _durationMs.value = 0
    }

    // === 内部事件处理 ===

    private fun onPropertyChanged(property: String, value: Double) {
        when (property) {
            "time-pos" -> {
                val positionMs = (value * 1000).toLong()
                _currentPositionMs.value = positionMs
                updateClockAnchor(positionMs)
                notifyListeners(PlayerEvent.PositionChanged(positionMs))
            }
            "duration" -> _durationMs.value = (value * 1000).toLong()
            "speed" -> {
                _playbackSpeed.value = value.toFloat()
                updateClockAnchor(_currentPositionMs.value)
                notifyListeners(PlayerEvent.SpeedChanged(value.toFloat()))
            }
        }
    }

    private fun onPropertyChanged(property: String, value: Boolean) {
        when (property) {
            "pause" -> {
                val playing = !value
                _isPlaying.value = playing
                updateClockAnchor(_currentPositionMs.value)
                notifyListeners(PlayerEvent.PlayStateChanged(playing))
            }
            "eof-reached" -> {
                if (value) {
                    _isPlaying.value = false
                    updateClockAnchor(_currentPositionMs.value)
                    notifyListeners(PlayerEvent.PlaybackEnded)
                }
            }
        }
    }

    private fun updateClockAnchor(positionMs: Long) {
        clockAnchorPositionMs = positionMs
        clockAnchorRealtimeNs = SystemClock.elapsedRealtimeNanos()
    }

    private fun updateClockOutput(positionMs: Long, nowNs: Long): Long {
        lastClockOutputMs = positionMs
        lastClockOutputRealtimeNs = nowNs
        return positionMs
    }

    private fun onPropertyChanged(property: String, value: Long) {
        // 预留
    }

    private fun onPropertyChanged(property: String, value: String) {
        // 预留
    }

    private fun onPropertyChanged(property: String, value: MPVNode) {
        when (property) {
            "video-params" -> {
                // 解析视频分辨率
                val map = value.asMap() ?: return
                _videoWidth.value = map["w"]?.asInt()?.toInt() ?: 0
                _videoHeight.value = map["h"]?.asInt()?.toInt() ?: 0
            }
        }
    }

    private fun onMpvEvent(eventId: Int) {
        when (eventId) {
            MPV.mpvEvent.MPV_EVENT_END_FILE -> {
                _isPlaying.value = false
            }
        }
    }
}

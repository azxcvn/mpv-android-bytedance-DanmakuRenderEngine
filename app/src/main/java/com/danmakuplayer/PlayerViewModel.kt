package com.danmakuplayer

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import com.bytedance.danmaku.render.engine.DanmakuView
import com.danmakuplayer.manager.MpvPlayerManager
import com.danmakuplayer.bridge.bytedance.ByteDanceDanmakuAdapter
import com.danmakuplayer.model.DensityMode
import com.danmakuplayer.model.GlobalDanmakuStyle
import com.danmakuplayer.parser.BilibiliXmlParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    val playerManager = MpvPlayerManager(application)

    /** 字节跳动弹幕适配器 */
    var danmakuAdapter: ByteDanceDanmakuAdapter? = null
        private set

    private val _currentVideoUri = MutableStateFlow<Uri?>(null)
    val currentVideoUri: StateFlow<Uri?> = _currentVideoUri.asStateFlow()

    private val _danmakuVisible = MutableStateFlow(true)
    val danmakuVisible: StateFlow<Boolean> = _danmakuVisible.asStateFlow()

    private val _isSeeking = MutableStateFlow(false)
    val isSeeking: StateFlow<Boolean> = _isSeeking.asStateFlow()

    private val _seekingPosition = MutableStateFlow(0L)
    val seekingPosition: StateFlow<Long> = _seekingPosition.asStateFlow()

    val isPlaying = playerManager.isPlaying
    val currentPositionMs = playerManager.currentPositionMs
    val durationMs = playerManager.durationMs
    val isAnime4KEnabled = playerManager.isAnime4KEnabled

    private val _densityMode = MutableStateFlow(DensityMode.Crowded)
    val densityMode: StateFlow<DensityMode> = _densityMode.asStateFlow()

    private val _danmakuScale = MutableStateFlow(1f)
    val danmakuScale: StateFlow<Float> = _danmakuScale.asStateFlow()

    private val _danmakuSpeed = MutableStateFlow(1f)
    val danmakuSpeed: StateFlow<Float> = _danmakuSpeed.asStateFlow()

    private val _danmakuAlpha = MutableStateFlow(0.85f)
    val danmakuAlpha: StateFlow<Float> = _danmakuAlpha.asStateFlow()

    private val _danmakuStroke = MutableStateFlow(2f)
    val danmakuStroke: StateFlow<Float> = _danmakuStroke.asStateFlow()

    private var currentDanmakuStyle = GlobalDanmakuStyle()
    private var playerInitialized = false
    private var pendingUri: Uri? = null
    private var pendingDanmakuPath: String? = null

    fun openVideo(uri: Uri) {
        ensurePlayerInitialized()
        _currentVideoUri.value = uri
        pendingUri = uri
    }

    private fun ensurePlayerInitialized() {
        if (playerInitialized) return
        playerManager.initialize()
        playerInitialized = true
    }

    fun playCurrentVideo() {
        val uri = pendingUri ?: return
        pendingUri = null
        playerManager.loadFile(uri.toString())
    }

    fun pause() { playerManager.pause() }
    fun togglePlayPause() { playerManager.togglePlayPause() }
    fun seekBy(offsetMs: Long) { playerManager.seekBy(offsetMs) }

    fun onSeekStart() { _isSeeking.value = true }
    fun onSeek(positionMs: Long) { _seekingPosition.value = positionMs }
    fun onSeekEnd(positionMs: Long) {
        playerManager.seekTo(positionMs)
        _isSeeking.value = false
    }

    fun toggleAnime4K() { playerManager.toggleAnime4K() }

    fun toggleDanmaku() {
        val newVisible = !_danmakuVisible.value
        _danmakuVisible.value = newVisible
        danmakuAdapter?.setVisibility(newVisible)
    }

    fun setDensityMode(mode: DensityMode) {
        _densityMode.value = mode
        currentDanmakuStyle = currentDanmakuStyle.copy(densityMode = mode)
        danmakuAdapter?.updateStyle(currentDanmakuStyle)
    }

    fun setDanmakuScale(scale: Float) {
        _danmakuScale.value = scale
        currentDanmakuStyle = currentDanmakuStyle.copy(scale = scale)
        danmakuAdapter?.updateStyle(currentDanmakuStyle)
    }

    fun setDanmakuSpeed(speed: Float) {
        _danmakuSpeed.value = speed
        currentDanmakuStyle = currentDanmakuStyle.copy(speedFactor = speed)
        danmakuAdapter?.updateStyle(currentDanmakuStyle)
    }

    fun setDanmakuAlpha(alpha: Float) {
        _danmakuAlpha.value = alpha
        currentDanmakuStyle = currentDanmakuStyle.copy(alpha = alpha)
        danmakuAdapter?.updateStyle(currentDanmakuStyle)
    }

    fun setDanmakuStroke(stroke: Float) {
        _danmakuStroke.value = stroke
        currentDanmakuStyle = currentDanmakuStyle.copy(strokeWidthDp = stroke)
        danmakuAdapter?.updateStyle(currentDanmakuStyle)
    }

    fun loadDanmaku(path: String) {
        pendingDanmakuPath = path
        tryLoadDanmaku()
    }

    /**
     * 绑定 DanmakuView 并创建适配器
     */
    fun attachDanmakuView(view: DanmakuView) {
        ensurePlayerInitialized()

        danmakuAdapter?.release()

        val adapter = ByteDanceDanmakuAdapter(playerManager, playerManager)
        adapter.attach(view)
        adapter.updateStyle(currentDanmakuStyle)
        adapter.setVisibility(_danmakuVisible.value)
        danmakuAdapter = adapter

        view.post {
            adapter.updateLineCount(view.height)
        }

        tryLoadDanmaku()
    }

    private fun tryLoadDanmaku() {
        val path = pendingDanmakuPath ?: return
        val adapter = danmakuAdapter ?: return
        pendingDanmakuPath = null

        CoroutineScope(Dispatchers.IO).launch {
            val parser = BilibiliXmlParser()
            val file = File(path)
            if (!file.exists()) return@launch
            val items = parser.parse(FileInputStream(file))
            withContext(Dispatchers.Main) {
                adapter.loadData(items)
            }
        }
    }

    fun onTrimMemory() { }

    override fun onCleared() {
        super.onCleared()
        danmakuAdapter?.release()
        danmakuAdapter = null
        playerManager.release()
    }
}

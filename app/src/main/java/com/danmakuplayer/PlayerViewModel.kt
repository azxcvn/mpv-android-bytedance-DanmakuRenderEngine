package com.danmakuplayer

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.danmakuplayer.manager.MpvPlayerManager
import com.danmaku.flow.controller.DanmakuController
import com.danmaku.flow.controller.DanmakuControllerImpl
import com.danmaku.flow.controller.DanmakuDataSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 播放器 ViewModel
 *
 * 职责：
 * - 管理 MpvPlayerManager 生命周期
 * - 暴露 UI 状态（播放状态、时间、Anime4K、弹幕显隐）
 * - 处理用户交互事件
 *
 * 对应融合方案中的「配置与控制层」雏形
 */
class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    val playerManager = MpvPlayerManager(application)

    // 弹幕控制器（在 mpv 初始化后创建）
    var danmakuController: DanmakuController? = null
        private set

    // === UI 状态 ===

    /** 当前播放的视频 URI（用于显示文件名） */
    private val _currentVideoUri = MutableStateFlow<Uri?>(null)
    val currentVideoUri: StateFlow<Uri?> = _currentVideoUri.asStateFlow()

    /** 弹幕显隐开关 */
    private val _danmakuVisible = MutableStateFlow(true)
    val danmakuVisible: StateFlow<Boolean> = _danmakuVisible.asStateFlow()

    /** 是否正在拖动进度条 */
    private val _isSeeking = MutableStateFlow(false)
    val isSeeking: StateFlow<Boolean> = _isSeeking.asStateFlow()

    /** 拖动中的临时时间位置 */
    private val _seekingPosition = MutableStateFlow(0L)
    val seekingPosition: StateFlow<Long> = _seekingPosition.asStateFlow()

    // 代理播放器状态
    val isPlaying = playerManager.isPlaying
    val currentPositionMs = playerManager.currentPositionMs
    val durationMs = playerManager.durationMs
    val isAnime4KEnabled = playerManager.isAnime4KEnabled

    // === 初始化 ===

    init {
        viewModelScope.launch {
            playerManager.initialize()
            // mpv 就绪后创建弹幕控制器
            danmakuController = DanmakuControllerImpl(playerManager, playerManager)
        }
    }

    /** 待播放的 URI（Surface 就绪后才真正加载） */
    private var pendingUri: Uri? = null

    /** 待加载的弹幕文件路径 */
    private var pendingDanmakuPath: String? = null
    /** 已加载的弹幕源（用于 Surface 就绪后重试） */
    private var currentDanmakuSource: DanmakuDataSource? = null
    /** OverlayHost 引用 */
    private var overlayHost: com.danmaku.flow.bridge.api.DanmakuOverlayHost? = null

    // === 播放控制 ===

    /**
     * 保存视频 URI 并导航到播放器
     * 实际加载在 [playCurrentVideo] 中执行（等 Surface 就绪后）
     */
    fun openVideo(uri: Uri) {
        _currentVideoUri.value = uri
        pendingUri = uri
    }

    /**
     * Surface 就绪后调用，真正加载视频
     */
    fun playCurrentVideo() {
        val uri = pendingUri ?: return
        pendingUri = null
        playerManager.loadFile(uri.toString())
    }

    fun pause() {
        playerManager.pause()
    }

    fun togglePlayPause() {
        playerManager.togglePlayPause()
    }

    fun seekBy(offsetMs: Long) {
        playerManager.seekBy(offsetMs)
    }

    fun onSeekStart() {
        _isSeeking.value = true
    }

    fun onSeek(positionMs: Long) {
        _seekingPosition.value = positionMs
    }

    fun onSeekEnd(positionMs: Long) {
        playerManager.seekTo(positionMs)
        _isSeeking.value = false
    }

    // === Anime4K ===

    fun toggleAnime4K() {
        playerManager.toggleAnime4K()
    }

    // === 弹幕 ===

    fun toggleDanmaku() {
        val newVisible = !_danmakuVisible.value
        _danmakuVisible.value = newVisible
        danmakuController?.setVisibility(newVisible)
    }

    /**
     * 加载弹幕文件
     */
    fun loadDanmaku(path: String) {
        pendingDanmakuPath = path
        // 如果 overlay 已就绪，立即加载
        attachPendingDanmaku()
    }

    /**
     * 当 OverlayHost 就绪后调用
     */
    fun attachDanmakuOverlay(host: com.danmaku.flow.bridge.api.DanmakuOverlayHost) {
        overlayHost = host
        danmakuController?.attach(host)
        attachPendingDanmaku()
    }

    /**
     * Surface 就绪后重试加载（解决 Surface 未创建时 load 失败的问题）
     */
    fun retryLoadDanmaku() {
        val controller = danmakuController ?: return
        val source = currentDanmakuSource ?: return
        val host = overlayHost ?: return
        // 重新绑定并加载
        controller.detach()
        controller.attach(host)
        controller.setSource(source)
        controller.load()
    }

    private fun attachPendingDanmaku() {
        val path = pendingDanmakuPath ?: return
        val controller = danmakuController ?: return
        pendingDanmakuPath = null
        currentDanmakuSource = DanmakuDataSource.FilePath(path)
        controller.setSource(currentDanmakuSource!!)
        controller.load()
    }

    // === 释放 ===

    override fun onCleared() {
        super.onCleared()
        playerManager.release()
    }
}

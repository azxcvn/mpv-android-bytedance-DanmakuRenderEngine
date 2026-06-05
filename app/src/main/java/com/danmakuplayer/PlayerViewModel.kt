package com.danmakuplayer

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.danmakuplayer.manager.MpvPlayerManager
import com.danmaku.flow.controller.DanmakuController
import com.danmaku.flow.controller.DanmakuControllerImpl
import com.danmaku.flow.controller.DanmakuDataSource
import com.danmaku.flow.model.DensityMode
import com.danmaku.flow.model.GlobalDanmakuStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    /** 弹幕密度模式（P1 三档切换） */
    private val _densityMode = MutableStateFlow(DensityMode.Crowded)
    val densityMode: StateFlow<DensityMode> = _densityMode.asStateFlow()

    /** 弹幕字号倍率 0.5~2.0 */
    private val _danmakuScale = MutableStateFlow(1f)
    val danmakuScale: StateFlow<Float> = _danmakuScale.asStateFlow()

    /** 弹幕速度倍率 0.5~2.0 */
    private val _danmakuSpeed = MutableStateFlow(1f)
    val danmakuSpeed: StateFlow<Float> = _danmakuSpeed.asStateFlow()

    /** 弹幕透明度 0.2~1.0 */
    private val _danmakuAlpha = MutableStateFlow(0.85f)
    val danmakuAlpha: StateFlow<Float> = _danmakuAlpha.asStateFlow()

    /** 弹幕描边宽度 dp 0~4 */
    private val _danmakuStroke = MutableStateFlow(2f)
    val danmakuStroke: StateFlow<Float> = _danmakuStroke.asStateFlow()

    /** 当前弹幕样式（累积各项修改） */
    private var currentDanmakuStyle = GlobalDanmakuStyle()

    /** mpv/控制器是否已初始化；首页阶段不提前创建，避免打开系统选择器时带着原生播放器切后台。 */
    private var playerInitialized = false

    // === 初始化 ===

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
        ensurePlayerInitialized()
        _currentVideoUri.value = uri
        pendingUri = uri
    }

    private fun ensurePlayerInitialized() {
        if (playerInitialized) return

        playerManager.initialize()

        val controller = DanmakuControllerImpl(playerManager, playerManager)
        danmakuController = controller
        overlayHost?.let(controller::attach)
        controller.updateStyle(currentDanmakuStyle)
        controller.setVisibility(_danmakuVisible.value)
        attachPendingDanmaku()

        playerInitialized = true
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
     * 切换弹幕密度模式
     * Strict = 弹幕最少，Balanced = 默认，Crowded = 弹幕最多
     */
    fun setDensityMode(mode: DensityMode) {
        _densityMode.value = DensityMode.Crowded
        currentDanmakuStyle = currentDanmakuStyle.copy(
            densityMode = DensityMode.Crowded,
            maxVisibleCount = 180
        )
        danmakuController?.updateStyle(currentDanmakuStyle)
    }

    /** 设置弹幕字号倍率 */
    fun setDanmakuScale(scale: Float) {
        _danmakuScale.value = scale
        currentDanmakuStyle = currentDanmakuStyle.copy(scale = scale)
        danmakuController?.updateStyle(currentDanmakuStyle)
    }

    /** 设置弹幕速度倍率 */
    fun setDanmakuSpeed(speed: Float) {
        _danmakuSpeed.value = speed
        currentDanmakuStyle = currentDanmakuStyle.copy(speedFactor = speed)
        danmakuController?.updateStyle(currentDanmakuStyle)
    }

    /** 设置弹幕透明度 */
    fun setDanmakuAlpha(alpha: Float) {
        _danmakuAlpha.value = alpha
        currentDanmakuStyle = currentDanmakuStyle.copy(alpha = alpha)
        danmakuController?.updateStyle(currentDanmakuStyle)
    }

    /** 设置弹幕描边宽度 dp */
    fun setDanmakuStroke(stroke: Float) {
        _danmakuStroke.value = stroke
        currentDanmakuStyle = currentDanmakuStyle.copy(strokeWidthDp = stroke)
        danmakuController?.updateStyle(currentDanmakuStyle)
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

    /**
     * P1: 低内存回调
     *
     * Activity 的 onTrimMemory 中调用，通知弹幕引擎缩减对象池和缓存。
     */
    fun onTrimMemory() {
        (danmakuController as? DanmakuControllerImpl)?.onTrimMemory()
    }

    override fun onCleared() {
        super.onCleared()
        playerManager.release()
    }
}

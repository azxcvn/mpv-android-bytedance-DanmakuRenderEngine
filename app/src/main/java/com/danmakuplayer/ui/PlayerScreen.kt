package com.danmakuplayer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ClosedCaptionOff
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ShutterSpeed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.danmakuplayer.PlayerViewModel
import com.danmakuplayer.manager.MpvSurfaceView
import com.bytedance.danmaku.render.engine.DanmakuView
import com.danmakuplayer.model.DensityMode
import kotlinx.coroutines.delay

/**
 * 全屏横屏播放器
 *
 * 控制栏仅占屏幕底部极小区域，所有按钮在一行内。
 * 布局：[弹幕][⏪][▶️/⏸][⏩][Anime4K]
 */
@Composable
fun PlayerScreen(viewModel: PlayerViewModel, onBack: () -> Unit) {
    val isPlaying by viewModel.isPlaying.collectAsState()
    val positionMs by viewModel.currentPositionMs.collectAsState()
    val durationMs by viewModel.durationMs.collectAsState()
    val isAnime4K by viewModel.isAnime4KEnabled.collectAsState()
    val danmakuVisible by viewModel.danmakuVisible.collectAsState()
    val densityMode by viewModel.densityMode.collectAsState()
    val danmakuScale by viewModel.danmakuScale.collectAsState()
    val danmakuSpeed by viewModel.danmakuSpeed.collectAsState()
    val danmakuAlpha by viewModel.danmakuAlpha.collectAsState()
    val danmakuStroke by viewModel.danmakuStroke.collectAsState()
    val mpvInstance = remember { viewModel.playerManager.mpvInstance }
    val view = LocalView.current
    val context = LocalContext.current
    var controlsVisible by remember { mutableStateOf(true) }
    var showDensityDialog by remember { mutableStateOf(false) }
    var showStyleDialog by remember { mutableStateOf(false) }

    // 弹幕文件选择器
    val danmakuPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val inputStream = context.contentResolver.openInputStream(it) ?: return@let
            val tmpFile = java.io.File(context.cacheDir, "danmaku.xml")
            tmpFile.outputStream().use { out -> inputStream.copyTo(out) }
            viewModel.loadDanmaku(tmpFile.absolutePath)
        }
    }

    // 沉浸式全屏
    DisposableEffect(Unit) {
        val a = (view.context as? androidx.activity.ComponentActivity)?.window ?: return@DisposableEffect onDispose {}
        val c = WindowCompat.getInsetsController(a, view)
        c.hide(WindowInsetsCompat.Type.systemBars())
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose { c.show(WindowInsetsCompat.Type.systemBars()) }
    }

    // 4秒自动隐藏
    LaunchedEffect(controlsVisible) { if (controlsVisible) { delay(4000); controlsVisible = false } }

    Box(
        Modifier.fillMaxSize().background(Color.Black).clickable(
            indication = null, interactionSource = remember { MutableInteractionSource() }
        ) { controlsVisible = !controlsVisible }
    ) {
        // 视频
        AndroidView(
            factory = { ctx ->
                MpvSurfaceView(ctx).also { sv ->
                    sv.setMpvInstance(mpvInstance)
                    sv.onSurfaceCreatedListener = { viewModel.playCurrentVideo() }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 弹幕叠加层（字节跳动 DanmakuView，普通 View 叠加在视频之上）
        AndroidView(
            factory = { ctx ->
                DanmakuView(ctx).also { view ->
                    viewModel.attachDanmakuView(view)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 控制栏（透明背景，仅底部一条）
        AnimatedVisibility(visible = controlsVisible, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    // 顶栏返回按钮
                    Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp)) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.size(36.dp).background(Color(0x44000000), CircleShape)
                        ) {
                            Icon(Icons.Default.ArrowBack, "返回", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    // 底部控制条（极简）
                    Column(
                        Modifier.fillMaxWidth().background(Color(0xAA000000)).padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        // 进度条
                        val progress = if (durationMs > 0)
                            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                            color = Color(0xFFFF6B35),
                            trackColor = Color(0x66FFFFFF),
                        )

                        Spacer(Modifier.height(6.dp))

                        // 单行：弹幕 | ⏪ | ▶️/⏸ | ⏩ | Anime4K
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            // 加载弹幕文件按钮
                            IconButton(onClick = {
                                danmakuPickerLauncher.launch(arrayOf("text/xml", "application/xml", "*/*"))
                            }, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    Icons.Default.Add, "加载弹幕",
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(Modifier.width(4.dp))

                            // 弹幕图标按钮
                            IconButton(onClick = {
                                viewModel.toggleDanmaku()
                            }, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    Icons.Default.ClosedCaptionOff, "弹幕",
                                    tint = if (danmakuVisible) Color(0xFFFF6B35) else Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(Modifier.width(8.dp))

                            // 快退
                            IconButton(onClick = { viewModel.seekBy(-10000) }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.FastRewind, "快退10秒", tint = Color.White, modifier = Modifier.size(22.dp))
                            }

                            Spacer(Modifier.width(4.dp))

                            // 播放/暂停
                            IconButton(
                                onClick = { viewModel.togglePlayPause() },
                                modifier = Modifier.size(44.dp).background(Color(0xFFFF6B35), CircleShape)
                            ) {
                                Icon(
                                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    if (isPlaying) "暂停" else "播放",
                                    tint = Color.White, modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(Modifier.width(4.dp))

                            // 快进
                            IconButton(onClick = { viewModel.seekBy(10000) }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.FastForward, "快进10秒", tint = Color.White, modifier = Modifier.size(22.dp))
                            }

                            Spacer(Modifier.width(8.dp))

                            // Anime4K 图标按钮
                            IconButton(onClick = {
                                viewModel.toggleAnime4K()
                                android.widget.Toast.makeText(context, "Anime4K待接入", android.widget.Toast.LENGTH_SHORT).show()
                            }, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    Icons.Default.ShutterSpeed, "Anime4K",
                                    tint = if (isAnime4K) Color(0xFFFF6B35) else Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(Modifier.width(8.dp))

                            // 密度模式切换按钮
                            IconButton(onClick = {
                                showDensityDialog = true
                            }, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    Icons.Default.Tune, "弹幕密度",
                                    tint = Color(0xFFFF6B35),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(Modifier.width(4.dp))

                            // 弹幕样式设置按钮
                            IconButton(onClick = {
                                showStyleDialog = true
                            }, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    Icons.Default.Settings, "弹幕样式",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // 时间（极小的文字）
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(formatTime(positionMs), color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
                            Text(formatTime(durationMs), color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
                        }

                        Spacer(Modifier.height(2.dp))
                    }
                }
            }
        }

        // 密度模式选择弹窗
        if (showDensityDialog) {
            AlertDialog(
                onDismissRequest = { showDensityDialog = false },
                containerColor = Color(0xDD222222),
                titleContentColor = Color.White,
                textContentColor = Color.White,
                title = { Text("弹幕密度") },
                text = {
                    Column {
                        densityOptions.forEach { (mode, label, desc) ->
                            TextButton(
                                onClick = {
                                    viewModel.setDensityMode(mode)
                                    showDensityDialog = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        if (densityMode == mode) "● " else "○ ",
                                        color = if (densityMode == mode) Color(0xFFFF6B35) else Color.Gray,
                                        fontSize = 14.sp
                                    )
                                    Column {
                                        Text(label, color = Color.White, fontSize = 14.sp)
                                        Text(desc, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showDensityDialog = false }) {
                        Text("关闭", color = Color(0xFFFF6B35))
                    }
                }
            )
        }

        // 弹幕样式设置弹窗
        if (showStyleDialog) {
            AlertDialog(
                onDismissRequest = { showStyleDialog = false },
                containerColor = Color(0xDD222222),
                titleContentColor = Color.White,
                textContentColor = Color.White,
                title = { Text("弹幕样式") },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .heightIn(max = 280.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // 字号
                        styleSlider("字号", danmakuScale, 0.3f, 3.0f, "x") {
                            viewModel.setDanmakuScale(it)
                        }
                        // 速度
                        styleSlider("速度", danmakuSpeed, 0.3f, 3.0f, "x") {
                            viewModel.setDanmakuSpeed(it)
                        }
                        // 透明度
                        styleSlider("透明度", danmakuAlpha, 0.1f, 1.0f, "") {
                            viewModel.setDanmakuAlpha(it)
                        }
                        // 描边
                        styleSlider("描边", danmakuStroke, 0f, 8f, "dp") {
                            viewModel.setDanmakuStroke(it)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showStyleDialog = false }) {
                        Text("关闭", color = Color(0xFFFF6B35))
                    }
                }
            )
        }
    }
}

/** 密度模式选项定义 */
private val densityOptions = listOf(
    Triple(DensityMode.Strict, "最少", "宁可丢弃，不重叠"),
    Triple(DensityMode.Balanced, "默认", "阅读体验与数量平衡"),
    Triple(DensityMode.Crowded, "最多", "优先显示更多弹幕"),
)

/**
 * 样式滑块组件
 */
@Composable
private fun styleSlider(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    unit: String,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = Color.White, fontSize = 13.sp)
            Text(
                "${String.format("%.1f", value)}$unit",
                color = Color(0xFFFF6B35),
                fontSize = 13.sp
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = min..max,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFFFF6B35),
                activeTrackColor = Color(0xFFFF6B35),
                inactiveTrackColor = Color(0x44FFFFFF)
            )
        )
    }
}

private fun formatTime(ms: Long): String {
    val s = ms / 1000
    return "%02d:%02d".format(s / 60, s % 60)
}

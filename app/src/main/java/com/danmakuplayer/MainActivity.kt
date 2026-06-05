package com.danmakuplayer

import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.danmakuplayer.ui.HomeScreen
import com.danmakuplayer.ui.PlayerScreen

/**
 * 单 Activity 入口
 *
 * 管理首页与全屏播放器之间的导航
 */
class MainActivity : ComponentActivity() {

    private val viewModel: PlayerViewModel by viewModels()

    /** 导航状态：true=播放器，false=首页 */
    private var isInPlayer by mutableStateOf(false)

    /** 弹幕文件路径 */
    private var danmakuPath by mutableStateOf<String?>(null)

    private val openVideoLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            contentResolver.takePersistableUriPermission(
                it, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            viewModel.openVideo(it)
            isInPlayer = true
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    /**
     * 弹幕文件选择器：支持 XML 文件
     */
    private val openDanmakuLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            // 将 URI 拷贝到内部存储以获得文件路径
            val inputStream = contentResolver.openInputStream(it) ?: return@registerForActivityResult
            val tmpFile = java.io.File(cacheDir, "danmaku.xml")
            tmpFile.outputStream().use { out -> inputStream.copyTo(out) }
            danmakuPath = tmpFile.absolutePath
            viewModel.loadDanmaku(tmpFile.absolutePath)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            if (isInPlayer) {
                PlayerScreen(
                    viewModel = viewModel,
                    onBack = {
                        viewModel.pause()  // 暂停播放
                        isInPlayer = false
                        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }
                )
            } else {
                HomeScreen(
                    onOpenFile = {
                        openVideoLauncher.launch(arrayOf("video/*"))
                    },
                    onLoadDanmaku = {
                        openDanmakuLauncher.launch(arrayOf("text/xml", "application/xml", "*/*"))
                    },
                    danmakuLoaded = danmakuPath != null
                )
            }
        }
    }
}

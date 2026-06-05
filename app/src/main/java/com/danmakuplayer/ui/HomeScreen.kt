package com.danmakuplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 首页
 *
 * 简洁欢迎页，只有一个「打开视频」按钮
 */
@Composable
fun HomeScreen(
    onOpenFile: () -> Unit,
    onLoadDanmaku: (() -> Unit)? = null,
    danmakuLoaded: Boolean = false
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo / 标题
            Text(
                text = "DanmakuPlayer",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "弹幕引擎的播放器底板",
                fontSize = 14.sp,
                color = Color.Black.copy(alpha = 0.4f)
            )

            Spacer(Modifier.height(48.dp))

            // 打开视频按钮
            Button(
                onClick = onOpenFile,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF6B35)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.size(width = 200.dp, height = 52.dp)
            ) {
                Icon(
                    Icons.Default.PlayCircle,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = "打开视频",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // 弹幕文件按钮（可选）
            if (onLoadDanmaku != null) {
                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = onLoadDanmaku,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (danmakuLoaded) Color(0xFF4CAF50) else Color(0xFF2196F3)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.size(width = 200.dp, height = 44.dp)
                ) {
                    Icon(
                        Icons.Default.ClosedCaption,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = if (danmakuLoaded) "弹幕已加载" else "加载弹幕文件",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

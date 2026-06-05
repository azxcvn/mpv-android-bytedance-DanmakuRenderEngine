package com.danmaku.flow.controller

/**
 * 弹幕数据源
 */
sealed class DanmakuDataSource {
    /** 从本地文件路径加载 */
    data class FilePath(val path: String) : DanmakuDataSource()
    /** 从 InputStream 加载 */
    data class Stream(val inputStream: java.io.InputStream) : DanmakuDataSource()
}

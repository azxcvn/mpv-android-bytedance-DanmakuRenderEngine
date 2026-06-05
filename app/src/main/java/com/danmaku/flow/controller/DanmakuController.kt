package com.danmaku.flow.controller

import com.danmaku.flow.bridge.api.DanmakuOverlayHost
import com.danmaku.flow.model.GlobalDanmakuStyle

/**
 * 弹幕控制器接口
 *
 * 业务侧只面对这一个入口。
 * 对应融合方案第七节 API 设计
 */
interface DanmakuController {
    fun attach(host: DanmakuOverlayHost)
    fun detach()

    fun setSource(source: DanmakuDataSource)
    fun load()
    fun clear()

    fun play()
    fun pause()
    fun seekTo(positionMs: Long)

    fun updateStyle(style: GlobalDanmakuStyle)
    fun setVisibility(visible: Boolean)
    fun release()
}

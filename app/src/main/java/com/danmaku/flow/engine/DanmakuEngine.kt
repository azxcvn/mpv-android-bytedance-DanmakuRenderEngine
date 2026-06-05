package com.danmaku.flow.engine

import com.danmaku.flow.model.ActiveDanmaku
import com.danmaku.flow.model.DanmakuRenderItem
import com.danmaku.flow.model.GlobalDanmakuStyle
import com.danmaku.flow.bridge.api.PlayerEvent
import com.danmaku.flow.parser.DanmakuRepository

/**
 * 弹幕引擎接口
 *
 * 管理内部状态推进：时间轴、轨道、活动池、密度控制。
 * 对应融合方案 7.1 节
 */
interface DanmakuEngine {
    /** 初始化引擎 */
    fun initialize(repository: DanmakuRepository, screenWidth: Int, screenHeight: Int)

    /** 动态更新屏幕尺寸（当实际画布尺寸与初始化时不同时调用） */
    fun setScreenSize(width: Int, height: Int)

    /** 每帧时钟推进 */
    fun onClockTick(positionMs: Long)

    /** 处理播放器事件 */
    fun onPlayerEvent(event: PlayerEvent)

    /** 更新全局样式 */
    fun updateStyle(style: GlobalDanmakuStyle)

    /** 生成当前帧渲染快照 */
    fun snapshot(): List<DanmakuRenderItem>

    /** 清空活动池 */
    fun clear()

    /** 释放资源 */
    fun release()
}

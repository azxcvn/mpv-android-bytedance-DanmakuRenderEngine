package com.danmakuplayer.model

/**
 * 全局弹幕样式配置
 *
 * 对应融合方案第七节 API 设计中的 GlobalDanmakuStyle。
 * 业务侧只面对这一个配置对象。
 */
data class GlobalDanmakuStyle(
    /** 全局字号倍率 */
    val scale: Float = 1f,
    /** 速度倍率 */
    val speedFactor: Float = 1f,
    /** 全局透明度 0~1 */
    val alpha: Float = 0.85f,
    /** 描边宽度 dp */
    val strokeWidthDp: Float = 2f,
    /** 同屏最大弹幕数 */
    val maxVisibleCount: Int = 180,
    /** 密度模式 */
    val densityMode: DensityMode = DensityMode.Crowded,
    /** 是否启用 Anime4K 感知 */
    val anime4kAware: Boolean = false,
    /** 滚动弹幕最大行数，0 表示自动（填满屏幕） */
    val scrollMaxLines: Int = 0,
    /** 顶部弹幕最大行数，0 表示自动 */
    val topMaxLines: Int = 0,
    /** 底部弹幕最大行数，0 表示自动 */
    val bottomMaxLines: Int = 0
)

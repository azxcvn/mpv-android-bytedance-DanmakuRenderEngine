package com.danmaku.flow.renderer

import android.util.Log
import com.danmaku.flow.model.FrameStats

/**
 * 帧预算监控器
 *
 * 监控每帧渲染耗时，当连续超预算时触发降级信号。
 * 对应融合方案 9.2 第 6 项：帧预算统计与自动降级
 *
 * 帧预算默认 16.67ms（60fps），可配置。
 * 当连续 [degradeThreshold] 帧超过预算时，触发降级。
 * 当连续 [recoverThreshold] 帧低于预算时，恢复。
 */
class FrameBudgetMonitor(
    /** 帧预算（ms），默认 16.67ms = 60fps */
    private val budgetMs: Float = 16.67f,
    /** 触发降级的连续超预算帧数 */
    private val degradeThreshold: Int = 5,
    /** 恢复正常所需的连续不超预算帧数 */
    private val recoverThreshold: Int = 15
) {
    companion object {
        private const val TAG = "FrameBudget"
    }

    /** 最近一帧耗时（ms） */
    var lastFrameMs: Float = 0f
        private set

    /** 最近 N 帧的平均耗时（ms） */
    var averageFrameMs: Float = 0f
        private set

    /** 连续超预算帧计数 */
    private var overBudgetCount = 0

    /** 连续不超预算帧计数 */
    private var underBudgetCount = 0

    /** 当前是否处于降级状态 */
    var isDegraded: Boolean = false
        private set

    /** 最近 N 帧的耗时环形缓冲 */
    private val frameTimes = FloatArray(30)
    private var frameIndex = 0
    private var frameCount = 0

    /**
     * 记录一帧的渲染耗时
     * @param renderTimeNs 渲染耗时（纳秒）
     * @return true 如果降级状态发生变化
     */
    fun recordFrame(renderTimeNs: Long): Boolean {
        val timeMs = renderTimeNs / 1_000_000f
        lastFrameMs = timeMs

        // 更新环形缓冲
        frameTimes[frameIndex] = timeMs
        frameIndex = (frameIndex + 1) % frameTimes.size
        frameCount = (frameCount + 1).coerceAtMost(frameTimes.size)

        // 计算平均耗时
        var sum = 0f
        for (i in 0 until frameCount) {
            sum += frameTimes[i]
        }
        averageFrameMs = sum / frameCount

        // 状态机
        val wasDegraded = isDegraded
        if (timeMs > budgetMs) {
            overBudgetCount++
            underBudgetCount = 0
            if (!isDegraded && overBudgetCount >= degradeThreshold) {
                isDegraded = true
                Log.w(TAG, "帧耗时连续超预算，触发降级 (avg=${averageFrameMs.to1Decimal()}ms)")
            }
        } else {
            underBudgetCount++
            overBudgetCount = 0
            if (isDegraded && underBudgetCount >= recoverThreshold) {
                isDegraded = false
                Log.i(TAG, "帧耗时恢复正常，退出降级 (avg=${averageFrameMs.to1Decimal()}ms)")
            }
        }

        return wasDegraded != isDegraded
    }

    /** 获取性能统计摘要 */
    fun getStats(): FrameStats = FrameStats(
        lastFrameMs = lastFrameMs,
        averageFrameMs = averageFrameMs,
        budgetMs = budgetMs,
        isDegraded = isDegraded,
        overBudgetCount = overBudgetCount
    )

    fun reset() {
        lastFrameMs = 0f
        averageFrameMs = 0f
        overBudgetCount = 0
        underBudgetCount = 0
        isDegraded = false
        frameCount = 0
        frameIndex = 0
    }
}

private fun Float.to1Decimal(): String = String.format("%.1f", this)

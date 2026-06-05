package com.danmakuplayer.manager

import android.content.Context
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView
import `is`.xyz.mpv.MPV

/**
 * mpv 视频输出 SurfaceView
 *
 * 负责管理 Surface 生命周期与 MPV 实例的绑定/解绑。
 * 替代 BaseMPVView 以避免 AAR 兼容问题。
 */
class MpvSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    private var mpv: MPV? = null
    private var surfaceReady = false
    private var surfaceWidth = 0
    private var surfaceHeight = 0

    /** Surface 就绪回调（用于延迟加载视频） */
    var onSurfaceCreatedListener: (() -> Unit)? = null

    init {
        holder.addCallback(this)
    }

    /**
     * 设置 MPV 实例，如果 Surface 已就绪则立即绑定
     */
    fun setMpvInstance(instance: MPV?) {
        mpv = instance
        if (surfaceReady && instance != null && instance.isInitialized) {
            attachMpv(instance)
        }
    }

    private fun attachMpv(instance: MPV) {
        instance.attachSurface(holder.surface)
        if (surfaceWidth > 0 && surfaceHeight > 0) {
            instance.setPropertyString("android-surface-size", "${surfaceWidth}x${surfaceHeight}")
        }
        instance.setPropertyString("vo", "gpu")
        instance.setPropertyString("force-window", "yes")
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceReady = true
        mpv?.let { instance ->
            if (instance.isInitialized) {
                attachMpv(instance)
            }
        }
        onSurfaceCreatedListener?.invoke()
        onSurfaceCreatedListener = null // 只触发一次
    }

    override fun surfaceChanged(
        holder: SurfaceHolder,
        format: Int,
        width: Int,
        height: Int
    ) {
        mpv?.setPropertyString("android-surface-size", "${width}x${height}")
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
        mpv?.let { instance ->
            if (instance.isInitialized) {
                instance.setPropertyString("vo", "null")
                instance.setPropertyString("force-window", "no")
                instance.detachSurface()
            }
        }
    }
}

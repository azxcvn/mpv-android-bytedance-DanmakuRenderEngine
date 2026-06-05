package com.danmaku.flow.parser

import com.danmaku.flow.model.DanmakuItem
import java.io.InputStream

/**
 * 弹幕解析器接口
 *
 * 策略模式：不同格式各自实现。
 * 对应融合方案 4.2 节
 */
interface DanmakuParser {
    fun parse(input: InputStream): List<DanmakuItem>
}

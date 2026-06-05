package com.danmaku.flow.parser

import android.util.Log
import com.danmaku.flow.model.DanmakuItem
import com.danmaku.flow.model.DanmakuSourceType
import com.danmaku.flow.model.DanmakuType
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream

/**
 * Bilibili XML 弹幕解析器
 *
 * 解析 `<d p="time,type,size,color,...">文本</d>` 格式。
 * 对应融合方案 9.1.1 第 3 步
 */
class BilibiliXmlParser : DanmakuParser {

    companion object {
        private const val TAG = "BilibiliXmlParser"
    }

    override fun parse(input: InputStream): List<DanmakuItem> {
        val items = mutableListOf<DanmakuItem>()
        var id = 0L
        var totalNodes = 0
        var blankAttrOrText = 0
        var invalidAttrParts = 0
        var invalidTime = 0
        val rawTypeCounter = linkedMapOf<Int, Int>()

        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(input, "UTF-8")

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "d") {
                totalNodes++
                val pAttr = parser.getAttributeValue(null, "p") ?: ""
                val text = parser.nextText() ?: ""

                val result = parseDanmakuElement(id++, pAttr, text)
                when (result) {
                    is ParseResult.Success -> {
                        val rawType = result.rawType
                        rawTypeCounter[rawType] = (rawTypeCounter[rawType] ?: 0) + 1
                        items.add(result.item)
                    }
                    ParseResult.BlankAttrOrText -> blankAttrOrText++
                    ParseResult.InvalidAttrParts -> invalidAttrParts++
                    ParseResult.InvalidTime -> invalidTime++
                }
            }
            eventType = parser.next()
        }

        val sorted = items.sortedBy { it.timelineMs }
        Log.i(
            TAG,
            "parse complete: totalNodes=$totalNodes, parsed=${sorted.size}, blank=$blankAttrOrText, invalidParts=$invalidAttrParts, invalidTime=$invalidTime, rawTypes=$rawTypeCounter"
        )
        return sorted
    }

    /**
     * 解析单条弹幕
     *
     * p 属性格式: time,type,size,color,timestamp,pool,uid,dmid
     * - time: 出现时间（秒，浮点）
     * - type: 弹幕类型 1/2/3=滚动 4=底部 5=顶部
     * - size: 字号
     * - color: 十进制颜色
     */
    private fun parseDanmakuElement(id: Long, pAttr: String, text: String): ParseResult {
        if (pAttr.isBlank() || text.isBlank()) return ParseResult.BlankAttrOrText

        val parts = pAttr.split(",")
        if (parts.size < 4) return ParseResult.InvalidAttrParts

        val timeSeconds = parts[0].toDoubleOrNull() ?: return ParseResult.InvalidTime
        val typeInt = parts[1].toIntOrNull() ?: 1
        val size = parts[2].toFloatOrNull() ?: 25f
        val colorDecimal = parts[3].toLongOrNull() ?: 0xFFFFFF

        val timelineMs = (timeSeconds * 1000).toLong()
        val type = mapType(typeInt)

        // 补全 alpha 为 0xFF
        val color = (0xFF000000L or (colorDecimal and 0xFFFFFF)).toInt()

        return ParseResult.Success(
            item = DanmakuItem(
                id = id,
                timelineMs = timelineMs,
                type = type,
                text = unescapeXml(text),
                color = color,
                textSizeSp = size / 1.0f, // B站 size 直接当 sp 用
                source = DanmakuSourceType.BilibiliXml,
                priority = 0
            ),
            rawType = typeInt
        )
    }

    private sealed interface ParseResult {
        data class Success(val item: DanmakuItem, val rawType: Int) : ParseResult
        data object BlankAttrOrText : ParseResult
        data object InvalidAttrParts : ParseResult
        data object InvalidTime : ParseResult
    }

    private fun mapType(typeInt: Int): DanmakuType = when (typeInt) {
        1, 2, 3 -> DanmakuType.ScrollRtl
        4 -> DanmakuType.BottomFixed
        5 -> DanmakuType.TopFixed
        else -> DanmakuType.ScrollRtl
    }

    private fun unescapeXml(text: String): String {
        return text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#xA;", "\n")
            .replace("&#x0A;", "\n")
    }
}

package com.danmaku.flow.parser

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

    override fun parse(input: InputStream): List<DanmakuItem> {
        val items = mutableListOf<DanmakuItem>()
        var id = 0L

        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(input, "UTF-8")

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "d") {
                val pAttr = parser.getAttributeValue(null, "p") ?: ""
                val text = parser.nextText() ?: ""

                val item = parseDanmakuElement(id++, pAttr, text)
                if (item != null) {
                    items.add(item)
                }
            }
            eventType = parser.next()
        }

        return items.sortedBy { it.timelineMs }
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
    private fun parseDanmakuElement(id: Long, pAttr: String, text: String): DanmakuItem? {
        if (pAttr.isBlank() || text.isBlank()) return null

        val parts = pAttr.split(",")
        if (parts.size < 4) return null

        val timeSeconds = parts[0].toDoubleOrNull() ?: return null
        val typeInt = parts[1].toIntOrNull() ?: 1
        val size = parts[2].toFloatOrNull() ?: 25f
        val colorDecimal = parts[3].toLongOrNull() ?: 0xFFFFFF

        val timelineMs = (timeSeconds * 1000).toLong()
        val type = mapType(typeInt)

        // 补全 alpha 为 0xFF
        val color = (0xFF000000L or (colorDecimal and 0xFFFFFF)).toInt()

        return DanmakuItem(
            id = id,
            timelineMs = timelineMs,
            type = type,
            text = unescapeXml(text),
            color = color,
            textSizeSp = size / 1.0f, // B站 size 直接当 sp 用
            source = DanmakuSourceType.BilibiliXml,
            priority = 0
        )
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

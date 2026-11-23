package top.easelink.lcg.ui.main.article.source

import org.jsoup.Jsoup

/**
 * 从帖子正文 HTML 中抽取网盘链接与提取码的纯规则集合（不依赖 Android 与 ViewModel，便于离线单测）。
 */
object DownloadLinkRules {

    /** 支持识别的网盘 / 下载站链接正则。 */
    private val linkRegex = Regex(
        """https?://(?:pan\.baidu\.com/s/|pan\.quark\.cn/s/|www\.aliyundrive\.com/s/|aliyundrive\.com/s/|cloud\.189\.cn/t/|pan\.xunlei\.com/s/|(?:[a-zA-Z0-9.]{0,20})lanzou[a-z]{1}\.com/)[^\s<>"']+"""
    )

    /** 提取码 / 密码 / 口令正则。 */
    private val codeRegex = Regex("""(?:提取码|密码|访问码|口令)[\s:：]*([a-zA-Z0-9]{4})""")

    /**
     * 解析正文 HTML，返回「链接 + 提取码」组合后的展示条目；无内容时返回空列表。
     */
    fun extract(html: String?): List<String> {
        if (html.isNullOrBlank()) return emptyList()

        val doc = Jsoup.parse(html)
        val plainText = doc.text()

        val hrefLinks = doc.select("a[href]").mapNotNull { element ->
            val href = element.attr("href").trim()
            if (linkRegex.containsMatchIn(href)) href else null
        }

        val textLinks = linkRegex.findAll(plainText).map { it.value }.toList()

        val allLinks = (hrefLinks + textLinks).distinct()

        val codeMatches = codeRegex.findAll(plainText).map { it.groupValues[1].trim() }.toList()

        if (allLinks.isEmpty() && codeMatches.isEmpty()) {
            return emptyList()
        }

        val resultList = ArrayList<String>()
        val maxCount = maxOf(allLinks.size, codeMatches.size)

        for (i in 0 until maxCount) {
            val link = allLinks.getOrNull(i)?.trim() ?: ""
            val code = codeMatches.getOrNull(i)?.trim() ?: ""

            var combined = ""
            if (link.isNotEmpty() && code.isNotEmpty()) {
                if (link.contains("pwd=$code") || link.contains("password=$code") ||
                    link.contains("code=$code") || link.contains("提取码")
                ) {
                    combined = link
                } else {
                    combined = "$link 提取码: $code"
                }
            } else if (link.isNotEmpty()) {
                combined = link
            } else if (code.isNotEmpty()) {
                combined = "提取码: $code"
            }

            if (combined.isNotEmpty() && !resultList.contains(combined)) {
                resultList.add(combined)
            }
        }

        return resultList
    }
}

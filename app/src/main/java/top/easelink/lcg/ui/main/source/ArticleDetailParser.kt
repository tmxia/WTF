package top.easelink.lcg.ui.main.source

import android.util.ArrayMap
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import top.easelink.lcg.ui.main.model.BlockException
import top.easelink.lcg.ui.main.source.model.PreviewPost

/**
 * 帖子详情 / 预览 / 正文清洗解析收口：纯 DOM -> model 映射。
 * 楼层组合仍由远程数据源编排，本文件只负责字段提取与正文 HTML 清洗。
 */

const val USER_NAME = "name"
const val USER_AVATAR = "avatar"
const val USER_PROFILE_URL = "profile_url"
const val USER_EXTRA_INFO = "extra_info"
const val FOLLOW_URL = "follow_url"
const val FOLLOW_TITLE = "follow_title"

fun getReplyAddUrl(document: Document): List<String> {
    val list: MutableList<String> = ArrayList(12)
    try {
        document.getElementById("recommend_add")?.attr("href")?.let { list.add(it) }
        document.select("a.replyadd").forEach { list.add(it.attr("href")) }
    } catch (_: NullPointerException) { }
    return list
}

fun getUserInfo(document: Document): List<Map<String, String>> {
    val list: MutableList<Map<String, String>> = ArrayList(12)
    val elements = document.select("td[rowspan]")
    for (element in elements) {
        val userInfoMap: MutableMap<String, String> = ArrayMap(4)
        element.select("div.avatar").apply {
            userInfoMap[USER_AVATAR] = select("img").attr("src")
            userInfoMap[USER_PROFILE_URL] = select("a").attr("href")
        }
        element.selectFirst("a[id^=follow]")?.let {
            userInfoMap[FOLLOW_URL] = it.attr("href")
            userInfoMap[FOLLOW_TITLE] = it.attr("title")
        }
        userInfoMap[USER_EXTRA_INFO] = element.getElementsByTag("dl").outerHtml()
        userInfoMap[USER_NAME] = element.select("a.xw1").text()
        list.add(userInfoMap)
    }
    return list
}

fun getDateTime(document: Document): List<String> =
    document.select("div.authi").select("em").map { it.text() }

@Throws(BlockException::class)
fun getFirstPost(document: Document): PreviewPost {
    val dateTime = document.selectFirst("div.authi")?.select("em")?.text() ?: run {
        val message = document.getElementById("messagetext")?.text().orEmpty()
        throw BlockException(message)
    }
    val content = getFirstContent(document)
    var avatar: String? = null
    var name: String? = null
    document.selectFirst("td[rowspan]")?.apply {
        avatar = selectFirst("div.avatar")?.selectFirst("img")?.attr("src")
        name = selectFirst("a.xw1")?.text()
    }
    return PreviewPost(
        avatar = avatar ?: "",
        author = name ?: "Unknown",
        date = dateTime,
        content = content
    )
}

fun getFirstContent(doc: Document): String = doc.selectFirst("div.pcb").let { element ->
    element?.selectFirst("td.t_f")?.let { tmp -> processContentElement(tmp).html() }
        ?: element?.selectFirst("div.locked")?.html().orEmpty()
}.orEmpty()

fun getContents(doc: Document): List<String> = doc.select("div.pcb").filterNotNull().mapTo(ArrayList<String>()) {
    it.selectFirst("td.t_f")?.let { tmp -> processContentElement(tmp) }?.let { res ->
        it.select("div.savephotop > img")?.forEach { imgElement ->
            res.appendChild(imgElement.attr("src", imgElement.attr("file")))
        }
        res.html()
    } ?: it.selectFirst("div.locked")?.html().orEmpty()
}

fun getReplyUrls(doc: Document): List<String> =
    doc.getElementsByClass("fastre").map { it.attr("href") }

fun processContentElement(element: Element): Element {
    element.select("div.tip").remove()
    element.select("script").remove()
    for (quote in element.select("div.quote")) {
        val inner = quote.selectFirst("blockquote")?.html().orEmpty()
        if (inner.isNotEmpty()) {
            quote.html("<blockquote style=\"border-left:3px solid #ccc;padding-left:8px;color:#888\">$inner</blockquote>")
        }
    }
    for (e in element.getElementsByTag("pre")) {
        val s = e.html().replace("\r\n", "<br/>").replace("\r", "<br/>").replace("\n", "<br/>").replace(" ", "&nbsp;")
        e.html(s)
    }
    val imgElements = element.getElementsByTag("img")
    for (i in imgElements.indices) {
        val imgElement = imgElements[i]
        val src = imgElement.attr("src")
        if (src.contains("static.52pojie.cn/static/image/") && !src.contains("smiley") && !src.contains("none")) {
            imgElement.remove()
        }
        val realAttr = listOf("file", "zoomfile", "data-src", "data-original").firstOrNull { key ->
            val value = imgElement.attr(key)
            value.isNotEmpty() && !value.contains("none.gif")
        }
        if (realAttr != null) imgElement.attr("src", imgElement.attr(realAttr))
    }
    for (bold in element.select("b, strong")) bold.unwrap()
    for (heading in element.select("h1, h2, h3, h4, h5, h6")) heading.tagName("p")
    return element
}

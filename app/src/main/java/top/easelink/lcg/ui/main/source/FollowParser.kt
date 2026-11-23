package top.easelink.lcg.ui.main.source

import androidx.annotation.WorkerThread
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import top.easelink.lcg.ui.main.follow.model.FeedInfo
import top.easelink.lcg.ui.main.follow.model.FollowInfo
import top.easelink.lcg.ui.main.follow.model.FollowResult

/**
 * 关注域解析收口：纯 DOM / XML -> model 映射，不持有 ViewModel 状态。
 * 站点改版时只需调整本文件选择器，关注流/关注列表 ViewModel 业务代码不动。
 */

/** 关注流解析结果：valid 表示响应非 "false"（与原 parseFeeds 返回语义一致），feeds 为动态列表。 */
data class FeedPage(
    val valid: Boolean,
    val feeds: List<FeedInfo>
)

@WorkerThread
fun parseFeedPage(xml: String): FeedPage {
    if (xml == "false") return FeedPage(false, emptyList())

    val result = xml
        .substring(xml.indexOf("[CDATA[", 0, true) + 7)
        .removeSuffix("]]></root>")

    val doc = Jsoup.parse(result)
    val feedInfos = doc.select("li.cl").map {
        val avatarUrl = it.selectFirst("a.z > img")?.attr("src").orEmpty()
        var username = ""
        var dateTime = ""
        it.selectFirst("div.flw_author")?.let { author ->
            username = author.selectFirst("a")?.text().orEmpty()
            dateTime = author.selectFirst("span")?.text().orEmpty()
        }
        val title = it.selectFirst("h2")?.text().orEmpty()
        val articleUrl = it.selectFirst("h2 > a")?.attr("href").orEmpty()

        val followImages = it.getElementsByClass("flw_image")
            .select("ul > li")
            .mapNotNull { li ->
                li.selectFirst("img")?.attr("src")
            }

        val content = it.selectFirst(".pbm")?.let { pbm ->
            pbm.select("div.flw_image").remove()
            pbm.select("img").remove()
            pbm.select("a.flw_readfull").remove()
            pbm.html()
        }.orEmpty()

        val forum = it.selectFirst("div.xg1 > a")?.text().orEmpty()
        val quote = it.selectFirst("div.flw_quotenote")?.text().orEmpty()

        FeedInfo(
            avatar = avatarUrl,
            username = username,
            dateTime = dateTime,
            title = title,
            articleUrl = articleUrl,
            content = content,
            forum = forum,
            quote = quote,
            images = followImages
        )
    }
    return FeedPage(true, feedInfos)
}

@WorkerThread
fun parseFollowResult(doc: Document): FollowResult {
    val followInfos = doc.select("li.cl").map {
        val avatarUrl = it.selectFirst("img")?.attr("src").orEmpty()
        val username = it.getElementById("edit_avt")?.attr("title").orEmpty()
        val lastAction = it.selectFirst("p")?.text().orEmpty()
        val url = it.selectFirst("a[id^=a_followmod]")?.attr("href").orEmpty()

        var follower = 0
        var following = 0
        it.select("strong.xi2").takeIf { e -> e.size == 2 }?.let { e ->
            follower = e[0].text().toIntOrNull() ?: 0
            following = e[1].text().toIntOrNull() ?: 0
        }

        FollowInfo(
            avatar = avatarUrl,
            lastAction = lastAction,
            username = username,
            followerNum = follower,
            followingNum = following,
            followOrUnFollowUrl = url
        )
    }

    val nextPageUrl = doc.selectFirst("a.nxt")?.attr("href")
    return FollowResult(followInfos, nextPageUrl)
}

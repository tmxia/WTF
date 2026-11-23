package top.easelink.lcg.ui.search.source

import android.util.ArrayMap
import org.jsoup.nodes.Document
import timber.log.Timber
import top.easelink.lcg.R
import top.easelink.lcg.network.JsoupClient
import top.easelink.lcg.ui.search.model.LCGSearchResultItem
import top.easelink.lcg.ui.search.model.LCGSearchResults
import top.easelink.lcg.utils.WebsiteConstant
import top.easelink.lcg.utils.showMessage
import java.net.SocketTimeoutException

object LCGSearchService {
    private const val MAX_RETRY = 3

    private var mNextPageUrl: String? = null
    private var mTotalResults: String? = null

    private val allowedOrderby = setOf("dateline", "lastpost", "replies", "views")

    suspend fun doSearchWith(keywordRaw: String, fid: String? = null, orderby: String? = null): LCGSearchResults? {
        mNextPageUrl = null
        mTotalResults = null

        val keyword = SearchAntiDefense.normalizeKeyword(keywordRaw)
        if (keyword.isBlank()) return null

        if (SearchAntiDefense.isFrozen()) {
            showMessage("搜索暂时受限，请稍后再试")
            return null
        }

        
        val key = SearchAntiDefense.cacheKey(keyword, fid, orderby)
        SearchAntiDefense.getCached(key)?.let {
            Timber.tag("[SearchProbe]").d("命中搜索缓存: $keyword, 条数=${it.searchResultList.size}")
            return it
        }

        if (!SearchAntiDefense.acquireSlot()) {
            showMessage("搜索太频繁，请稍后再试")
            return null
        }

        val url = "${WebsiteConstant.SERVER_BASE_URL}search.php?searchsubmit=yes"
        val form = buildForm(keyword, fid, orderby)

        var lastError: Exception? = null
        for (attempt in 0..MAX_RETRY) {
            try {
                val response = JsoupClient.sendPostRequestWithUrl(url, form, enableWafPopup = true)
                val code = response.statusCode()
                Timber.tag("[SearchProbe]").d("HTTP 响应码: $code (attempt=$attempt)")

                
                if (SearchAntiDefense.isBlockedHttpCode(code)) {
                    if (attempt < MAX_RETRY) {
                        SearchAntiDefense.backoffSleep(attempt)
                        continue
                    }
                    SearchAntiDefense.freeze()
                    throw Exception("请求被站点风控拦截，请稍后再试")
                }
                if (code !in 200..299) {
                    throw Exception("服务器拒绝请求 (HTTP $code)，请尝试切换板块或稍后再试")
                }

                val doc = response.parse()
                if (SearchAntiDefense.detectWaf(doc)) {
                    if (attempt < MAX_RETRY) {
                        SearchAntiDefense.backoffSleep(attempt)
                        continue
                    }
                    SearchAntiDefense.freeze()
                    throw Exception("触发安全风控，请完成验证后再搜索")
                }

                
                val errorText = doc.selectFirst(".alert_error, .alert_info, .alert_msg")?.text()?.trim().orEmpty()
                if (errorText.isNotBlank()) {
                    throw Exception(errorText)
                }

                val result = parseSearchResults(doc)
                try {
                    mTotalResults = doc.selectFirst("h2")?.text()
                    result.totalResult = mTotalResults
                } catch (e: Exception) {
                    Timber.w(e)
                }

                SearchAntiDefense.putCached(key, result)
                return result
            } catch (e: Exception) {
                lastError = e
                val retryable = e is SocketTimeoutException ||
                    e.message?.contains("风控") == true ||
                    e.message?.contains("拦截") == true ||
                    e.message?.contains("超时") == true
                if (retryable && attempt < MAX_RETRY) {
                    Timber.tag("SearchDebug").w(e, "搜索可重试异常，退避后重试 (attempt=$attempt)")
                    SearchAntiDefense.backoffSleep(attempt)
                    continue
                }
                throw e
            }
        }
        throw lastError ?: Exception("搜索失败，请稍后再试")
    }

    suspend fun doSearchNextPage(): LCGSearchResults {
        val nextUrl = mNextPageUrl
        if (nextUrl.isNullOrEmpty()) return emptyResult()
        if (SearchAntiDefense.isFrozen()) {
            showMessage("搜索暂时受限，请稍后再试")
            return emptyResult()
        }
        
        SearchAntiDefense.nextPageSleep()
        try {
            return parseSearchResults(JsoupClient.sendGetRequestWithQuery(nextUrl))
        } catch (e: SocketTimeoutException) {
            showMessage(R.string.network_error)
        } catch (e: Exception) {
            Timber.e(e)
            showMessage(R.string.error)
        }
        return emptyResult()
    }

    private fun buildForm(keyword: String, fid: String?, orderby: String?): ArrayMap<String, String> {
        val safeFid = fid?.split(",")
            ?.map { it.trim() }
            ?.filter { it.matches(Regex("\\d+")) }
            ?.joinToString(",")
        val safeOrderby = orderby?.takeIf { it in allowedOrderby }

        return ArrayMap<String, String>().apply {
            put("formhash", JsoupClient.formHash ?: "")
            put("mod", "forum")
            put("srchtype", "title")
            if (!safeFid.isNullOrEmpty()) put("srhfid", safeFid)
            if (!safeOrderby.isNullOrBlank()) put("orderby", safeOrderby)
            put("srhlocality", "forum::index")
            put("srchtxt", keyword)
            put("searchsubmit", "true")
        }
    }

    private fun parseSearchResults(doc: Document): LCGSearchResults {
        val results: List<LCGSearchResultItem> = try {
            doc.getElementsByClass("pbw").mapNotNull {
                try {
                    val spans = it.getElementsByTag("span")
                    val aNode = it.selectFirst("a")
                    val pNode = it.selectFirst("p.xg1")

                    if (aNode != null && pNode != null && spans.size >= 3) {
                        LCGSearchResultItem(
                            title = aNode.html(),
                            url = aNode.attr("href"),
                            replyView = pNode.text(),
                            content = pNode.nextElementSibling()?.html().orEmpty(),
                            author = spans[1].text(),
                            date = spans[0].text(),
                            forum = spans[2].text()
                        )
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    Timber.e(e)
                    null
                }
            }
        } catch (e: Exception) {
            Timber.tag("SearchDebug").e(e, "搜索结果解析异常，返回已提取部分")
            emptyList()
        }
        return LCGSearchResults(results).also {
            mNextPageUrl = try {
                doc.selectFirst("a.nxt")?.attr("href")
            } catch (e: Exception) {
                Timber.w(e)
                null
            }
        }
    }

    private fun emptyResult(): LCGSearchResults {
        mNextPageUrl = null
        return LCGSearchResults(emptyList()).also {
            if (!mTotalResults.isNullOrEmpty()) {
                it.totalResult = mTotalResults
            }
        }
    }
}

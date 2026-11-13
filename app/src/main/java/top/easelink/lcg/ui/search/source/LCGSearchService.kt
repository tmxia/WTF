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
    private const val MIN_REQUEST_INTERVAL = 1000L
    private var lastRequestTime: Long = 0L

    private var mNextPageUrl: String? = null
    private var mTotalResults: String? = null

    fun doSearchWith(keyword: String, fid: String? = null, orderby: String? = null): LCGSearchResults? {
        mNextPageUrl = null
        mTotalResults = null
        if (keyword.isBlank()) return null
        
        if (!canSendRequest()) {
            top.easelink.lcg.utils.showMessage("搜索太频繁，请稍后再试")
            return null
        }

        try {
            val url = "${WebsiteConstant.SERVER_BASE_URL}search.php?searchsubmit=yes"
            val form = ArrayMap<String, String>().apply {
                put("formhash", JsoupClient.formHash ?: "")
                put("mod", "forum")
                put("srchtype", "title")

                val fidList = fid?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
                if (fidList.isEmpty()) {
                    put("srchfid", "")
                } else {
                    // 核心修复：Discuz 多选板块应使用逗号分隔，而不是数组下标格式
                    put("srchfid", fidList.joinToString(","))
                }

                if (!orderby.isNullOrBlank()) put("orderby", orderby)
                put("srhlocality", "forum::index")
                put("srchtxt", keyword)
                put("searchsubmit", "true")
            }

            // 核心修复：允许触发 WAF 验证弹窗，否则遇到验证会直接阻断
            val response = JsoupClient.sendPostRequestWithUrl(url, form, enableWafPopup = true)

            Timber.tag("[SearchProbe]").d("HTTP 响应码: ${response.statusCode()}")
            if (response.statusCode() !in 200..299) {
                throw Exception("服务器拒绝请求 (HTTP ${response.statusCode()})，请尝试切换板块或稍后再试")
            }
            if (response.statusCode() in 200..299) {
                val doc = response.parse()
                Timber.tag("[SearchProbe]").d("返回 HTML 前 200 字: ${doc.html().take(200).replace("\n", " ")}")
                
                // 核心修复：检查服务器返回的错误提示并抛给上层
                val errorNode = doc.selectFirst(".alert_error, .alert_info, .alert_msg")
                if (errorNode != null) {
                    val errorText = errorNode.text().trim()
                    if (errorText.isNotBlank()) {
                        throw Exception(errorText)
                    }
                }

                val result = parseSearchResults(doc)
                try {
                    mTotalResults = doc.selectFirst("h2")?.text()
                    result.totalResult = mTotalResults
                } catch (e: Exception) {
                    Timber.w(e)
                }
                return result
            }
        } catch (e: Exception) {
            Timber.tag("SearchDebug").e(e, "搜索异常: ${e.message}")
            throw e
        }
        return emptyResult()
    }

fun doSearchNextPage(): LCGSearchResults {
        if (mNextPageUrl.isNullOrEmpty()) {
            return emptyResult()
        }
        try {
            
            return parseSearchResults(JsoupClient.sendGetRequestWithQuery(mNextPageUrl!!))
        } catch (e: SocketTimeoutException) {
            showMessage(R.string.network_error)
        } catch (e: Exception) {
            Timber.e(e)
            showMessage(R.string.error)
        }
        return emptyResult()
    }

    private fun parseSearchResults(doc: Document): LCGSearchResults {
        val results: List<LCGSearchResultItem> =
            doc.getElementsByClass("pbw").mapNotNull {
                try {
                    val spans = it.getElementsByTag("span")
                    val aNode = it.selectFirst("a")
                    val pNode = it.selectFirst("p.xg1")

                    
                    if (aNode != null && pNode != null && spans.isNotEmpty() && spans.size == 3) {
                        return@mapNotNull LCGSearchResultItem(
                            title = aNode.html(),
                            url = aNode.attr("href"),
                            replyView = pNode.text(),
                            
                            content = pNode.nextElementSibling()?.html().orEmpty(),
                            author = spans[1].text(),
                            date = spans[0].text(),
                            forum = spans[2].text()
                        )
                    }
                } catch (e: Exception) {
                    Timber.e(e)
                }
                null
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

    private fun canSendRequest(): Boolean {
        return if (System.currentTimeMillis() - lastRequestTime < MIN_REQUEST_INTERVAL) {
            false 
        } else {
            lastRequestTime = System.currentTimeMillis()
            true
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
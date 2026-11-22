package top.easelink.lcg.ui.profile.source

import kotlinx.coroutines.withContext
import org.jsoup.nodes.Document
import top.easelink.framework.threadpool.IOPool
import top.easelink.lcg.network.JsoupClient

object ProfileSource {
    private const val FRIEND_OP_BASE_URL =
        "https://www.52pojie.cn/home.php?mod=spacecp&ac=friend"

    suspend fun getProfile(query: String) = withContext(IOPool) {
        JsoupClient.sendGetRequestWithQuery(query)
    }

    suspend fun parseUserInfo(doc: Document) {
    }

    /**
     * 关注 / 拉黑（好友添加或解除）操作，返回响应 HTML；失败抛异常由调用方处理。
     */
    suspend fun toggleFriend(uid: String, op: String, handlekey: String): String = withContext(IOPool) {
        val postData = mutableMapOf(
            "formhash" to (JsoupClient.formHash ?: ""),
            "uid" to uid,
            "handlekey" to handlekey
        )
        JsoupClient.sendPostRequestWithUrl("$FRIEND_OP_BASE_URL&op=$op", postData).body()
    }

    /**
     * 订阅/关注用户，返回页面提示文案；无提示返回 null。
     */
    suspend fun subscribe(url: String): String? = withContext(IOPool) {
        JsoupClient.sendGetRequestWithQuery(url).getElementById("messagetext")?.text()
    }
}

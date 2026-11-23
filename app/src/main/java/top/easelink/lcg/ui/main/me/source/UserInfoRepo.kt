package top.easelink.lcg.ui.main.me.source

import top.easelink.lcg.account.UserInfo
import top.easelink.lcg.network.JsoupClient
import top.easelink.lcg.ui.main.source.parseUserInfo
import top.easelink.lcg.utils.WebsiteConstant

object UserInfoRepo {
    /**
     * 请求个人中心资料；异常不再静默吞成 null，由 ViewModel 按类型分类为 Error 态，
     * 与"未登录 / 无数据"区分开。
     */
    suspend fun requestUserInfo(): UserInfo? {
        val doc = JsoupClient.sendGetRequestWithQuery(WebsiteConstant.PROFILE_QUERY)
        return parseUserInfo(doc)
    }
}

package top.easelink.lcg.ui.profile.viewmodel

import androidx.lifecycle.ViewModel
import top.easelink.lcg.ui.profile.source.ProfileSource

/**
 * 个人资料弹窗操作 ViewModel：关注/拉黑、订阅请求编排收口于此，弹窗只做 UI 反馈。
 */
class PopUpProfileViewModel : ViewModel() {

    /** 关注 / 拉黑（好友操作），返回服务端响应 HTML；异常向上抛由 UI 兜底。 */
    suspend fun toggleFriend(uid: String, op: String, handlekey: String): String =
        ProfileSource.toggleFriend(uid, op, handlekey)

    /** 订阅用户，返回提示文案；未登录或无内容时返回 null。 */
    suspend fun subscribe(url: String): String? = ProfileSource.subscribe(url)
}

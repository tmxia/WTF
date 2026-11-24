package top.easelink.lcg.ui.main.me.viewmodel

import androidx.lifecycle.ViewModel
import top.easelink.lcg.account.UserInfo
import top.easelink.lcg.ui.main.me.source.UserInfoRepo

/**
 * 用户资料远程拉取 ViewModel：供 Activity 登录校验 / 资料回填复用，
 * 屏蔽 Activity 对编排 Repo 的直接依赖；只做请求透传，状态判定仍由调用方处理。
 */
class MeProfileViewModel : ViewModel() {

    /** 拉取远程用户资料，失败或未登录时返回 null。 */
    suspend fun fetchRemoteUserInfo(): UserInfo? = UserInfoRepo.requestUserInfo()
}

package top.easelink.lcg.ui.main.source.local

import android.content.Context
import top.easelink.lcg.appinit.LCGApp

/**
 * 点赞状态的本地落地：只记录"服务端已确认成功"的楼层 pid。
 * 二次进入帖子时据此恢复点赞标记，避免每次重新加载后又变回未赞；
 * 退出登录 / 清理账号时统一清空，防止串号。
 */
object LikeStateStore {
    private const val SP_NAME = "like_state"
    private const val KEY_LIKED_PIDS = "liked_pids"

    private fun sp() = LCGApp.instance.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)

    fun isLiked(pid: String?): Boolean {
        val key = pid?.takeIf { it.isNotBlank() } ?: return false
        return runCatching { sp().getStringSet(KEY_LIKED_PIDS, emptySet())?.contains(key) == true }
            .getOrDefault(false)
    }

    fun markLiked(pid: String?) {
        val key = pid?.takeIf { it.isNotBlank() } ?: return
        runCatching {
            val old = sp().getStringSet(KEY_LIKED_PIDS, emptySet()).orEmpty().toMutableSet()
            if (old.add(key)) {
                sp().edit().putStringSet(KEY_LIKED_PIDS, old).apply()
            }
        }
    }

    fun clear() {
        runCatching { sp().edit().remove(KEY_LIKED_PIDS).apply() }
    }
}

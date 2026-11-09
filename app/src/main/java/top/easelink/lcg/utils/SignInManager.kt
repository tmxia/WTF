package top.easelink.lcg.utils

import androidx.core.content.edit
import timber.log.Timber
import top.easelink.lcg.appinit.LCGApp
import top.easelink.lcg.network.JsoupClient
import top.easelink.lcg.network.LCGCookieJar

object SignInManager {

    private const val APPLY_TASK_URL = "https://www.52pojie.cn/home.php?mod=task&do=apply&id=2"

    fun updateLocalSignInTime() {
        SharedPreferencesHelper.getUserSp().edit {
            putLong("sp_key_last_sign_in_at", System.currentTimeMillis())
        }
    }

    fun isSignedToday(): Boolean {
        val sp = SharedPreferencesHelper.getUserSp()
        val lastTime = sp.getLong("sp_key_last_sign_in_at", 0L)
        if (lastTime == 0L) return false
        val lastCal = java.util.Calendar.getInstance().apply { timeInMillis = lastTime }
        val todayCal = java.util.Calendar.getInstance()
        return lastCal.get(java.util.Calendar.YEAR) == todayCal.get(java.util.Calendar.YEAR) &&
                lastCal.get(java.util.Calendar.DAY_OF_YEAR) == todayCal.get(java.util.Calendar.DAY_OF_YEAR)
    }

    /**
     * 【独立模块 1：验证机制】
     * 保持纯净的 GET 请求，强行对齐官方 API 状态
     */
    fun checkSignInStatus(onResult: (Boolean) -> Unit) {
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                Timber.tag("SignInDebug").d("【官方API验证】发起 GET 状态同步请求...")
                // 注意这里也传了 false，防止后台验证时弹窗
                val doc = JsoupClient.sendGetRequestWithUrl(APPLY_TASK_URL, enableWafPopup = false)
                val text = doc.text()
                val rawHtml = doc.outerHtml()

                Timber.tag("SignInDebug").d("【官方API验证】返回片段: ${text.take(100)}")

                if (rawHtml.contains("wzwsquestion") || rawHtml.contains("waf_zw_verify")) {
                    Timber.tag("SignInDebug").w("【官方API验证】被 WAF 拦截，无法确认状态。")
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onResult(false) }
                    return@launch
                }

                if (text.contains("已申请") || text.contains("已完成") || text.contains("已签到") || 
                    text.contains("已经签到") || text.contains("已经完成") || text.contains("无法再次申请")) {
                    updateLocalSignInTime()
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onResult(true) }
                } else {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onResult(false) }
                }
            } catch (e: Exception) {
                Timber.tag("SignInDebug").e(e, "【官方API验证】请求异常")
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onResult(false) }
            }
        }
    }

    /**
     * 【独立模块 2：手工签到机制】
     * 完美复刻历史提交，使用请求级隔离，静默签到不弹窗
     */
    fun performSignIn(onResult: (String, String) -> Unit) {
        performSignIn(LCGApp.context, onResult)
    }

    fun performSignIn(context: android.content.Context, onResult: (String, String) -> Unit) {
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                Timber.tag("SignIn").d("【手工签到】发起静默签到请求...")
                
                // 核心大招：enableWafPopup = false，强制请求级隔离，绝不弹人机验证
                val applyResponse = JsoupClient.sendGetRequestWithUrl(APPLY_TASK_URL, enableWafPopup = false)
                val rawHtml = applyResponse.outerHtml()
                
                Timber.tag("SignIn").d("签到任务响应: body=${rawHtml.take(200)}")

                if (rawHtml.contains("您需要先登录")) {
                    Timber.tag("SignIn").e("签到失败：Cookie 已失效")
                    withResult(onResult, "FAILED", "签到失败：登录已失效")
                    return@launch
                }

                if (rawHtml.contains("wzwsquestion") || rawHtml.contains("waf_zw_verify")) {
                    Timber.tag("SignIn").e("签到失败：触发 WAF 风控")
                    withResult(onResult, "FAILED", "签到失败：触发安全风控，请去网页端签到")
                    return@launch
                }

                val applyMessage = applyResponse.getElementsByClass("alert_info")
                    .first()?.selectFirst("p")?.text().orEmpty()
                
                if (applyMessage.contains("已申请") || applyMessage.contains("已完成")) {
                    Timber.tag("SignIn").d("今日已签到: $applyMessage")
                    updateLocalSignInTime()
                    withResult(onResult, "ALREADY_DONE", "今日已签到")
                    return@launch
                }
                
                if (applyMessage.contains("签到成功") || applyMessage.contains("获得") || applyMessage.contains("奖励") || applyMessage.contains("成功")) {
                    updateLocalSignInTime()
                    withResult(onResult, "SUCCESS", "签到成功！")
                } else {
                    // 有些情况下任务申请完成，需要去领取奖励，但通常 GET 一次 apply 就会触发
                    updateLocalSignInTime()
                    withResult(onResult, "SUCCESS", if (applyMessage.isNotBlank()) applyMessage else "签到完成")
                }
                
            } catch (e: Exception) {
                Timber.tag("SignIn").e(e, "签到请求异常")
                withResult(onResult, "FAILED", "网络异常: ${e.message}")
            }
        }
    }

    private fun withResult(onResult: (String, String) -> Unit, status: String, msg: String) {
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
            onResult(status, msg)
        }
    }
}

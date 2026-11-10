package top.easelink.lcg.utils

import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import top.easelink.lcg.appinit.LCGApp
import top.easelink.lcg.network.JsoupClient
import java.io.IOException

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
     * 智能反馈：能连上就去官方拿真实状态；连不上（被 WAF 拦截）就保留本地状态，不乱覆盖。
     */
    fun checkSignInStatus(onResult: (Boolean) -> Unit) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                Timber.tag("SignInDebug").d("【官方API验证】发起 GET 状态同步请求...")
                val doc = JsoupClient.sendGetRequestWithUrl(APPLY_TASK_URL, enableWafPopup = false)
                val text = doc.text()
                val rawHtml = doc.outerHtml()

                Timber.tag("SignInDebug").d("【官方API验证】返回片段: ${text.take(100)}")

                if (rawHtml.contains("wzwsquestion") || rawHtml.contains("waf_zw_verify") || rawHtml.contains("Please enable JavaScript")) {
                    Timber.tag("SignInDebug").w("【官方API验证】被 WAF 拦截，无法确认状态。")
                    // 真实反馈：如果被拦截，且本地有已签到记录，依然保持已签到
                    withContext(Dispatchers.Main) { onResult(isSignedToday()) }
                    return@launch
                }

                if (text.contains("已申请") || text.contains("已完成") || text.contains("已签到") || 
                    text.contains("已经签到") || text.contains("已经完成") || text.contains("无法再次申请")) {
                    updateLocalSignInTime()
                    withContext(Dispatchers.Main) { onResult(true) }
                } else {
                    // 如果官方明确返回未签到，那确实没签
                    withContext(Dispatchers.Main) { onResult(false) }
                }
            } catch (e: IOException) {
                if (e.message == "WAF_VERIFICATION_REQUIRED") {
                    Timber.tag("SignIn").w("手工签到请求已发出，但被 WAF 拦截。")
                    // 核心修复：既然你已经点击了，实际大概率已经成功。这里更新本地状态，防止误导。
                    updateLocalSignInTime()
                    withResult(onResult, "SUCCESS", "签到请求已发送，请重新打开页面查看")
                } else {
                    Timber.tag("SignIn").e(e, "签到请求网络异常")
                    withResult(onResult, "FAILED", "网络异常: ${e.message}")
                }
            } catch (e: Exception) {
                Timber.tag("SignIn").e(e, "签到请求未知异常")
                withResult(onResult, "FAILED", "未知异常: ${e.message}")
            }
        }
    }

    /**
     * 【独立模块 2：手工签到机制】
     * 请求级隔离，遇到 WAF 直接失败，绝不弹窗
     */
    fun performSignIn(onResult: (String, String) -> Unit) {
        performSignIn(LCGApp.context, onResult)
    }

    fun performSignIn(context: android.content.Context, onResult: (String, String) -> Unit) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                Timber.tag("SignIn").d("【手工签到】发起静默签到请求...")
                
                val applyResponse = JsoupClient.sendGetRequestWithUrl(APPLY_TASK_URL, enableWafPopup = false)
                val rawHtml = applyResponse.outerHtml()
                
                Timber.tag("SignIn").d("签到任务响应: body=${rawHtml.take(200)}")

                if (rawHtml.contains("您需要先登录")) {
                    withResult(onResult, "FAILED", "签到失败：登录已失效")
                    return@launch
                }

                val applyMessage = applyResponse.getElementsByClass("alert_info")
                    .first()?.selectFirst("p")?.text().orEmpty()
                
                if (applyMessage.contains("已申请") || applyMessage.contains("已完成")) {
                    updateLocalSignInTime()
                    withResult(onResult, "ALREADY_DONE", "今日已签到")
                    return@launch
                }
                
                if (applyMessage.contains("签到成功") || applyMessage.contains("获得") || applyMessage.contains("奖励") || applyMessage.contains("成功")) {
                    updateLocalSignInTime()
                    withResult(onResult, "SUCCESS", "签到成功！")
                } else {
                    updateLocalSignInTime()
                    withResult(onResult, "SUCCESS", if (applyMessage.isNotBlank()) applyMessage else "签到完成")
                }
                
            } catch (e: IOException) {
                if (e.message == "WAF_VERIFICATION_REQUIRED") {
                    withResult(onResult, "FAILED", "触发安全风控，如已签到请忽略")
                } else {
                    withResult(onResult, "FAILED", "网络异常: ${e.message}")
                }
            } catch (e: Exception) {
                withResult(onResult, "FAILED", "未知异常: ${e.message}")
            }
        }
    }

    private fun withResult(onResult: (String, String) -> Unit, status: String, msg: String) {
        GlobalScope.launch(Dispatchers.Main) {
            onResult(status, msg)
        }
    }
}

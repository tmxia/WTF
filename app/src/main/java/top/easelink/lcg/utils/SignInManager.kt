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
     * 纯后台静默检测，绝不弹窗
     */
    fun checkSignInStatus(onResult: (Boolean) -> Unit) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                Timber.tag("SignInDebug").d("【官方API验证】发起 GET 状态同步请求...")
                // 传入 false，禁止弹出 WAF 验证
                val doc = JsoupClient.sendGetRequestWithUrl(APPLY_TASK_URL, enableWafPopup = false)
                val text = doc.text()
                val rawHtml = doc.outerHtml()

                Timber.tag("SignInDebug").d("【官方API验证】返回片段: ${text.take(100)}")

                if (rawHtml.contains("wzwsquestion") || rawHtml.contains("waf_zw_verify") || rawHtml.contains("Please enable JavaScript")) {
                    Timber.tag("SignInDebug").w("【官方API验证】被 WAF 拦截，无法确认状态。")
                    withContext(Dispatchers.Main) { onResult(false) }
                    return@launch
                }

                if (text.contains("已申请") || text.contains("已完成") || text.contains("已签到") || 
                    text.contains("已经签到") || text.contains("已经完成") || text.contains("无法再次申请")) {
                    updateLocalSignInTime()
                    withContext(Dispatchers.Main) { onResult(true) }
                } else {
                    withContext(Dispatchers.Main) { onResult(false) }
                }
            } catch (e: IOException) {
                if (e.message == "WAF_VERIFICATION_REQUIRED") {
                    Timber.tag("SignInDebug").w("【官方API验证】触发 WAF，判定为状态未知（保持未签到）")
                    withContext(Dispatchers.Main) { onResult(false) }
                } else {
                    Timber.tag("SignInDebug").e(e, "【官方API验证】网络异常")
                    withContext(Dispatchers.Main) { onResult(false) }
                }
            } catch (e: Exception) {
                Timber.tag("SignInDebug").e(e, "【官方API验证】请求异常")
                withContext(Dispatchers.Main) { onResult(false) }
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
                
                // 核心大招：传入 false，底层遇到 WAF 会直接抛出 IOException("WAF_VERIFICATION_REQUIRED")
                val applyResponse = JsoupClient.sendGetRequestWithUrl(APPLY_TASK_URL, enableWafPopup = false)
                val rawHtml = applyResponse.outerHtml()
                
                Timber.tag("SignIn").d("签到任务响应: body=${rawHtml.take(200)}")

                if (rawHtml.contains("您需要先登录")) {
                    Timber.tag("SignIn").e("签到失败：Cookie 已失效")
                    withResult(onResult, "FAILED", "签到失败：登录已失效")
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
                    updateLocalSignInTime()
                    withResult(onResult, "SUCCESS", if (applyMessage.isNotBlank()) applyMessage else "签到完成")
                }
                
            } catch (e: IOException) {
                // 完美捕获底层抛出的 WAF 异常
                if (e.message == "WAF_VERIFICATION_REQUIRED") {
                    Timber.tag("SignIn").w("手工签到遇到 WAF，静默拦截成功。")
                    withResult(onResult, "FAILED", "触发安全风控，请前往网页端签到")
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

    private fun withResult(onResult: (String, String) -> Unit, status: String, msg: String) {
        GlobalScope.launch(Dispatchers.Main) {
            onResult(status, msg)
        }
    }
}

package top.easelink.lcg.utils

import timber.log.Timber
import top.easelink.lcg.network.JsoupClient

object SignInManager {

    private const val APPLY_TASK_URL = "https://www.52pojie.cn/home.php?mod=task&do=apply&id=2"
    private const val DRAW_TASK_URL = "https://www.52pojie.cn/home.php?mod=task&do=draw&id=2"

    /**
     * 纯 Jsoup 签到方案（底层已带持久化 Cookie 和 400ms 限流防封）
     */
    fun performSignIn(onResult: (String, String) -> Unit) {
        try {
            Timber.d("开始签到：发起申请任务请求")
            val applyResponse = JsoupClient.sendGetRequestWithUrl(APPLY_TASK_URL)
            val rawHtml = applyResponse.outerHtml()

            // 拦截风控/未登录
            if (rawHtml.contains("您需要先登录")) {
                Timber.e("签到失败：Cookie 已失效，请重新登录")
                onResult("FAILED", "签到失败：登录已失效")
                return
            }

            // 获取提示信息
            val applyMessage = applyResponse.getElementsByClass("alert_info")
                .first()?.selectFirst("p")?.text().orEmpty()

            Timber.d("申请任务响应: $applyMessage, HTML前200字符: ${rawHtml.take(200)}")

            // 判断是否已经签过
            if (applyMessage.contains("已申请") || applyMessage.contains("已完成") || applyMessage.contains("已签到")) {
                Timber.d("今日已签到: $applyMessage")
                onResult("ALREADY_DONE", "今日已签到")
                return
            }

            // 如果没签过，发起领取奖励任务
            Timber.d("开始领取奖励：发起 draw 任务请求")
            val drawResponse = JsoupClient.sendGetRequestWithUrl(DRAW_TASK_URL)
            val drawMessage = drawResponse.getElementsByClass("alert_info")
                .first()?.selectFirst("p")?.text().orEmpty()
            val drawHtml = drawResponse.outerHtml()

            Timber.d("领取奖励响应: $drawMessage, HTML前200字符: ${drawHtml.take(200)}")
            
            // 根据实际返回判断成功或失败
            if (drawMessage.contains("成功") || drawMessage.contains("恭喜") || rawHtml.contains("签到成功")) {
                onResult("SUCCESS", "签到成功！")
            } else {
                // 如果 WAF 拦截，返回的可能是 HTML 挑战页，没有 alert_info
                if (drawMessage.isEmpty()) {
                    Timber.e("签到异常：未获取到提示文本，可能被 WAF 拦截")
                    onResult("FAILED", "签到失败，可能触发风控，请稍后再试")
                } else {
                    onResult("SUCCESS", "签到完成：$drawMessage")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Jsoup 签到请求异常")
            onResult("FAILED", "签到失败，请检查网络或登录状态")
        }
    }
}

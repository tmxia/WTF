package top.easelink.lcg.ui.main.model

/**
 * 用于通知界面显示 WAF 人机验证弹窗的 EventBus 事件
 * 注意：如果在底层网络库传入了 enableWafPopup = false，则此事件通常不会被派发。
 */
class WafVerificationEvent(
    val url: String,
    val applyTask: Boolean = false
)

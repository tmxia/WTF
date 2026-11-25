package top.easelink.lcg.ui.main.source.model

/**
 * 点赞接口的结果：以站点返回结构（是否出现错误提示块）判定成败，
 * 文案只用于提示，不用于判断，避免未知文案被误当成失败而回滚图标。
 */
data class LikeActionResult(val success: Boolean, val message: String)

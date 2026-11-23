package top.easelink.lcg.ui.main.model

import top.easelink.lcg.network.NetworkException as NetException

/**
 * [P1-2] 旧式 marker 异常已收口到网络异常层级 [NetException]；
 * 以下类名与构造签名保留，供既有调用点的 import 与 `is` 判断零改动使用，
 * 运行时类型即为对应网络异常子类，可被 ErrorHandler 统一分类识别。
 */
class BlockException(override val alertMessage: String) : NetException.Blocked(alertMessage)

class NetworkException() : NetException.IoError("网络异常", null)

class LoginRequiredException : NetException.LoginRequired()

class AntiScrapingException : NetException.AntiScraping()

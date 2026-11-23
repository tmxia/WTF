package top.easelink.framework.utils

/**
 * 客户端统一指纹常量。
 *
 * 业务 HTML 请求（Jsoup）、详情正文图片（Coil / HtmlCoilImageGetter）共用同一套 UA，
 * 会话期间固定不随机切换，避免同一设备在不同链路出现 UA 抖动而被风控关联识别。
 */
const val APP_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

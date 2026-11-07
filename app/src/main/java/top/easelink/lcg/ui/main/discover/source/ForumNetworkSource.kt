package top.easelink.lcg.ui.main.discover.source

import top.easelink.lcg.R
import top.easelink.lcg.network.JsoupClient
import top.easelink.lcg.ui.main.discover.model.ForumNavigationModel
import timber.log.Timber

object ForumNetworkSource {

    fun fetchAllForumsFromApi(): List<ForumNavigationModel> {
        val list = mutableListOf<ForumNavigationModel>()
        val uniqueUrls = mutableSetOf<String>()

        try {
            // 策略 1：请求发帖页，获取最纯净的板块下拉树
            Timber.d("【板块API】尝试从发帖页面获取真实板块树...")
            val doc = JsoupClient.sendGetRequestWithUrl("https://www.52pojie.cn/forum.php?mod=post&action=newthread&fid=2")
            
            // Discuz! 标准的版块下拉框
            val options = doc.select("select[name=fid] option, select#fid option")
            Timber.d("【板块API】发帖页找到 ${options.size} 个选项")

            if (options.isNotEmpty()) {
                for (option in options) {
                    val text = option.text().trim()
                    val value = option.attr("value").trim()
                    
                    // 过滤掉“请选择”或空选项
                    if (value.matches(Regex("\\d+")) && text.isNotBlank() && !text.contains("请选择")) {
                        // 通过缩进和特殊符号判断层级（Discuz 通常用全角空格或 ├ 表示子版块）
                        val isSub = text.startsWith("&nbsp;") || text.contains("├") || text.startsWith("　") || text.startsWith(">")
                        val cleanTitle = text.replace("&nbsp;", "").replace("├", "").replace("　", "").replace(">", "").trim()
                        
                        val url = "forum-${value}-1.html"
                        if (uniqueUrls.add(url)) {
                            list.add(
                                ForumNavigationModel(
                                    title = cleanTitle,
                                    drawableRes = getIconForForum(cleanTitle),
                                    url = url,
                                    isSubForum = isSub
                                )
                            )
                        }
                    }
                }
            }

            // 策略 2：如果发帖页抓取失败，从首页兜底提取严谨的 forumdisplay 链接
            if (list.isEmpty()) {
                Timber.w("【板块API】发帖页提取失败，尝试从首页兜底提取...")
                val homeDoc = JsoupClient.sendGetRequestWithUrl("https://www.52pojie.cn/forum.php")
                val links = homeDoc.select("a[href^=forum-]")
                
                for (link in links) {
                    val title = link.text().trim()
                    val url = link.attr("href").trim()
                    
                    // 只提取 `forum-数字-1.html` 格式的链接，这绝对是版块
                    if (url.matches(Regex("forum-\\d+-1\\.html")) && title.isNotBlank() && title.length < 20) {
                        if (uniqueUrls.add(url)) {
                            list.add(
                                ForumNavigationModel(
                                    title = title,
                                    drawableRes = getIconForForum(title),
                                    url = url,
                                    isSubForum = false
                                )
                            )
                        }
                    }
                }
            }

            Timber.d("【板块API】抓取完成，最终确认 ${list.size} 个真实板块")
        } catch (e: Exception) {
            Timber.e(e, "【板块API】请求异常")
        }
        return list
    }

    private fun getIconForForum(title: String): Int {
        return when {
            title.contains("安全") || title.contains("逆向") || title.contains("病毒") || title.contains("破解") -> R.drawable.ic_sercurite
            title.contains("软件") || title.contains("程序") || title.contains("工具") || title.contains("资源") -> R.drawable.ic_software
            title.contains("原创") || title.contains("发布") -> R.drawable.ic_creator
            title.contains("水") || title.contains("闲聊") -> R.drawable.ic_water
            title.contains("福利") || title.contains("活动") -> R.drawable.ic_welfare
            else -> R.drawable.ic_software
        }
    }
}

package top.easelink.lcg.ui.main.source

import org.jsoup.nodes.Document

/**
 * 回复/发帖表单隐藏字段解析收口：纯 DOM -> 字段集合。
 */

data class ReplyFormFields(
    val formHash: String,
    val handlekey: String,
    val noticeauthor: String,
    val noticetrimstr: String,
    val noticeauthormsg: String,
    val usesig: String,
    val reppid: String,
    val reppost: String
)

fun parseReplyForm(doc: Document): ReplyFormFields {
    val noticeauthormsg = doc.selectFirst("input[name=noticeauthormsg]")?.attr("value").orEmpty()
    val noticetrimstr = doc.selectFirst("input[name=noticetrimstr]")?.attr("value").orEmpty()
    val noticeauthor = doc.selectFirst("input[name=noticeauthor]")?.attr("value").orEmpty()
    val handlekey = doc.selectFirst("input[name=handlekey]")?.attr("value") ?: "reply"
    val usesig = doc.selectFirst("input[name=usesig]")?.attr("value") ?: "1"
    val reppid = doc.selectFirst("input[name=reppid]")?.attr("value").orEmpty()
    val reppost = doc.selectFirst("input[name=reppost]")?.attr("value").orEmpty()
    val formHash = doc.selectFirst("input[name=formhash]")?.attr("value").orEmpty()
    return ReplyFormFields(
        formHash = formHash,
        handlekey = handlekey,
        noticeauthor = noticeauthor,
        noticetrimstr = noticetrimstr,
        noticeauthormsg = noticeauthormsg,
        usesig = usesig,
        reppid = reppid,
        reppost = reppost
    )
}

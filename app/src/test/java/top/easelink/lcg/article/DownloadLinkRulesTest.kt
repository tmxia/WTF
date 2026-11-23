package top.easelink.lcg.article

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import top.easelink.lcg.ui.main.article.source.DownloadLinkRules

class DownloadLinkRulesTest {

    @Test
    fun extract_blankHtml_returnsEmpty() {
        assertTrue(DownloadLinkRules.extract(null).isEmpty())
        assertTrue(DownloadLinkRules.extract("").isEmpty())
        assertTrue(DownloadLinkRules.extract("   ").isEmpty())
    }

    @Test
    fun extract_plainTextLinkAndCode_combinesWithCode() {
        val html = "分享地址 https://pan.baidu.com/s/1xyz 提取码 abcd"
        val result = DownloadLinkRules.extract(html)
        assertEquals(listOf("https://pan.baidu.com/s/1xyz 提取码: abcd"), result)
    }

    @Test
    fun extract_linkWithEmbeddedCode_keepsLinkOnly() {
        val html = "https://pan.baidu.com/s/1xyz?pwd=abcd 提取码 abcd"
        val result = DownloadLinkRules.extract(html)
        assertEquals(listOf("https://pan.baidu.com/s/1xyz?pwd=abcd"), result)
    }

    @Test
    fun extract_anchorHref_isRecognized() {
        val html = "<a href=\"https://pan.quark.cn/s/qqq123\">夸克网盘</a>"
        val result = DownloadLinkRules.extract(html)
        assertEquals(listOf("https://pan.quark.cn/s/qqq123"), result)
    }

    @Test
    fun extract_codeOnly_returnsPrefixedCode() {
        val html = "解压密码 1234，链接稍后补发"
        val result = DownloadLinkRules.extract(html)
        assertEquals(listOf("提取码: 1234"), result)
    }

    @Test
    fun extract_noLinkNoCode_returnsEmpty() {
        assertTrue(DownloadLinkRules.extract("这是一段普通正文，没有网盘资源。").isEmpty())
    }
}

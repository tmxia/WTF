package top.easelink.lcg.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LikeRulesTest {

    @Test
    fun `pid 从支持链接中精确提取`() {
        val url = "forum.php?mod=misc&action=postreview&do=support&tid=2117334&pid=55542666&hash=abc"
        assertEquals("55542666", LikeRules.pidFromUrl("https://www.52pojie.cn/$url"))
    }

    @Test
    fun `pid 缺失或空链接返回 null`() {
        assertNull(LikeRules.pidFromUrl(null))
        assertNull(LikeRules.pidFromUrl(""))
        assertNull(LikeRules.pidFromUrl("forum.php?mod=misc&action=postreview&do=support&tid=1"))
    }

    @Test
    fun `成功文案判定`() {
        assertTrue(LikeRules.isSuccessMessage("支持成功"))
        assertTrue(LikeRules.isSuccessMessage("感谢您的支持"))
        assertTrue(LikeRules.isSuccessMessage("已赞过该楼层"))
        assertTrue(LikeRules.isAlreadyLiked("您已经赞过该楼层了"))
    }

    @Test
    fun `失败文案判定`() {
        assertTrue(LikeRules.isFailureMessage("抱歉，您不能给自己点赞"))
        assertTrue(LikeRules.isFailureMessage("操作失败"))
        assertTrue(LikeRules.isFailureMessage("Error"))
        assertTrue(LikeRules.isFailureMessage(""))
        assertFalse(LikeRules.isSuccessMessage("抱歉，您不能给自己点赞"))
        assertFalse(LikeRules.isSuccessMessage("Error"))
    }
}

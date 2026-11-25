package top.easelink.lcg.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FavoriteKeyRulesTest {

    @Test
    fun `详情页绝对地址取 tid`() {
        assertEquals(
            "2117334",
            FavoriteKeyRules.threadId("https://www.52pojie.cn/thread-2117334-1-1.html")
        )
    }

    @Test
    fun `收藏列表相对地址取 tid`() {
        assertEquals("2117334", FavoriteKeyRules.threadId("thread-2117334-1-1.html"))
    }

    @Test
    fun `query 形式也取 tid`() {
        assertEquals("2117334", FavoriteKeyRules.threadId("forum.php?mod=viewthread&tid=2117334&page=1"))
        assertNull(FavoriteKeyRules.threadId(null))
    }
}

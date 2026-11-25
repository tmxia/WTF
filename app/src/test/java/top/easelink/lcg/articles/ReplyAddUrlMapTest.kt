package top.easelink.lcg.articles

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import top.easelink.lcg.ui.main.source.getPostIds
import top.easelink.lcg.ui.main.source.getReplyAddUrlMap

class ReplyAddUrlMapTest {

    private val html = """
        <html><body>
          <a id="recommend_add" href="forum.php?mod=misc&amp;action=postreview&amp;do=support&amp;tid=1&amp;pid=111&amp;hash=a">支持</a>
          <div id="post_111">
            <div class="pcb">one</div>
          </div>
          <div id="post_222">
            <div class="pcb">two</div>
            <!-- 已赞楼层：站点不再输出 replyadd -->
          </div>
          <div id="post_333">
            <div class="pcb">three</div>
            <a class="replyadd" href="forum.php?mod=misc&amp;action=postreview&amp;do=support&amp;tid=1&amp;pid=333&amp;hash=c">支持</a>
          </div>
        </body></html>
    """.trimIndent()

    @Test
    fun `楼层顺序与 pid 一致`() {
        val doc = Jsoup.parse(html)
        assertEquals(listOf("111", "222", "333"), getPostIds(doc))
    }

    @Test
    fun `已赞楼层不再输出 liked 链接时不会造成后面楼层错位`() {
        val doc = Jsoup.parse(html)
        val map = getReplyAddUrlMap(doc)
        // 222 楼已赞：站点不输出它的 replyadd，其余楼层仍各自归属
        assertTrue(map["111"]!!.contains("pid=111"))
        assertFalse(map.containsKey("222"))
        assertTrue(map["333"]!!.contains("pid=333"))
    }
}

package top.easelink.lcg.articles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import top.easelink.lcg.ui.main.articles.source.ForumQueryRules

class ForumQueryRulesTest {

    @Test
    fun safeOrderby_whitelistedPassesThrough() {
        assertEquals("dateline", ForumQueryRules.safeOrderby("dateline"))
        assertEquals("lastpost", ForumQueryRules.safeOrderby("lastpost"))
    }

    @Test
    fun safeOrderby_rejectsUnknownAndNull() {
        assertNull(ForumQueryRules.safeOrderby("views"))
        assertNull(ForumQueryRules.safeOrderby(null))
        assertNull(ForumQueryRules.safeOrderby(""))
    }

    @Test
    fun safeDateline_whitelistedPassesThrough() {
        assertEquals(7, ForumQueryRules.safeDateline(7))
        assertEquals(365, ForumQueryRules.safeDateline(365))
    }

    @Test
    fun safeDateline_rejectsUnknownAndNull() {
        assertNull(ForumQueryRules.safeDateline(3))
        assertNull(ForumQueryRules.safeDateline(null))
    }

    @Test
    fun orderbyParam_buildsParamOrEmpty() {
        assertEquals("&orderby=dateline", ForumQueryRules.orderbyParam("dateline"))
        assertEquals("", ForumQueryRules.orderbyParam(null))
    }

    @Test
    fun datelineFilter_convertsDaysToSeconds() {
        // 7 天 = 7 * 86400 = 604800 秒
        assertEquals("&filter=dateline&dateline=604800", ForumQueryRules.datelineFilter(7))
        assertEquals("", ForumQueryRules.datelineFilter(null))
    }

    @Test
    fun secondsPerDayConstant() {
        assertEquals(86400, ForumQueryRules.SECONDS_PER_DAY)
    }
}

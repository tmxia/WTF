package top.easelink.lcg.utils

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TimeUtilsTest {

    @Test
    fun parseValidDateTimeString() {
        assertNotNull("2024-05-20 12:30".toTimeStamp())
    }

    @Test
    fun parseInvalidStringReturnsNull() {
        assertNull("not a date".toTimeStamp())
        assertNull("".toTimeStamp())
    }
}

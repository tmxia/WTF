package top.easelink.lcg.utils

import org.json.JSONObject

object WafSolver {

    fun isWafPage(html: String?): Boolean {
        if (html.isNullOrEmpty()) return false
        return (html.contains("wzwsquestion") || html.contains("LZ=")) && html.contains("waf_zw_verify")
    }

    fun extractWafParams(html: String): List<String>? {
        val qRegex = Regex("""wzwsquestion='(\d+)'|LZ='(\d+)'""")
        val fRegex = Regex("""wzwsfactor='(\d+)'|LJ='(\d+)'""")
        val bRegex = Regex("""base64_chars='([^']+)'|LE='([^']+)'""")
        val apiRegex = Regex("""dynamicapi='([^']+)'""")

        val qMatch = qRegex.find(html)
        val fMatch = fRegex.find(html)
        val bMatch = bRegex.find(html)
        val apiMatch = apiRegex.find(html)

        if (qMatch != null && fMatch != null && bMatch != null) {
            val q = qMatch.groupValues[1].ifEmpty { qMatch.groupValues[2] }
            val f = fMatch.groupValues[1].ifEmpty { fMatch.groupValues[2] }
            val b = bMatch.groupValues[1].ifEmpty { bMatch.groupValues[2] }
            val api = apiMatch?.groupValues?.get(1) ?: "/waf_zw_verify"
            return listOf(q, f, b, api)
        }
        return null
    }

    fun calcAnswer(question: String, factor: Int): String {
        var d = 0L
        var s = 1L
        for ((p, char) in question.withIndex()) {
            d = 2 * (d + char.code)
            s = 2 * (s + p + 1)
        }
        d = d * factor + s
        return "WZWS_CONFIRM_PREFIX_LABEL$d"
    }

    fun encodeCustomBase64(data: ByteArray, base64Chars: String): String {
        val p = StringBuilder()
        val length = data.size
        var q = 0
        while (q < length) {
            val f = data[q++].toInt() and 0xFF
            if (q == length) {
                p.append(base64Chars[f shr 2])
                p.append(base64Chars[(f and 3) shl 4])
                p.append("==")
                break
            }
            val u = data[q++].toInt() and 0xFF
            if (q == length) {
                p.append(base64Chars[f shr 2])
                p.append(base64Chars[((f and 3) shl 4) or ((u and 0xF0) shr 4)])
                p.append(base64Chars[(u and 0x0F) shl 2])
                p.append("=")
                break
            }
            val z = data[q++].toInt() and 0xFF
            p.append(base64Chars[f shr 2])
            p.append(base64Chars[((f and 3) shl 4) or ((u and 0xF0) shr 4)])
            p.append(base64Chars[((u and 0x0F) shl 2) or ((z and 0xC0) shr 6)])
            p.append(base64Chars[z and 0x3F])
        }
        return p.toString()
    }

    fun buildFpPayload(timestamp: Long, answer: String, userAgent: String): String {
        val y = if (timestamp % 10 == 0L) 10 else (timestamp % 10).toInt()
        // 这里使用简化版但结构正确的 JSON
        val fpInfos = JSONObject()
        fpInfos.put("plugins", JSONObject().apply {
            put("names", org.json.JSONArray(listOf("PDF Viewer", "Chrome PDF Viewer")))
            put("fp", "9772d5556d57fcc8177f76029bfd92ef")
            put("verify", 100 * y)
        })
        fpInfos.put("fonts", JSONObject().apply {
            put("names", org.json.JSONArray(listOf("Arial", "Helvetica")))
            put("fp", "1b16b21419cf4eed8bdae341d01ae629")
            put("verify", 50 * y)
        })
        fpInfos.put("screenObject", JSONObject().apply {
            put("screenResolution", org.json.JSONArray(listOf(1920, 1080)))
            put("verify", (1920+1080) * y)
        })
        fpInfos.put("navigatorObject", JSONObject().apply {
            put("userAgent", userAgent)
            put("platform", "Win32")
            put("verify", userAgent.length * y)
        })
        fpInfos.put("dateTime", JSONObject().apply { put("timestamp", timestamp); put("verify", timestamp.toString().length * y) })
        
        return JSONObject().apply {
            put("fp_infos", fpInfos)
            put("answer", answer)
            put("hostname", "www.52pojie.cn")
            put("scheme", "https")
        }.toString()
    }
}

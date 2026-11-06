package top.easelink.lcg.utils

import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

object WafSolver {

    fun isWafPage(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        return (text.contains("wzwsquestion") || text.contains("LZ=")) && text.contains("waf_zw_verify")
    }

    fun calcAnswer(question: String, factor: Int): String {
        var d = 0
        var s = 1
        for ((p, char) in question.withIndex()) {
            d = 2 * (d + char.code)
            s = 2 * (s + p + 1)
        }
        d = d * factor + s
        return "WZWS_CONFIRM_PREFIX_LABEL$d"
    }

    fun encodeCustomBase64(dataBytes: ByteArray, base64Chars: String): String {
        val p = StringBuilder()
        val length = dataBytes.size
        var q = 0
        while (q < length) {
            val f = dataBytes[q].toInt() and 0xFF
            q++
            if (q == length) {
                p.append(base64Chars[f ushr 2])
                p.append(base64Chars[(f and 3) shl 4])
                p.append("==")
                break
            }
            val u = dataBytes[q].toInt() and 0xFF
            q++
            if (q == length) {
                p.append(base64Chars[f ushr 2])
                p.append(base64Chars[((f and 3) shl 4) or ((u and 0xF0) ushr 4)])
                p.append(base64Chars[(u and 0xF) shl 2])
                p.append("=")
                break
            }
            val z = dataBytes[q].toInt() and 0xFF
            q++
            p.append(base64Chars[f ushr 2])
            p.append(base64Chars[((f and 3) shl 4) or ((u and 0xF0) ushr 4)])
            p.append(base64Chars[((u and 0xF) shl 2) or ((z and 0xC0) ushr 6)])
            p.append(base64Chars[z and 0x3F])
        }
        return p.toString()
    }

    fun buildFpPayload(timestamp: Long, answer: String, userAgent: String): String {
        val y = (timestamp % 10).toInt().takeIf { it != 0 } ?: 10

        val pluginsDetails = JSONArray().apply {
            listOf("PDF Viewer", "Chrome PDF Viewer", "Chromium PDF Viewer", "Microsoft Edge PDF Viewer", "WebKit built-in PDF").forEach { name ->
                put(JSONObject().apply {
                    put("name", name)
                    put("description", "Portable Document Format")
                    put("filename", "internal-pdf-viewer")
                    put("mimetypes", JSONArray().apply {
                        put(JSONObject().apply { put("type", "application/pdf"); put("suffixes", "pdf") })
                        put(JSONObject().apply { put("type", "text/pdf"); put("suffixes", "pdf") })
                    })
                })
            }
        }
        
        val pluginNames = JSONArray().apply {
            for (i in 0 until pluginsDetails.length()) put(pluginsDetails.getJSONObject(i).getString("name"))
        }

        val fpInfos = JSONObject().apply {
            put("errors", JSONObject())
            put("plugins", JSONObject().apply {
                put("details", pluginsDetails); put("names", pluginNames); put("fp", "9772d5556d57fcc8177f76029bfd92ef")
            })
            put("fonts", JSONObject().apply {
                put("names", JSONArray().apply {
                    listOf("Arial", "Arial Black", "Calibri", "Comic Sans MS", "Courier New", "Georgia", "Helvetica", "Impact", "Segoe UI", "Tahoma", "Times New Roman", "Verdana").forEach { put(it) }
                })
                put("fp", "1b16b21419cf4eed8bdae341d01ae629")
            })
            put("screenObject", JSONObject().apply {
                put("screenResolution", JSONArray().put(1920).put(1080))
                put("availableScreenResolution", JSONArray().put(1920).put(1040))
                put("colorDepth", 24); put("availTop", 0); put("availLeft", 0)
                put("isExtended", false); put("pixelDepth", 24)
                put("orientation", JSONObject().apply { put("angle", 0); put("type", "landscape-primary") })
            })
            put("intlObject", JSONObject().apply {
                put("locale", "zh-CN"); put("calendar", "gregory"); put("numberingSystem", "latn")
                put("timeZone", "Asia/Shanghai"); put("year", "numeric"); put("month", "numeric")
                put("day", "numeric"); put("timezoneOffset", -480)
            })
            put("touchSupport", JSONArray().put(0).put(false).put(false))
            put("audio", "124.04347527516074"); put("webdriver", false)
            put("webGL", JSONObject().apply {
                put("webgl_version", "WebGL 1.0 (OpenGL ES 2.0 Chromium)")
                put("webgl_vendor_and_renderer", "Google Inc. (NVIDIA)~ANGLE (NVIDIA, NVIDIA GeForce RTX 4060 Laptop GPU Direct3D11 vs_5_0 ps_5_0, D3D11)")
                put("webgl_unmasked_renderer", "ANGLE (NVIDIA, NVIDIA GeForce RTX 4060 Laptop GPU Direct3D11 vs_5_0 ps_5_0, D3D11)")
                put("webgl_unmasked_vendor", "Google Inc. (NVIDIA)")
                put("webgl_aliased_point_size_range", JSONArray().put(1).put(1024))
                put("webgl_fragment_shader_medium_int_precision_rangeMax", 30)
                put("webgl_fragment_shader_medium_int_precision_rangeMin", 31)
                put("fp", "ba6faa254e58a06a3de7ed87b7f1f4e5")
            })
            put("canvas", JSONObject().apply { put("canvas_winding", true); put("fp", "5f1789b44d2008b266b283ffa91bb040") })
            put("deviceInfos", JSONObject().apply { put("deviceMemory", 8); put("hardwareConcurrency", 16) })
            put("storageObject", JSONObject().apply { put("localStorage", true); put("openDatabase", false); put("indexedDb", true); put("sessionStorage", true); put("addBehavior", false) })
            put("navigatorObject", JSONObject().apply {
                put("userAgent", userAgent); put("platform", "Win32"); put("vendor", "Google Inc.")
                put("language", "zh-CN"); put("languages", JSONArray().put("zh-CN").put("zh").put("en"))
                put("productSub", "20030107")
            })
            put("functions", JSONObject().apply { put("eval_tostring_length", 33) })
            put("dateTime", JSONObject().apply { put("timestamp", timestamp) })
            put("fp", "11f1f389f6f17388040dfca306404b31"); put("protocol", "https")
        }
        
        val keys = fpInfos.keys()
        while (keys.hasNext()) {
            val section = fpInfos.opt(keys.next())
            if (section is JSONObject) {
                var x = 0
                val secKeys = section.keys()
                while (secKeys.hasNext()) {
                    val v = section.opt(secKeys.next())
                    when (v) {
                        is Number -> if (v !is Boolean) x += v.toInt()
                        is String -> x += v.length
                        else -> x += y
                    }
                }
                if (x > 0) section.put("verify", x * y)
            }
        }
        
        return JSONObject().apply {
            put("fp_infos", fpInfos); put("answer", answer); put("hostname", "www.52pojie.cn"); put("scheme", "https")
        }.toString()
    }

    fun extractWafParams(html: String): List<String>? {
        // 兼容单引号和双引号
        val qMatch = Regex("wzwsquestion=['\"](\\d+)['\"]").find(html) ?: Regex("LZ=['\"](\\d+)['\"]").find(html)
        val fMatch = Regex("wzwsfactor=['\"](\\d+)['\"]").find(html) ?: Regex("LJ=['\"](\\d+)['\"]").find(html)
        val bMatch = Regex("base64_chars=['\"]([^'\"]+)['\"]").find(html) ?: Regex("LE=['\"]([^'\"]+)['\"]").find(html)
        val apiMatch = Regex("dynamicapi=['\"]([^'\"]+)['\"]").find(html)

        if (qMatch != null && fMatch != null && bMatch != null) {
            val api = apiMatch?.groupValues?.get(1) ?: "/waf_zw_verify"
            return listOf(qMatch.groupValues[1], fMatch.groupValues[1], bMatch.groupValues[1], api)
        }
        return null
    }
}

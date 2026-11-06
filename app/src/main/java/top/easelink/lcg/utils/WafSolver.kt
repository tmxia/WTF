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

    private fun calcVerify(section: Map<String, Any>, y: Int): Int {
        var x = 0
        for ((_, value) in section) {
            when (value) {
                is Int -> x += value
                is Long -> x += value.toInt()
                is Double -> x += value.toInt()
                is Boolean -> x += y
                is String -> x += value.length
                else -> x += y
            }
        }
        return x * y
    }

    fun buildFpPayload(timestamp: Long, answer: String, userAgent: String): String {
        val y = if (timestamp % 10 == 0L) 10 else (timestamp % 10).toInt()
        val fpInfos = LinkedHashMap<String, Any>()

        val pluginsDetails = listOf(
            mapOf("name" to "PDF Viewer", "description" to "Portable Document Format", "filename" to "internal-pdf-viewer", "mimetypes" to listOf(mapOf("type" to "application/pdf", "suffixes" to "pdf"), mapOf("type" to "text/pdf", "suffixes" to "pdf"))),
            mapOf("name" to "Chrome PDF Viewer", "description" to "Portable Document Format", "filename" to "internal-pdf-viewer", "mimetypes" to listOf(mapOf("type" to "application/pdf", "suffixes" to "pdf"), mapOf("type" to "text/pdf", "suffixes" to "pdf"))),
            mapOf("name" to "Chromium PDF Viewer", "description" to "Portable Document Format", "filename" to "internal-pdf-viewer", "mimetypes" to listOf(mapOf("type" to "application/pdf", "suffixes" to "pdf"), mapOf("type" to "text/pdf", "suffixes" to "pdf"))),
            mapOf("name" to "Microsoft Edge PDF Viewer", "description" to "Portable Document Format", "filename" to "internal-pdf-viewer", "mimetypes" to listOf(mapOf("type" to "application/pdf", "suffixes" to "pdf"), mapOf("type" to "text/pdf", "suffixes" to "pdf"))),
            mapOf("name" to "WebKit built-in PDF", "description" to "Portable Document Format", "filename" to "internal-pdf-viewer", "mimetypes" to listOf(mapOf("type" to "application/pdf", "suffixes" to "pdf"), mapOf("type" to "text/pdf", "suffixes" to "pdf")))
        )
        val pluginNames = listOf("PDF Viewer", "Chrome PDF Viewer", "Chromium PDF Viewer", "Microsoft Edge PDF Viewer", "WebKit built-in PDF")

        val pluginsMap = linkedMapOf<String, Any>("details" to pluginsDetails, "names" to pluginNames, "fp" to "9772d5556d57fcc8177f76029bfd92ef")
        pluginsMap["verify"] = calcVerify(pluginsMap, y)
        fpInfos["plugins"] = pluginsMap

        val errorsMap = linkedMapOf<String, Any>()
        errorsMap["verify"] = calcVerify(errorsMap, y)
        fpInfos["errors"] = errorsMap

        val fontsMap = linkedMapOf<String, Any>("names" to listOf("Arial", "Arial Black", "Calibri", "Comic Sans MS", "Courier New", "Georgia", "Helvetica", "Impact", "Segoe UI", "Tahoma", "Times New Roman", "Verdana"), "fp" to "1b16b21419cf4eed8bdae341d01ae629")
        fontsMap["verify"] = calcVerify(fontsMap, y)
        fpInfos["fonts"] = fontsMap

        val screenMap = linkedMapOf<String, Any>("screenResolution" to listOf(1920, 1080), "availableScreenResolution" to listOf(1920, 1040), "colorDepth" to 24, "availTop" to 0, "availLeft" to 0, "isExtended" to false, "pixelDepth" to 24, "orientation" to mapOf("angle" to 0, "type" to "landscape-primary"))
        screenMap["verify"] = calcVerify(screenMap, y)
        fpInfos["screenObject"] = screenMap

        val intlMap = linkedMapOf<String, Any>("locale" to "zh-CN", "calendar" to "gregory", "numberingSystem" to "latn", "timeZone" to "Asia/Shanghai", "year" to "numeric", "month" to "numeric", "day" to "numeric", "timezoneOffset" to -480)
        intlMap["verify"] = calcVerify(intlMap, y)
        fpInfos["intlObject"] = intlMap

        fpInfos["touchSupport"] = listOf(0, false, false)
        fpInfos["audio"] = "124.04347527516074"
        fpInfos["webdriver"] = false

        val webGlMap = linkedMapOf<String, Any>("webgl_version" to "WebGL 1.0 (OpenGL ES 2.0 Chromium)", "webgl_vendor_and_renderer" to "Google Inc. (NVIDIA)~ANGLE (NVIDIA, NVIDIA GeForce RTX 4060 Laptop GPU Direct3D11 vs_5_0 ps_5_0, D3D11)", "webgl_unmasked_renderer" to "ANGLE (NVIDIA, NVIDIA GeForce RTX 4060 Laptop GPU Direct3D11 vs_5_0 ps_5_0, D3D11)", "webgl_unmasked_vendor" to "Google Inc. (NVIDIA)", "webgl_aliased_point_size_range" to listOf(1, 1024), "webgl_fragment_shader_medium_int_precision_rangeMax" to 30, "webgl_fragment_shader_medium_int_precision_rangeMin" to 31, "fp" to "ba6faa254e58a06a3de7ed87b7f1f4e5")
        webGlMap["verify"] = calcVerify(webGlMap, y)
        fpInfos["webGL"] = webGlMap

        val canvasMap = linkedMapOf<String, Any>("canvas_winding" to true, "fp" to "5f1789b44d2008b266b283ffa91bb040")
        canvasMap["verify"] = calcVerify(canvasMap, y)
        fpInfos["canvas"] = canvasMap

        val deviceMap = linkedMapOf<String, Any>("deviceMemory" to 8, "hardwareConcurrency" to 16)
        deviceMap["verify"] = calcVerify(deviceMap, y)
        fpInfos["deviceInfos"] = deviceMap

        val storageMap = linkedMapOf<String, Any>("localStorage" to true, "openDatabase" to false, "indexedDb" to true, "sessionStorage" to true, "addBehavior" to false)
        storageMap["verify"] = calcVerify(storageMap, y)
        fpInfos["storageObject"] = storageMap

        val navMap = linkedMapOf<String, Any>("userAgent" to userAgent, "platform" to "Win32", "vendor" to "Google Inc.", "language" to "zh-CN", "languages" to listOf("zh-CN", "zh", "en"), "productSub" to "20030107")
        navMap["verify"] = calcVerify(navMap, y)
        fpInfos["navigatorObject"] = navMap

        val funcMap = linkedMapOf<String, Any>("eval_tostring_length" to 33)
        funcMap["verify"] = calcVerify(funcMap, y)
        fpInfos["functions"] = funcMap

        val dateMap = linkedMapOf<String, Any>("timestamp" to timestamp)
        dateMap["verify"] = calcVerify(dateMap, y)
        fpInfos["dateTime"] = dateMap

        fpInfos["fp"] = "11f1f389f6f17388040dfca306404b31"
        fpInfos["protocol"] = "https"

        val finalPayload = linkedMapOf<String, Any>(
            "fp_infos" to fpInfos,
            "answer" to answer,
            "hostname" to "www.52pojie.cn",
            "scheme" to "https"
        )
        return JSONObject(finalPayload).toString()
    }
}

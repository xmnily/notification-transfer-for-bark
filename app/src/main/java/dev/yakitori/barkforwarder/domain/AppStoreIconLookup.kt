package dev.yakitori.barkforwarder.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 通过 App Store 搜索接口按应用名拿到公开的图标 URL（mzstatic CDN）。
 * Bark 的 icon 只接受公开 URL，而本机图标文件无法被 iPhone 访问；
 * 应用商店的图标链接正好是公开且长期有效的。
 */
class AppStoreIconLookup(private val httpClient: OkHttpClient) {
    suspend fun lookup(label: String): String? = withContext(Dispatchers.IO) {
        if (label.isBlank()) return@withContext null
        val url = "https://itunes.apple.com/search".toHttpUrl().newBuilder()
            .addQueryParameter("term", label)
            .addQueryParameter("country", COUNTRY)
            .addQueryParameter("entity", "software")
            .addQueryParameter("limit", RESULT_LIMIT.toString())
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()
        runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                pickIconUrl(response.body?.string().orEmpty(), label)
            }
        }.getOrNull()
    }

    internal fun pickIconUrl(body: String, label: String): String? {
        val wanted = normalizeName(label)
        if (wanted.isEmpty()) return null
        val results = runCatching {
            JSON.parseToJsonElement(body).jsonObject["results"]?.jsonArray
        }.getOrNull() ?: return null

        var best: JsonObject? = null
        var bestScore = Int.MAX_VALUE
        var bestLength = Int.MAX_VALUE
        for (element in results) {
            val candidate = element as? JsonObject ?: continue
            val trackName = candidate.string("trackName") ?: continue
            val name = normalizeName(trackName)
            val score = when {
                name == wanted -> 0
                name.startsWith(wanted) -> 1
                name.contains(wanted) -> 2
                else -> continue
            }
            if (score < bestScore || (score == bestScore && name.length < bestLength)) {
                best = candidate
                bestScore = score
                bestLength = name.length
            }
        }
        return best?.let(::artworkUrl)
    }

    private fun artworkUrl(result: JsonObject): String? {
        result.string("artworkUrl512")
            ?.takeIf { it.startsWith("https://") }
            ?.let { return it }
        val fallback = result.string("artworkUrl100") ?: result.string("artworkUrl60") ?: return null
        return fallback
            .replace("100x100bb", "512x512bb")
            .replace("60x60bb", "512x512bb")
            .takeIf { it.startsWith("https://") }
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun normalizeName(value: String): String =
        value.lowercase().filterNot { it.isWhitespace() || it == '·' || it == '・' }

    private companion object {
        const val COUNTRY = "cn"
        const val RESULT_LIMIT = 5
        const val USER_AGENT = "BarkForwarder/1.0 (Android)"
        val JSON = Json { ignoreUnknownKeys = true; isLenient = true }
    }
}

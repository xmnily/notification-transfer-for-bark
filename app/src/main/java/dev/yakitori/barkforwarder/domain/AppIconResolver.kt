package dev.yakitori.barkforwarder.domain

import dev.yakitori.barkforwarder.data.repo.AppRuleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 解析 Bark 可用的图标 URL。
 *
 * iOS 端只能下载公开 URL，本地导出的 PNG 文件无法直接被引用；
 * 因此按 手动 URL → 缓存 → App Store 公开图标 → Play 商店网页 的顺序解析。
 */
class AppIconResolver(
    private val httpClient: OkHttpClient,
    private val appRuleRepository: AppRuleRepository,
    private val appStoreIconLookup: AppStoreIconLookup,
) {
    /** 仅返回手动设置或已缓存的图标，绝不发起网络请求。 */
    suspend fun resolveCached(packageName: String): String? {
        val rule = appRuleRepository.getRule(packageName) ?: return null
        rule.manualIconUrl?.takeIf { it.isNotBlank() }?.let { return it }
        return rule.cachedResolvedIconUrl?.takeIf { it.isNotBlank() }
    }

    /** 忽略缓存与失败记录，立即重新解析一次（手动刷新用）；失败时保留原图标。 */
    suspend fun refresh(packageName: String): String? {
        val previous = appRuleRepository.getRule(packageName)
            ?.cachedResolvedIconUrl
            ?.takeIf { it.isNotBlank() }
        val resolved = resolve(packageName, force = true)
        if (resolved == null && previous != null) {
            appRuleRepository.cacheResolvedIconUrl(packageName, previous)
            return previous
        }
        return resolved
    }

    suspend fun resolve(packageName: String, force: Boolean = false): String? {
        val rule = appRuleRepository.getRule(packageName)
        rule?.manualIconUrl?.takeIf { it.isNotBlank() }?.let { return it }

        val cached = rule?.cachedResolvedIconUrl?.takeIf { it.isNotBlank() }
        if (!force && cached != null) return cached

        val lastAttempt = rule?.iconResolvedAt
        if (!force && lastAttempt != null &&
            System.currentTimeMillis() - lastAttempt < FAILURE_RETRY_MILLIS
        ) {
            return null
        }

        val label = rule?.appLabel?.takeIf { it.isNotBlank() && it != packageName }
            ?: appRuleRepository.resolveAppLabel(packageName)
        val resolved = appStoreIconLookup.lookup(label) ?: fetchFromPlay(packageName)
        if (resolved != null) {
            appRuleRepository.cacheResolvedIconUrl(packageName, resolved)
        } else {
            appRuleRepository.markIconResolutionAttempt(packageName)
        }
        return resolved
    }

    private suspend fun fetchFromPlay(packageName: String): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://play.google.com/store/apps/details?id=$packageName&hl=ja")
            .header("User-Agent", "Mozilla/5.0")
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val html = response.body?.string().orEmpty()
            PlayIconParser.extractIconUrl(html)
        }
    }

    private companion object {
        const val FAILURE_RETRY_MILLIS = 60L * 60L * 1000L
    }
}

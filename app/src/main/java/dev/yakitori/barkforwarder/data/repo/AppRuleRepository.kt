package dev.yakitori.barkforwarder.data.repo

import android.content.pm.PackageManager
import dev.yakitori.barkforwarder.data.db.AppRuleDao
import dev.yakitori.barkforwarder.data.db.AppRuleEntity
import dev.yakitori.barkforwarder.data.model.AppRule
import dev.yakitori.barkforwarder.data.model.InstalledApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AppRuleRepository(
    private val packageManager: PackageManager,
    private val selfPackageName: String,
    private val dao: AppRuleDao,
) {
    fun observeRules(): Flow<List<AppRule>> {
        return dao.observeAll().map { entities ->
            entities.map { it.toModel() }
        }
    }

    suspend fun syncInstalledApps(installedApps: List<InstalledApp>) {
        val existing = dao.getAll().associateBy { it.packageName }
        val synced = installedApps.map { app ->
            val current = existing[app.packageName]
            AppRuleEntity(
                packageName = app.packageName,
                appLabel = app.appLabel,
                excluded = current?.excluded ?: (app.packageName == selfPackageName),
                manualIconUrl = current?.manualIconUrl,
                cachedResolvedIconUrl = current?.cachedResolvedIconUrl,
                iconResolvedAt = current?.iconResolvedAt,
            )
        }
        dao.upsertAll(synced)
    }

    suspend fun ensureRule(packageName: String, appLabel: String) {
        val current = dao.getByPackageName(packageName)
        if (current == null || current.appLabel != appLabel) {
            dao.upsert(
                AppRuleEntity(
                    packageName = packageName,
                    appLabel = appLabel,
                    excluded = current?.excluded ?: (packageName == selfPackageName),
                    manualIconUrl = current?.manualIconUrl,
                    cachedResolvedIconUrl = current?.cachedResolvedIconUrl,
                    iconResolvedAt = current?.iconResolvedAt,
                ),
            )
        }
    }

    suspend fun updateExcluded(packageName: String, excluded: Boolean) {
        val current = dao.getByPackageName(packageName) ?: return
        dao.upsert(current.copy(excluded = excluded || packageName == selfPackageName))
    }

    suspend fun updateManualIconUrl(packageName: String, manualIconUrl: String?) {
        val current = dao.getByPackageName(packageName) ?: return
        val trimmed = manualIconUrl?.trim().takeUnless { it.isNullOrBlank() }
        dao.upsert(
            current.copy(
                manualIconUrl = trimmed,
                // 缓存里可能存的就是旧的手动 URL，清除时必须一起清掉，否则仍会沿用该图标。
                cachedResolvedIconUrl = if (trimmed == null) null else current.cachedResolvedIconUrl,
                iconResolvedAt = if (trimmed == null) null else current.iconResolvedAt,
            ),
        )
    }

    suspend fun cacheResolvedIconUrl(packageName: String, iconUrl: String?) {
        val current = dao.getByPackageName(packageName) ?: return
        dao.upsert(current.copy(cachedResolvedIconUrl = iconUrl, iconResolvedAt = System.currentTimeMillis()))
    }

    /** 记录一次解析尝试但保留旧缓存，用于失败后的退避重试。 */
    suspend fun markIconResolutionAttempt(packageName: String) {
        val current = dao.getByPackageName(packageName) ?: return
        dao.upsert(current.copy(iconResolvedAt = System.currentTimeMillis()))
    }

    suspend fun getRule(packageName: String): AppRule? = dao.getByPackageName(packageName)?.toModel()

    suspend fun getAllRules(): List<AppRule> = dao.getAll().map { it.toModel() }

    fun resolveAppLabel(packageName: String): String {
        return runCatching {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        }.getOrDefault(packageName)
    }
}


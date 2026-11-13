package dev.yakitori.barkforwarder

import android.content.Context
import dev.yakitori.barkforwarder.data.db.AppDatabase
import dev.yakitori.barkforwarder.data.prefs.SettingsRepository
import dev.yakitori.barkforwarder.data.repo.AppRuleRepository
import dev.yakitori.barkforwarder.data.repo.DedupeRepository
import dev.yakitori.barkforwarder.data.repo.InstalledAppRepository
import dev.yakitori.barkforwarder.data.repo.NotificationHistoryRepository
import dev.yakitori.barkforwarder.data.repo.NotificationRuleRepository
import dev.yakitori.barkforwarder.domain.BarkPushClient
import dev.yakitori.barkforwarder.domain.ForwardingEngine
import dev.yakitori.barkforwarder.domain.AppIconResolver
import dev.yakitori.barkforwarder.domain.AppStoreIconLookup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val database by lazy { AppDatabase.create(appContext) }
    private val httpClient by lazy {
        OkHttpClient.Builder()
            .callTimeout(10, TimeUnit.SECONDS)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    val settingsRepository by lazy { SettingsRepository(appContext) }
    val appRuleRepository by lazy {
        AppRuleRepository(
            packageManager = appContext.packageManager,
            selfPackageName = appContext.packageName,
            dao = database.appRuleDao(),
        )
    }
    val installedAppRepository by lazy { InstalledAppRepository(appContext, appRuleRepository) }
    val dedupeRepository by lazy { DedupeRepository(database.dedupeDao()) }
    val notificationHistoryRepository by lazy { NotificationHistoryRepository(database.notificationHistoryDao()) }
    val notificationRuleRepository by lazy { NotificationRuleRepository(database.notificationRuleDao()) }
    val appIconResolver by lazy {
        AppIconResolver(
            httpClient = httpClient,
            appRuleRepository = appRuleRepository,
            appStoreIconLookup = AppStoreIconLookup(httpClient),
        )
    }
    val barkPushClient by lazy { BarkPushClient(httpClient) }
    val forwardingEngine by lazy {
        ForwardingEngine(
            settingsRepository = settingsRepository,
            appRuleRepository = appRuleRepository,
            notificationRuleRepository = notificationRuleRepository,
            notificationHistoryRepository = notificationHistoryRepository,
            dedupeRepository = dedupeRepository,
            appIconResolver = appIconResolver,
            barkPushClient = barkPushClient,
            backgroundScope = applicationScope,
        )
    }
}

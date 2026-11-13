package dev.yakitori.barkforwarder.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.yakitori.barkforwarder.data.model.BarkConfig
import dev.yakitori.barkforwarder.data.model.NotificationFilterConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(
    private val context: Context,
) {
    val barkConfig: Flow<BarkConfig> = context.dataStore.data.map { prefs ->
        BarkConfig(
            serverUrl = prefs[Keys.SERVER_URL] ?: DEFAULT_SERVER_URL,
            deviceKey = prefs[Keys.DEVICE_KEY] ?: "",
            forwardingEnabled = prefs[Keys.FORWARDING_ENABLED] ?: true,
            notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true,
            smsEnabled = prefs[Keys.SMS_ENABLED] ?: true,
            callsEnabled = prefs[Keys.CALLS_ENABLED] ?: true,
            ringIncomingCalls = prefs[Keys.RING_INCOMING_CALLS] ?: true,
        )
    }

    val notificationFilterConfig: Flow<NotificationFilterConfig> = context.dataStore.data.map { prefs ->
        NotificationFilterConfig(
            duplicateFilterEnabled = prefs[Keys.DUPLICATE_FILTER_ENABLED] ?: true,
            duplicateWindowSeconds = (prefs[Keys.DUPLICATE_WINDOW_SECONDS] ?: 5).coerceIn(1, 120),
            filterOngoingNotifications = prefs[Keys.FILTER_ONGOING] ?: true,
            chatOptimizationEnabled = prefs[Keys.CHAT_OPTIMIZATION] ?: true,
            historyLogsEnabled = prefs[Keys.HISTORY_LOGS_ENABLED] ?: true,
        )
    }

    val hideFromRecents: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.HIDE_FROM_RECENTS] ?: false
    }

    suspend fun setHideFromRecents(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.HIDE_FROM_RECENTS] = enabled
        }
    }

    suspend fun updateBarkConfig(config: BarkConfig) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SERVER_URL] = config.serverUrl.ifBlank { DEFAULT_SERVER_URL }
            prefs[Keys.DEVICE_KEY] = config.deviceKey
            prefs[Keys.FORWARDING_ENABLED] = config.forwardingEnabled
            prefs[Keys.NOTIFICATIONS_ENABLED] = config.notificationsEnabled
            prefs[Keys.SMS_ENABLED] = config.smsEnabled
            prefs[Keys.CALLS_ENABLED] = config.callsEnabled
            prefs[Keys.RING_INCOMING_CALLS] = config.ringIncomingCalls
        }
    }

    suspend fun updateNotificationFilterConfig(config: NotificationFilterConfig) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DUPLICATE_FILTER_ENABLED] = config.duplicateFilterEnabled
            prefs[Keys.DUPLICATE_WINDOW_SECONDS] = config.duplicateWindowSeconds.coerceIn(1, 120)
            prefs[Keys.FILTER_ONGOING] = config.filterOngoingNotifications
            prefs[Keys.CHAT_OPTIMIZATION] = config.chatOptimizationEnabled
            prefs[Keys.HISTORY_LOGS_ENABLED] = config.historyLogsEnabled
        }
    }

    private object Keys {
        val SERVER_URL: Preferences.Key<String> = stringPreferencesKey("server_url")
        val DEVICE_KEY: Preferences.Key<String> = stringPreferencesKey("device_key")
        val FORWARDING_ENABLED: Preferences.Key<Boolean> = booleanPreferencesKey("forwarding_enabled")
        val NOTIFICATIONS_ENABLED: Preferences.Key<Boolean> = booleanPreferencesKey("notifications_enabled")
        val SMS_ENABLED: Preferences.Key<Boolean> = booleanPreferencesKey("sms_enabled")
        val CALLS_ENABLED: Preferences.Key<Boolean> = booleanPreferencesKey("calls_enabled")
        val RING_INCOMING_CALLS: Preferences.Key<Boolean> = booleanPreferencesKey("ring_incoming_calls")
        val DUPLICATE_WINDOW_SECONDS: Preferences.Key<Int> = intPreferencesKey("duplicate_window_seconds")
        val DUPLICATE_FILTER_ENABLED: Preferences.Key<Boolean> = booleanPreferencesKey("duplicate_filter_enabled")
        val FILTER_ONGOING: Preferences.Key<Boolean> = booleanPreferencesKey("filter_ongoing")
        val CHAT_OPTIMIZATION: Preferences.Key<Boolean> = booleanPreferencesKey("chat_optimization")
        val HISTORY_LOGS_ENABLED: Preferences.Key<Boolean> = booleanPreferencesKey("history_logs_enabled")
        val HIDE_FROM_RECENTS: Preferences.Key<Boolean> = booleanPreferencesKey("hide_from_recents")
    }

    companion object {
        const val DEFAULT_SERVER_URL = "https://api.day.app/push"
    }
}

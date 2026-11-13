package dev.yakitori.barkforwarder.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.yakitori.barkforwarder.BarkBridgeApp
import dev.yakitori.barkforwarder.data.model.AppRule
import dev.yakitori.barkforwarder.data.model.BarkConfig
import dev.yakitori.barkforwarder.data.model.NotificationHistory
import dev.yakitori.barkforwarder.data.model.NotificationFilterConfig
import dev.yakitori.barkforwarder.data.model.NotificationRule
import dev.yakitori.barkforwarder.domain.AppPermissions
import dev.yakitori.barkforwarder.domain.BarkEndpointParser
import dev.yakitori.barkforwarder.domain.BarkPushPayload
import dev.yakitori.barkforwarder.domain.BarkSettingsValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val container = BarkBridgeApp.from(application).container
    private val selfPackageName = application.packageName
    private val _searchQuery = MutableStateFlow("")
    private val _historySection = MutableStateFlow(HistorySection.All)

    val barkConfig = container.settingsRepository.barkConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BarkConfig())
    val notificationFilterConfig = container.settingsRepository.notificationFilterConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotificationFilterConfig())
    val notificationHistory = container.notificationHistoryRepository.observeHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val notificationRules = container.notificationRuleRepository.observeRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val hideFromRecents = container.settingsRepository.hideFromRecents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val searchQuery: StateFlow<String> = _searchQuery
    val historySection: StateFlow<HistorySection> = _historySection

    private val _permissionSnapshot = MutableStateFlow(readPermissionSnapshot())
    val permissionSnapshot: StateFlow<PermissionSnapshot> = _permissionSnapshot
    private val _barkSettingsMessage = MutableStateFlow<String?>(null)
    val barkSettingsMessage: StateFlow<String?> = _barkSettingsMessage

    private val _testLogs = MutableStateFlow<List<String>>(emptyList())
    val testLogs: StateFlow<List<String>> = _testLogs
    private val logTimeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private val _iconCacheMessage = MutableStateFlow<String?>(null)
    val iconCacheMessage: StateFlow<String?> = _iconCacheMessage
    private val _iconBulkMessage = MutableStateFlow<String?>(null)
    val iconBulkMessage: StateFlow<String?> = _iconBulkMessage
    private val _iconBulkRefreshing = MutableStateFlow(false)
    val iconBulkRefreshing: StateFlow<Boolean> = _iconBulkRefreshing

    val filteredRules: StateFlow<List<AppRule>> = combine(
        container.installedAppRepository.observeRules(),
        _searchQuery,
    ) { rules, query ->
        val filtered = if (query.isBlank()) rules else rules.filter {
            it.appLabel.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true)
        }
        sortAppRulesForAppsScreen(filtered, selfPackageName)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun refreshInstalledApps() {
        viewModelScope.launch {
            container.installedAppRepository.refreshInstalledApps()
        }
    }

    fun refreshPermissionSnapshot() {
        _permissionSnapshot.value = readPermissionSnapshot()
    }

    fun saveBarkSettings(barkUrlOrKey: String) {
        viewModelScope.launch {
            runCatching {
                BarkSettingsValidator.validate(
                    barkUrlOrKey = barkUrlOrKey,
                )
            }.onSuccess { validated ->
                val current = barkConfig.value
                container.settingsRepository.updateBarkConfig(
                    current.copy(
                        serverUrl = validated.endpoint.serverUrl,
                        deviceKey = validated.endpoint.deviceKey,
                    ),
                )
                _barkSettingsMessage.value = "已保存 Bark 设置。"
            }.onFailure { error ->
                _barkSettingsMessage.value = error.message ?: "无法保存 Bark 设置。"
            }
        }
    }

    fun clearTestLogs() {
        _testLogs.value = emptyList()
    }

    fun sendTestNotification(barkUrlOrKey: String) {
        viewModelScope.launch {
            val endpoint = BarkEndpointParser.parse(barkUrlOrKey)
            if (endpoint == null) {
                appendTestLog("发送失败：Bark 链接或 Key 无效")
                return@launch
            }
            val payload = BarkPushPayload(
                title = "测试通知",
                body = "这是一条来自「通知转发到 Bark」的测试消息。",
                group = "test",
                level = "active",
                icon = TEST_ICON_URL,
            )
            appendTestLog("目标：${endpoint.serverUrl}")
            appendTestLog("设备 Key：${maskKey(endpoint.deviceKey)}")
            appendTestLog("图标：$TEST_ICON_URL（若通知仍无图标，说明 Bark 端版本不支持 icon）")
            appendTestLog("请求：POST ${endpoint.serverUrl}（title=${payload.title}）")
            val startedAt = System.currentTimeMillis()
            runCatching {
                container.barkPushClient.send(endpoint.serverUrl, endpoint.deviceKey, payload)
            }.onSuccess { result ->
                val elapsed = System.currentTimeMillis() - startedAt
                appendTestLog("响应：HTTP ${result.statusCode}（${elapsed}ms）")
                if (result.body.isNotBlank()) {
                    appendTestLog("返回内容：${result.body}")
                }
                appendTestLog("测试通知发送成功 ✅")
            }.onFailure { error ->
                val elapsed = System.currentTimeMillis() - startedAt
                appendTestLog("响应失败（${elapsed}ms）：${error.message ?: error::class.simpleName}")
                appendTestLog("测试通知发送失败 ❌")
            }
        }
    }

    private fun appendTestLog(message: String) {
        val timestamp = logTimeFormat.format(Date())
        _testLogs.value = (_testLogs.value + "$timestamp  $message").takeLast(MAX_TEST_LOG_LINES)
    }

    private fun maskKey(key: String): String =
        if (key.length <= 6) key else "${key.take(4)}****${key.takeLast(2)}"

    fun setForwardingEnabled(enabled: Boolean) = updateBarkConfig { copy(forwardingEnabled = enabled) }
    fun setNotificationsEnabled(enabled: Boolean) = updateBarkConfig { copy(notificationsEnabled = enabled) }
    fun setSmsEnabled(enabled: Boolean) = updateBarkConfig { copy(smsEnabled = enabled) }
    fun setCallsEnabled(enabled: Boolean) = updateBarkConfig { copy(callsEnabled = enabled) }
    fun setRingIncomingCalls(enabled: Boolean) = updateBarkConfig { copy(ringIncomingCalls = enabled) }

    fun setDuplicateFilterEnabled(enabled: Boolean) =
        updateFilterConfig { copy(duplicateFilterEnabled = enabled) }

    fun setDuplicateWindowSeconds(seconds: Int) =
        updateFilterConfig { copy(duplicateWindowSeconds = seconds.coerceIn(1, 120)) }

    fun setFilterOngoingNotifications(enabled: Boolean) =
        updateFilterConfig { copy(filterOngoingNotifications = enabled) }

    fun setChatOptimizationEnabled(enabled: Boolean) =
        updateFilterConfig { copy(chatOptimizationEnabled = enabled) }

    fun setHistoryLogsEnabled(enabled: Boolean) =
        updateFilterConfig { copy(historyLogsEnabled = enabled) }

    private fun updateFilterConfig(transform: NotificationFilterConfig.() -> NotificationFilterConfig) {
        viewModelScope.launch {
            container.settingsRepository.updateNotificationFilterConfig(notificationFilterConfig.value.transform())
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setHistorySection(section: HistorySection) {
        _historySection.value = section
    }

    fun clearHistory() {
        viewModelScope.launch {
            container.notificationHistoryRepository.clearHistory()
        }
    }

    fun setHideFromRecents(enabled: Boolean) {
        viewModelScope.launch {
            container.settingsRepository.setHideFromRecents(enabled)
        }
    }

    fun setAppExcluded(packageName: String, excluded: Boolean) {
        viewModelScope.launch {
            container.appRuleRepository.updateExcluded(packageName, excluded)
        }
    }

    fun updateManualIconUrl(packageName: String, manualUrl: String?) {
        viewModelScope.launch {
            container.appRuleRepository.updateManualIconUrl(packageName, manualUrl)
        }
    }

    fun refreshAppIcon(packageName: String) {
        viewModelScope.launch {
            if (!container.appRuleRepository.getRule(packageName)?.manualIconUrl.isNullOrBlank()) {
                _iconCacheMessage.value = "$packageName 正在使用手动图标 URL，无需刷新"
                return@launch
            }
            _iconCacheMessage.value = "正在刷新 $packageName 的图标…"
            val result = runCatching { container.appIconResolver.refresh(packageName) }
            _iconCacheMessage.value = result.fold(
                onSuccess = { url ->
                    if (url != null) {
                        "已刷新 $packageName 的图标：$url"
                    } else {
                        "未能获取 $packageName 的图标，可尝试手动填写 URL"
                    }
                },
                onFailure = { error ->
                    "刷新图标失败：${error.message ?: error::class.simpleName}"
                },
            )
        }
    }

    fun refreshAllAppIcons() {
        viewModelScope.launch {
            if (_iconBulkRefreshing.value) return@launch
            _iconBulkRefreshing.value = true
            try {
                val targets = container.appRuleRepository.getAllRules()
                    .filter { it.manualIconUrl.isNullOrBlank() }
                if (targets.isEmpty()) {
                    _iconBulkMessage.value = "当前没有可刷新的应用"
                    return@launch
                }
                var success = 0
                var failed = 0
                targets.forEachIndexed { index, rule ->
                    _iconBulkMessage.value = "正在刷新图标 ${index + 1}/${targets.size}…"
                    runCatching { container.appIconResolver.refresh(rule.packageName) }
                        .onSuccess { url -> if (url != null) success++ else failed++ }
                        .onFailure { failed++ }
                }
                _iconBulkMessage.value = buildString {
                    append("已刷新 $success 个图标")
                    if (failed > 0) append("，$failed 个未能获取")
                }
            } finally {
                _iconBulkRefreshing.value = false
            }
        }
    }

    fun createNotificationRule(
        history: NotificationHistory,
        appNamePattern: String,
        titlePattern: String,
        bodyPattern: String,
    ) {
        viewModelScope.launch {
            runCatching {
                container.notificationRuleRepository.saveRule(
                    packageName = history.packageName,
                    packageLabelAtCreation = history.sourceLabel,
                    appNamePattern = appNamePattern,
                    titlePattern = titlePattern,
                    bodyPattern = bodyPattern,
                )
            }
        }
    }

    fun updateNotificationRule(
        rule: NotificationRule,
        appNamePattern: String,
        titlePattern: String,
        bodyPattern: String,
    ) {
        viewModelScope.launch {
            runCatching {
                container.notificationRuleRepository.updateRule(
                    existing = rule,
                    appNamePattern = appNamePattern,
                    titlePattern = titlePattern,
                    bodyPattern = bodyPattern,
                )
            }
        }
    }

    fun deleteNotificationRule(ruleId: String) {
        viewModelScope.launch {
            container.notificationRuleRepository.deleteRule(ruleId)
        }
    }

    private fun updateBarkConfig(transform: BarkConfig.() -> BarkConfig) {
        viewModelScope.launch {
            container.settingsRepository.updateBarkConfig(barkConfig.value.transform())
        }
    }

    private fun readPermissionSnapshot(): PermissionSnapshot {
        val context = getApplication<Application>()
        return PermissionSnapshot(
            notificationListenerEnabled = AppPermissions.hasNotificationListenerAccess(context),
            smsPermissionsGranted = AppPermissions.hasSmsAccess(context),
            callPermissionsGranted = AppPermissions.hasCallAccess(context),
            postNotificationsGranted = AppPermissions.hasPostNotificationsPermission(context),
        )
    }

    data class PermissionSnapshot(
        val notificationListenerEnabled: Boolean,
        val smsPermissionsGranted: Boolean,
        val callPermissionsGranted: Boolean,
        val postNotificationsGranted: Boolean,
    )

    enum class HistorySection {
        All,
        Success,
        Failed,
        Filtered,
        BlockedRules,
    }

    private companion object {
        const val MAX_TEST_LOG_LINES = 200
        const val TEST_ICON_URL =
            "https://is1-ssl.mzstatic.com/image/thumb/PurpleSource211/v4/27/b3/7f/" +
                "27b37f52-a029-c5ad-8675-fa0cfb935801/Placeholder.mill/400x400bb-75.jpg"
    }
}

internal fun sortAppRulesForAppsScreen(
    rules: List<AppRule>,
    selfPackageName: String,
): List<AppRule> {
    return rules.sortedWith(
        compareBy<AppRule>(
            { if (it.excluded && it.packageName != selfPackageName) 0 else 1 },
            { it.appLabel.lowercase(Locale.ROOT) },
            { it.packageName.lowercase(Locale.ROOT) },
        ),
    )
}

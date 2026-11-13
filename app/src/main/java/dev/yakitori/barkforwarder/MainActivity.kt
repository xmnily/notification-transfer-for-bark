package dev.yakitori.barkforwarder

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yakitori.barkforwarder.data.model.AppRule
import dev.yakitori.barkforwarder.data.model.HistoryStatus
import dev.yakitori.barkforwarder.data.model.NotificationHistory
import dev.yakitori.barkforwarder.data.model.NotificationRule
import dev.yakitori.barkforwarder.domain.BarkEndpointParser
import dev.yakitori.barkforwarder.ui.MainViewModel
import dev.yakitori.barkforwarder.ui.theme.BarkForwarderTheme
import java.text.DateFormat

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BarkForwarderTheme {
                BarkForwarderApp(viewModel = viewModel)
            }
        }
    }
}

private enum class Destination(val label: String) {
    Setup("初始设置"),
    Bark("Bark"),
    Filters("转发"),
    Apps("应用"),
    History("历史"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BarkForwarderApp(viewModel: MainViewModel) {
    val barkConfig by viewModel.barkConfig.collectAsStateWithLifecycle()
    val filterConfig by viewModel.notificationFilterConfig.collectAsStateWithLifecycle()
    val appRules by viewModel.filteredRules.collectAsStateWithLifecycle()
    val notificationHistory by viewModel.notificationHistory.collectAsStateWithLifecycle()
    val notificationRules by viewModel.notificationRules.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val historySection by viewModel.historySection.collectAsStateWithLifecycle()
    val barkSettingsMessage by viewModel.barkSettingsMessage.collectAsStateWithLifecycle()
    val testLogs by viewModel.testLogs.collectAsStateWithLifecycle()
    val iconCacheMessage by viewModel.iconCacheMessage.collectAsStateWithLifecycle()
    val iconBulkMessage by viewModel.iconBulkMessage.collectAsStateWithLifecycle()
    val iconBulkRefreshing by viewModel.iconBulkRefreshing.collectAsStateWithLifecycle()
    val hideFromRecents by viewModel.hideFromRecents.collectAsStateWithLifecycle()
    var destination by rememberSaveable { mutableStateOf(Destination.Setup) }
    val context = LocalContext.current

    LaunchedEffect(hideFromRecents) {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        runCatching {
            activityManager?.appTasks?.forEach { it.setExcludeFromRecents(hideFromRecents) }
        }
    }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { }
    val callPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { }

    val permissionSnapshot by viewModel.permissionSnapshot.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.refreshInstalledApps()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = destination == Destination.Setup,
                    onClick = { destination = Destination.Setup },
                    icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                    label = { Text(Destination.Setup.label) },
                )
                NavigationBarItem(
                    selected = destination == Destination.Bark,
                    onClick = { destination = Destination.Bark },
                    icon = { Icon(Icons.Outlined.Security, contentDescription = null) },
                    label = { Text(Destination.Bark.label) },
                )
                NavigationBarItem(
                    selected = destination == Destination.Filters,
                    onClick = { destination = Destination.Filters },
                    icon = { Icon(Icons.Outlined.Notifications, contentDescription = null) },
                    label = { Text(Destination.Filters.label) },
                )
                NavigationBarItem(
                    selected = destination == Destination.Apps,
                    onClick = { destination = Destination.Apps },
                    icon = { Icon(Icons.Outlined.Apps, contentDescription = null) },
                    label = { Text(Destination.Apps.label) },
                )
                NavigationBarItem(
                    selected = destination == Destination.History,
                    onClick = { destination = Destination.History },
                    icon = { Icon(Icons.Outlined.History, contentDescription = null) },
                    label = { Text(Destination.History.label) },
                )
            }
        },
    ) { padding ->
        when (destination) {
            Destination.Setup -> SetupScreen(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                status = permissionSnapshot,
                onOpenNotificationListenerSettings = {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                },
                onRequestSmsPermissions = {
                    smsPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.RECEIVE_SMS,
                            Manifest.permission.READ_SMS,
                        ),
                    )
                },
                onRequestCallPermissions = {
                    callPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.READ_PHONE_STATE,
                            Manifest.permission.READ_CALL_LOG,
                        ),
                    )
                },
                onRequestNotificationPermission = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                onRefresh = viewModel::refreshPermissionSnapshot,
                forwardingEnabled = barkConfig.forwardingEnabled,
                onForwardingEnabledChanged = viewModel::setForwardingEnabled,
                hideFromRecents = hideFromRecents,
                onHideFromRecentsChanged = viewModel::setHideFromRecents,
            )

            Destination.Bark -> BarkSettingsScreen(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                barkUrlOrKey = BarkEndpointParser.toDisplayValue(barkConfig),
                statusMessage = barkSettingsMessage,
                testLogs = testLogs,
                onSave = { barkUrlOrKey ->
                    viewModel.saveBarkSettings(barkUrlOrKey)
                },
                onSendTest = viewModel::sendTestNotification,
                onClearLogs = viewModel::clearTestLogs,
            )

            Destination.Filters -> FilterSettingsScreen(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                barkConfig = barkConfig,
                duplicateFilterEnabled = filterConfig.duplicateFilterEnabled,
                duplicateWindowSeconds = filterConfig.duplicateWindowSeconds,
                filterOngoingNotifications = filterConfig.filterOngoingNotifications,
                chatOptimizationEnabled = filterConfig.chatOptimizationEnabled,
                onNotificationsEnabledChanged = viewModel::setNotificationsEnabled,
                onSmsEnabledChanged = viewModel::setSmsEnabled,
                onCallsEnabledChanged = viewModel::setCallsEnabled,
                onRingIncomingCallsChanged = viewModel::setRingIncomingCalls,
                onDuplicateFilterChanged = viewModel::setDuplicateFilterEnabled,
                onDuplicateWindowChanged = viewModel::setDuplicateWindowSeconds,
                onFilterOngoingChanged = viewModel::setFilterOngoingNotifications,
                onChatOptimizationChanged = viewModel::setChatOptimizationEnabled,
            )

            Destination.Apps -> AppRulesScreen(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                query = query,
                rules = appRules,
                iconCacheMessage = iconCacheMessage,
                iconBulkMessage = iconBulkMessage,
                iconBulkRefreshing = iconBulkRefreshing,
                onQueryChanged = viewModel::setSearchQuery,
                onRefresh = viewModel::refreshInstalledApps,
                onRefreshIcon = viewModel::refreshAppIcon,
                onRefreshAllIcons = viewModel::refreshAllAppIcons,
                onExcludedChanged = viewModel::setAppExcluded,
                onManualIconSave = viewModel::updateManualIconUrl,
            )

            Destination.History -> HistoryScreen(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                section = historySection,
                history = notificationHistory,
                rules = notificationRules,
                historyLogsEnabled = filterConfig.historyLogsEnabled,
                onSectionChanged = viewModel::setHistorySection,
                onHistoryLogsChanged = viewModel::setHistoryLogsEnabled,
                onClearHistory = viewModel::clearHistory,
                onCreateRule = viewModel::createNotificationRule,
                onUpdateRule = viewModel::updateNotificationRule,
                onDeleteRule = viewModel::deleteNotificationRule,
            )
        }
    }
}

@Composable
private fun SetupScreen(
    modifier: Modifier,
    status: MainViewModel.PermissionSnapshot,
    forwardingEnabled: Boolean,
    hideFromRecents: Boolean,
    onOpenNotificationListenerSettings: () -> Unit,
    onRequestSmsPermissions: () -> Unit,
    onRequestCallPermissions: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onForwardingEnabledChanged: (Boolean) -> Unit,
    onHideFromRecentsChanged: (Boolean) -> Unit,
    onRefresh: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("初始设置", style = MaterialTheme.typography.headlineSmall)
        Text(
            "开启通知访问权限后，通知转发效果最佳。在限制直接授权的设备上，短信和通话访问会回退为读取应用通知。",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("转发总开关", style = MaterialTheme.typography.titleMedium)
                    Switch(
                        checked = forwardingEnabled,
                        onCheckedChange = onForwardingEnabledChanged,
                    )
                }
                Text(
                    if (forwardingEnabled) {
                        "已开启：通知、短信与通话会按各页设置转发。"
                    } else {
                        "已关闭：所有事件都不会转发，也不会写入历史。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        StatusCard(
            title = "通知访问权限",
            enabled = status.notificationListenerEnabled,
            buttonLabel = "打开设置",
            onClick = onOpenNotificationListenerSettings,
        )
        StatusCard(
            title = "短信权限",
            enabled = status.smsPermissionsGranted,
            buttonLabel = "请求短信权限",
            onClick = onRequestSmsPermissions,
        )
        StatusCard(
            title = "通话权限",
            enabled = status.callPermissionsGranted,
            buttonLabel = "请求通话访问权限",
            onClick = onRequestCallPermissions,
        )
        StatusCard(
            title = "发送通知",
            enabled = status.postNotificationsGranted,
            buttonLabel = "请求权限",
            onClick = onRequestNotificationPermission,
        )
        OutlinedCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ToggleRow(
                    "在最近任务中隐藏本应用",
                    hideFromRecents,
                    onHideFromRecentsChanged,
                )
                Text(
                    "开启后会从最近任务列表移除本应用，通知转发不受影响；再次打开请从桌面图标进入。",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        OutlinedButton(onClick = onRefresh) {
            Text("刷新状态")
        }
        OutlinedCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("开源许可", style = MaterialTheme.typography.titleMedium)
                Text(
                    "本应用为开源项目，基于 MIT 许可证分发。完整许可证文本见代码仓库。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(
                    onClick = {
                        uriHandler.openUri("https://github.com/notisfer-dev/notification-transfer-for-bark")
                    },
                ) {
                    Text("查看代码仓库")
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    title: String,
    enabled: Boolean,
    buttonLabel: String,
    onClick: () -> Unit,
) {
    OutlinedCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(if (enabled) "已开启" else "未开启")
            Button(onClick = onClick) {
                Text(buttonLabel)
            }
        }
    }
}

@Composable
private fun BarkSettingsScreen(
    modifier: Modifier,
    barkUrlOrKey: String,
    statusMessage: String?,
    testLogs: List<String>,
    onSave: (String) -> Unit,
    onSendTest: (String) -> Unit,
    onClearLogs: () -> Unit,
) {
    var barkEndpoint by rememberSaveable(barkUrlOrKey) { mutableStateOf(barkUrlOrKey) }
    val logScrollState = rememberScrollState()
    LaunchedEffect(testLogs.size) {
        logScrollState.scrollTo(logScrollState.maxValue)
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Bark 设置", style = MaterialTheme.typography.headlineSmall)
        Text("粘贴 `https://api.day.app/<你的 Key>`，或直接填入设备 Key。推送将以明文方式发送。")
        OutlinedCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("感谢 Bark", style = MaterialTheme.typography.titleMedium)
                Text(
                    "特别感谢 Bark 及其开发者 Finb 打造并分享了这款 iOS 应用，让本工作流成为可能。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = barkEndpoint,
            onValueChange = { barkEndpoint = it },
            label = { Text("Bark 链接或 Key") },
            supportingText = {
                Text("示例：https://api.day.app/XXXXXXXXXXXXXXXXXX，或直接填设备 Key")
            },
            singleLine = true,
        )
        statusMessage?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onSave(barkEndpoint) }) {
                Text("保存 Bark 设置")
            }
            OutlinedButton(onClick = { onSendTest(barkEndpoint.trim()) }) {
                Text("发送测试通知")
            }
        }
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("测试日志", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onClearLogs, enabled = testLogs.isNotEmpty()) {
                        Text("清空")
                    }
                }
                if (testLogs.isEmpty()) {
                    Text(
                        "点击“发送测试通知”后，可在此查看请求与响应日志。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .verticalScroll(logScrollState),
                    ) {
                        testLogs.forEach { line ->
                            Text(
                                line,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterSettingsScreen(
    modifier: Modifier,
    barkConfig: dev.yakitori.barkforwarder.data.model.BarkConfig,
    duplicateFilterEnabled: Boolean,
    duplicateWindowSeconds: Int,
    filterOngoingNotifications: Boolean,
    chatOptimizationEnabled: Boolean,
    onNotificationsEnabledChanged: (Boolean) -> Unit,
    onSmsEnabledChanged: (Boolean) -> Unit,
    onCallsEnabledChanged: (Boolean) -> Unit,
    onRingIncomingCallsChanged: (Boolean) -> Unit,
    onDuplicateFilterChanged: (Boolean) -> Unit,
    onDuplicateWindowChanged: (Int) -> Unit,
    onFilterOngoingChanged: (Boolean) -> Unit,
    onChatOptimizationChanged: (Boolean) -> Unit,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("转发设置", style = MaterialTheme.typography.headlineSmall)
        ToggleRow("转发应用通知", barkConfig.notificationsEnabled, onNotificationsEnabledChanged)
        ToggleRow("转发短信", barkConfig.smsEnabled, onSmsEnabledChanged)
        ToggleRow("转发通话", barkConfig.callsEnabled, onCallsEnabledChanged)
        ToggleRow("来电时使用 Bark call=1", barkConfig.ringIncomingCalls, onRingIncomingCallsChanged)
        ToggleRow(
            "过滤持续性通知（常驻通知 / 前台服务）",
            filterOngoingNotifications,
            onFilterOngoingChanged,
        )
        ToggleRow(
            "聊天通知优化（微信 / QQ 标题与分组）",
            chatOptimizationEnabled,
            onChatOptimizationChanged,
        )
        Card {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("过滤重复通知")
                        Text(
                            if (duplicateFilterEnabled) "相同内容在超时时间内只转发一次" else "已关闭，所有通知都会转发",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(checked = duplicateFilterEnabled, onCheckedChange = onDuplicateFilterChanged)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("重复通知超时")
                Text("$duplicateWindowSeconds 秒")
                Slider(
                    value = duplicateWindowSeconds.toFloat(),
                    onValueChange = { onDuplicateWindowChanged(it.toInt()) },
                    valueRange = 1f..120f,
                    enabled = duplicateFilterEnabled,
                )
                Text("仅对通知事件生效。内容相同的通知在比较前会先做归一化处理，因此像 LINE 这类会合并通知的应用不太可能重复转发。")
            }
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    OutlinedCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, modifier = Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun AppRulesScreen(
    modifier: Modifier,
    query: String,
    rules: List<AppRule>,
    iconCacheMessage: String?,
    iconBulkMessage: String?,
    iconBulkRefreshing: Boolean,
    onQueryChanged: (String) -> Unit,
    onRefresh: () -> Unit,
    onRefreshIcon: (String) -> Unit,
    onRefreshAllIcons: () -> Unit,
    onExcludedChanged: (String, Boolean) -> Unit,
    onManualIconSave: (String, String?) -> Unit,
) {
    Column(modifier = modifier.padding(16.dp)) {
        Text("排除的应用", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Bark 图标必须是公开可访问的 URL。解析顺序：手动 URL → 本地缓存 → App Store 公开图标 → Play 商店；" +
                "首次收到某应用的通知会先在后台解析，从下一条通知开始显示图标。商店里找不到的应用可在编辑区手动填写图标 URL。" +
                "「一键刷新所有应用图标」会重新解析所有未手动填写图标 URL 的应用（无论是否已有缓存）。",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = query,
            onValueChange = onQueryChanged,
            label = { Text("搜索已安装的应用") },
            singleLine = true,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onRefresh) {
                Text("刷新已安装应用")
            }
            OutlinedButton(onClick = onRefreshAllIcons, enabled = !iconBulkRefreshing) {
                Text("一键刷新所有应用图标")
            }
        }
        iconBulkMessage?.takeIf { it.isNotBlank() }?.let { message ->
            Spacer(modifier = Modifier.height(6.dp))
            Text(message, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(modifier = Modifier.height(12.dp))
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(rules, key = { it.packageName }) { rule ->
                AppRuleRow(
                    rule = rule,
                    iconCacheMessage = iconCacheMessage,
                    onExcludedChanged = onExcludedChanged,
                    onManualIconSave = onManualIconSave,
                    onRefreshIcon = onRefreshIcon,
                )
            }
        }
    }
}

@Composable
private fun AppRuleRow(
    rule: AppRule,
    iconCacheMessage: String?,
    onExcludedChanged: (String, Boolean) -> Unit,
    onManualIconSave: (String, String?) -> Unit,
    onRefreshIcon: (String) -> Unit,
) {
    var expanded by rememberSaveable(rule.packageName) { mutableStateOf(false) }
    var manualUrl by rememberSaveable(rule.packageName, rule.manualIconUrl) { mutableStateOf(rule.manualIconUrl.orEmpty()) }

    OutlinedCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = rule.excluded,
                    onCheckedChange = { onExcludedChanged(rule.packageName, it) },
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(rule.appLabel, style = MaterialTheme.typography.titleMedium)
                    Text(rule.packageName, style = MaterialTheme.typography.bodySmall)
                    Text(
                        when {
                            !rule.manualIconUrl.isNullOrBlank() -> "已设置手动图标 URL"
                            !rule.cachedResolvedIconUrl.isNullOrBlank() -> "已缓存图标"
                            else -> "尚未缓存图标"
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    val iconUrlInUse = rule.manualIconUrl?.takeIf { it.isNotBlank() }
                        ?: rule.cachedResolvedIconUrl?.takeIf { it.isNotBlank() }
                    iconUrlInUse?.let { iconUrl ->
                        SelectionContainer {
                            Text(
                                iconUrl,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            )
                        }
                    }
                }
                Text(if (expanded) "收起" else "编辑")
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = manualUrl,
                    onValueChange = { manualUrl = it },
                    label = { Text("手动图标 URL") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onManualIconSave(rule.packageName, manualUrl) }) {
                        Text("保存图标 URL")
                    }
                    OutlinedButton(onClick = {
                        manualUrl = ""
                        onManualIconSave(rule.packageName, null)
                    }) {
                        Text("清除")
                    }
                    OutlinedButton(onClick = { onRefreshIcon(rule.packageName) }) {
                        Text("刷新图标")
                    }
                }
                val rowMessage = iconCacheMessage?.takeIf { it.contains(rule.packageName) }
                if (!rowMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(rowMessage, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun HistoryScreen(
    modifier: Modifier,
    section: MainViewModel.HistorySection,
    history: List<NotificationHistory>,
    rules: List<NotificationRule>,
    historyLogsEnabled: Boolean,
    onSectionChanged: (MainViewModel.HistorySection) -> Unit,
    onHistoryLogsChanged: (Boolean) -> Unit,
    onClearHistory: () -> Unit,
    onCreateRule: (NotificationHistory, String, String, String) -> Unit,
    onUpdateRule: (NotificationRule, String, String, String) -> Unit,
    onDeleteRule: (String) -> Unit,
) {
    var editorState by androidx.compose.runtime.remember { mutableStateOf<RuleEditorState?>(null) }
    var showClearConfirm by androidx.compose.runtime.remember { mutableStateOf(false) }

    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("历史", style = MaterialTheme.typography.headlineSmall)
            TextButton(
                onClick = { showClearConfirm = true },
                enabled = history.isNotEmpty(),
            ) {
                Text("清除历史")
            }
        }
        if (showClearConfirm) {
            AlertDialog(
                onDismissRequest = { showClearConfirm = false },
                title = { Text("清除全部历史？") },
                text = { Text("将删除所有历史记录及其处理日志，此操作无法撤销。") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showClearConfirm = false
                            onClearHistory()
                        },
                    ) {
                        Text("清除")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearConfirm = false }) {
                        Text("取消")
                    }
                },
            )
        }
        ToggleRow("记录处理日志", historyLogsEnabled, onHistoryLogsChanged)
        Text(
            "历史按成功、失败、被过滤分类（被过滤包括持续性通知、排除应用、拦截规则与重复通知）。" +
                "在任意记录上点按，可创建规则来拦截同一应用的类似通知。",
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HistorySectionButton(
                label = "全部历史",
                selected = section == MainViewModel.HistorySection.All,
                onClick = { onSectionChanged(MainViewModel.HistorySection.All) },
            )
            HistorySectionButton(
                label = "成功",
                selected = section == MainViewModel.HistorySection.Success,
                onClick = { onSectionChanged(MainViewModel.HistorySection.Success) },
            )
            HistorySectionButton(
                label = "失败",
                selected = section == MainViewModel.HistorySection.Failed,
                onClick = { onSectionChanged(MainViewModel.HistorySection.Failed) },
            )
            HistorySectionButton(
                label = "被过滤",
                selected = section == MainViewModel.HistorySection.Filtered,
                onClick = { onSectionChanged(MainViewModel.HistorySection.Filtered) },
            )
            HistorySectionButton(
                label = "拦截规则",
                selected = section == MainViewModel.HistorySection.BlockedRules,
                onClick = { onSectionChanged(MainViewModel.HistorySection.BlockedRules) },
            )
        }

        when (section) {
            MainViewModel.HistorySection.BlockedRules -> {
                if (rules.isEmpty()) {
                    EmptyStateCard(
                        title = "暂无拦截规则",
                        body = "在“全部历史”中打开一条记录，即可创建规则来拦截来自同一应用的类似通知。",
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(rules, key = { it.id }) { rule ->
                            NotificationRuleRow(
                                rule = rule,
                                onEdit = { editorState = RuleEditorState.fromRule(rule) },
                                onDelete = { onDeleteRule(rule.id) },
                            )
                        }
                    }
                }
            }

            else -> {
                val visibleHistory = when (section) {
                    MainViewModel.HistorySection.All -> history
                    MainViewModel.HistorySection.Success -> history.filter { it.status == HistoryStatus.SUCCESS }
                    MainViewModel.HistorySection.Failed -> history.filter { it.status == HistoryStatus.FAILED }
                    MainViewModel.HistorySection.Filtered -> history.filter { it.status == HistoryStatus.FILTERED }
                    MainViewModel.HistorySection.BlockedRules -> emptyList()
                }
                if (visibleHistory.isEmpty()) {
                    EmptyStateCard(
                        title = historyEmptyTitle(section),
                        body = historyEmptyBody(section),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(visibleHistory, key = { it.id }) { item ->
                            NotificationHistoryRow(
                                item = item,
                                onClick = { editorState = RuleEditorState.fromHistory(item) },
                            )
                        }
                    }
                }
            }
        }
    }

    editorState?.let { state ->
        NotificationRuleEditorDialog(
            state = state,
            onDismiss = { editorState = null },
            onSave = { appNamePattern, titlePattern, bodyPattern ->
                state.existingRule?.let { existing ->
                    onUpdateRule(existing, appNamePattern, titlePattern, bodyPattern)
                } ?: state.historyItem?.let { historyItem ->
                    onCreateRule(historyItem, appNamePattern, titlePattern, bodyPattern)
                }
                editorState = null
            },
        )
    }
}

private fun historyEmptyTitle(section: MainViewModel.HistorySection): String = when (section) {
    MainViewModel.HistorySection.All -> "暂无历史记录"
    MainViewModel.HistorySection.Success -> "暂无成功记录"
    MainViewModel.HistorySection.Failed -> "暂无失败记录"
    MainViewModel.HistorySection.Filtered -> "暂无被过滤的记录"
    MainViewModel.HistorySection.BlockedRules -> "暂无拦截规则"
}

private fun historyEmptyBody(section: MainViewModel.HistorySection): String = when (section) {
    MainViewModel.HistorySection.All -> "应用通知的处理结果会显示在这里。"
    MainViewModel.HistorySection.Success -> "成功发送到 Bark 的通知会显示在这里。"
    MainViewModel.HistorySection.Failed -> "请求 Bark 失败的通知会显示在这里，可展开日志查看原因。"
    MainViewModel.HistorySection.Filtered -> "持续性通知、排除的应用、命中拦截规则与重复通知都会显示在这里。"
    MainViewModel.HistorySection.BlockedRules -> "在“全部历史”中打开一条记录，即可创建规则来拦截来自同一应用的类似通知。"
}

@Composable
private fun HistorySectionButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        Button(modifier = modifier, onClick = onClick) {
            Text(label)
        }
    } else {
        OutlinedButton(modifier = modifier, onClick = onClick) {
            Text(label)
        }
    }
}

@Composable
private fun EmptyStateCard(
    title: String,
    body: String,
) {
    OutlinedCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun NotificationHistoryRow(
    item: NotificationHistory,
    onClick: () -> Unit,
) {
    var showLog by rememberSaveable(item.id) { mutableStateOf(false) }

    OutlinedCard(
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(item.sourceLabel, style = MaterialTheme.typography.titleMedium)
                Text(formatTimestamp(item.forwardedAt), style = MaterialTheme.typography.bodySmall)
            }
            Text(
                buildHistoryPreview(item),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    item.status.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = when (item.status) {
                        HistoryStatus.SUCCESS -> MaterialTheme.colorScheme.primary
                        HistoryStatus.FAILED -> MaterialTheme.colorScheme.error
                        HistoryStatus.FILTERED -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                item.filterReason.takeIf { it.isNotBlank() }?.let { reason ->
                    Text(
                        "· $reason",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text("点按可拦截类似通知", style = MaterialTheme.typography.bodySmall)
            if (item.log.isNotBlank()) {
                Text(
                    if (showLog) "收起日志" else "查看日志",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { showLog = !showLog },
                )
                if (showLog) {
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            item.log,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(10.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationRuleRow(
    rule: NotificationRule,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier.clickable(onClick = onEdit),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(rule.packageLabelAtCreation, style = MaterialTheme.typography.titleMedium)
            Text(rule.packageName, style = MaterialTheme.typography.bodySmall)
            Text(rule.summary(), style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onEdit) {
                    Text("编辑")
                }
                OutlinedButton(onClick = onDelete) {
                    Text("删除")
                }
            }
        }
    }
}

@Composable
private fun NotificationRuleEditorDialog(
    state: RuleEditorState,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
) {
    var appNamePattern by rememberSaveable(state.dialogKey, state.initialAppNamePattern) {
        mutableStateOf(state.initialAppNamePattern)
    }
    var titlePattern by rememberSaveable(state.dialogKey, state.initialTitlePattern) {
        mutableStateOf(state.initialTitlePattern)
    }
    var bodyPattern by rememberSaveable(state.dialogKey, state.initialBodyPattern) {
        mutableStateOf(state.initialBodyPattern)
    }
    val canSave = titlePattern.isNotBlank() || bodyPattern.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(state.dialogTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("Bark 预览", style = MaterialTheme.typography.labelLarge)
                        Text(state.renderedTitle, style = MaterialTheme.typography.titleMedium)
                        Text(state.renderedBody, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text(
                    "只有来自同一应用包的通知才会被拦截。下面每个非空字段都必须包含在后续通知中，因此保存前可以删掉会变化的部分。",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "包名：${state.packageName}",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = appNamePattern,
                    onValueChange = { appNamePattern = it },
                    label = { Text("应用名匹配") },
                    singleLine = true,
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = titlePattern,
                    onValueChange = { titlePattern = it },
                    label = { Text("标题匹配") },
                    singleLine = false,
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = bodyPattern,
                    onValueChange = { bodyPattern = it },
                    label = { Text("正文匹配") },
                    singleLine = false,
                )
                if (!canSave) {
                    Text(
                        "标题或正文至少填写一项。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = canSave,
                onClick = { onSave(appNamePattern, titlePattern, bodyPattern) },
            ) {
                Text(if (state.existingRule == null) "保存规则" else "更新规则")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

private fun buildHistoryPreview(item: NotificationHistory): String {
    return listOf(item.title, item.body)
        .filter { it.isNotBlank() }
        .joinToString(" - ")
        .ifBlank { item.renderedBody.ifBlank { "收到一条通知" } }
}

private fun formatTimestamp(timestamp: Long): String {
    return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(timestamp)
}

private fun NotificationRule.summary(): String {
    return buildList {
        appNamePattern?.takeIf { it.isNotBlank() }?.let { add("应用名包含 \"$it\"") }
        titlePattern?.takeIf { it.isNotBlank() }?.let { add("标题包含 \"$it\"") }
        bodyPattern?.takeIf { it.isNotBlank() }?.let { add("正文包含 \"$it\"") }
    }.joinToString(" / ")
}

private data class RuleEditorState(
    val dialogKey: String,
    val dialogTitle: String,
    val packageName: String,
    val renderedTitle: String,
    val renderedBody: String,
    val initialAppNamePattern: String,
    val initialTitlePattern: String,
    val initialBodyPattern: String,
    val historyItem: NotificationHistory? = null,
    val existingRule: NotificationRule? = null,
) {
    companion object {
        fun fromHistory(item: NotificationHistory): RuleEditorState {
            return RuleEditorState(
                dialogKey = "history-${item.id}",
                dialogTitle = "拦截类似通知",
                packageName = item.packageName,
                renderedTitle = item.renderedTitle,
                renderedBody = item.renderedBody,
                initialAppNamePattern = item.sourceLabel,
                initialTitlePattern = item.title,
                initialBodyPattern = item.body,
                historyItem = item,
            )
        }

        fun fromRule(rule: NotificationRule): RuleEditorState {
            val fallbackPreview = rule.summary().ifBlank { "${rule.packageLabelAtCreation} 的拦截规则" }
            return RuleEditorState(
                dialogKey = "rule-${rule.id}",
                dialogTitle = "编辑拦截规则",
                packageName = rule.packageName,
                renderedTitle = rule.packageLabelAtCreation,
                renderedBody = fallbackPreview,
                initialAppNamePattern = rule.appNamePattern.orEmpty(),
                initialTitlePattern = rule.titlePattern.orEmpty(),
                initialBodyPattern = rule.bodyPattern.orEmpty(),
                existingRule = rule,
            )
        }
    }
}

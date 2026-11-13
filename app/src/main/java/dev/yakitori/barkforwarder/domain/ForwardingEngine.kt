package dev.yakitori.barkforwarder.domain

import dev.yakitori.barkforwarder.data.model.BarkConfig
import dev.yakitori.barkforwarder.data.model.CaptureSource
import dev.yakitori.barkforwarder.data.model.EventType
import dev.yakitori.barkforwarder.data.model.ForwardEvent
import dev.yakitori.barkforwarder.data.model.HistoryStatus
import dev.yakitori.barkforwarder.data.model.NotificationFilterConfig
import dev.yakitori.barkforwarder.data.prefs.SettingsRepository
import dev.yakitori.barkforwarder.data.repo.AppRuleRepository
import dev.yakitori.barkforwarder.data.repo.DedupeRepository
import dev.yakitori.barkforwarder.data.repo.NotificationHistoryRepository
import dev.yakitori.barkforwarder.data.repo.NotificationRuleRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class ForwardingEngine(
    private val settingsRepository: SettingsRepository,
    private val appRuleRepository: AppRuleRepository,
    private val notificationRuleRepository: NotificationRuleRepository,
    private val notificationHistoryRepository: NotificationHistoryRepository,
    private val dedupeRepository: DedupeRepository,
    private val appIconResolver: AppIconResolver,
    private val barkPushClient: BarkPushClient,
    private val backgroundScope: CoroutineScope,
) {
    private val iconRefreshesInFlight = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    suspend fun process(event: ForwardEvent) {
        val barkConfig = settingsRepository.barkConfig.first()
        if (!barkConfig.forwardingEnabled) return
        val filterConfig = settingsRepository.notificationFilterConfig.first()
        val logLines = if (filterConfig.historyLogsEnabled && event.type == EventType.NOTIFICATION) {
            mutableListOf<String>()
        } else {
            null
        }

        fun log(message: String) {
            logLines?.add("${LOG_TIME_FORMAT.format(Instant.now())}  $message")
        }

        suspend fun record(
            status: HistoryStatus,
            filterReason: String = "",
            renderedTitle: String = event.title.ifBlank { event.sourceLabel },
            renderedBody: String = event.body,
            forwardedAt: Long = System.currentTimeMillis(),
        ) {
            notificationHistoryRepository.recordNotification(
                event = event,
                status = status,
                renderedTitle = renderedTitle,
                renderedBody = renderedBody,
                forwardedAt = forwardedAt,
                log = logLines?.joinToString("\n").orEmpty(),
                filterReason = filterReason,
            )
        }

        suspend fun stop(reason: String) {
            log("已过滤：$reason")
            record(HistoryStatus.FILTERED, filterReason = reason)
        }

        log("收到通知 · ${eventTypeLabel(event.type)} · ${event.sourceLabel}（${event.packageName ?: "未知应用"}）")

        if (!shouldHandleEvent(event, barkConfig)) {
            stop(disabledReason(event.type))
            return
        }
        if (barkConfig.deviceKey.isBlank()) {
            stop("未配置 Bark Device Key")
            return
        }
        if (event.type == EventType.NOTIFICATION &&
            event.isOngoing &&
            filterConfig.filterOngoingNotifications
        ) {
            stop("持续性通知")
            return
        }

        val packageName = event.packageName
        if (!packageName.isNullOrBlank()) {
            val rule = appRuleRepository.getRule(packageName)
            if (rule?.excluded == true) {
                stop("应用已在排除列表")
                return
            }
            if (rule == null) appRuleRepository.ensureRule(packageName, event.sourceLabel)
        }

        if (event.type == EventType.NOTIFICATION) {
            val matchingRule = notificationRuleRepository.findMatchingRule(event)
            if (matchingRule != null) {
                stop("命中应用的拦截规则")
                return
            }
        }

        val dedupeWindow = dedupeWindowSeconds(event, filterConfig)
        if (event.type == EventType.NOTIFICATION) {
            log(
                if (dedupeWindow <= 0) {
                    "重复过滤：关闭"
                } else {
                    "重复过滤：开启（$dedupeWindow 秒）"
                },
            )
        }
        if (!dedupeRepository.shouldForward(
                dedupeKey = event.dedupeKey,
                relatedKeys = event.dedupeAliases,
                eventType = event.type,
                windowSeconds = dedupeWindow,
                nowMillis = event.postedAt,
            )
        ) {
            stop("重复通知（${dedupeWindow} 秒内）")
            return
        }

        val iconUrl: String?
        val iconNote: String
        when {
            !event.iconUrl.isNullOrBlank() -> {
                iconUrl = event.iconUrl
                iconNote = event.iconUrl
            }

            packageName.isNullOrBlank() -> {
                iconUrl = null
                iconNote = "无"
            }

            else -> {
                // 推送路径只用已有缓存，避免为了图标多等一次网络请求。
                val cached = appIconResolver.resolveCached(packageName)
                if (cached == null && iconRefreshesInFlight.add(packageName)) {
                    backgroundScope.launch {
                        try {
                            runCatching { appIconResolver.resolve(packageName) }
                        } finally {
                            iconRefreshesInFlight.remove(packageName)
                        }
                    }
                }
                iconUrl = cached
                iconNote = cached ?: "无（缓存缺失，已在后台解析）"
            }
        }

        val payload = createPayload(event, iconUrl, barkConfig, filterConfig)
        if (filterConfig.chatOptimizationEnabled &&
            ChatNotificationOptimizer.isChatPackage(packageName)
        ) {
            log("聊天优化：已应用（分组=${payload.group}）")
        }
        log("图标：$iconNote")
        log("请求：POST ${barkConfig.serverUrl}")
        log("请求明文：${barkPushClient.formBody(barkConfig.deviceKey, payload)}")

        val sendStartedAt = System.currentTimeMillis()
        val result = try {
            barkPushClient.send(
                serverUrl = barkConfig.serverUrl,
                deviceKey = barkConfig.deviceKey,
                payload = payload,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val failedAt = System.currentTimeMillis()
            val reason = error.message ?: error::class.simpleName ?: error.javaClass.name
            log("请求失败：$reason")
            record(
                status = HistoryStatus.FAILED,
                filterReason = reason,
                renderedTitle = payload.title,
                renderedBody = payload.body,
                forwardedAt = failedAt,
            )
            throw error
        }
        val finishedAt = System.currentTimeMillis()
        log(
            "响应：HTTP ${result.statusCode} · 请求 ${finishedAt - sendStartedAt}ms · " +
                "端到端 ${(finishedAt - event.postedAt).coerceAtLeast(0)}ms",
        )

        record(
            status = HistoryStatus.SUCCESS,
            renderedTitle = payload.title,
            renderedBody = payload.body,
            forwardedAt = finishedAt,
        )
    }

    private fun disabledReason(type: EventType): String = when (type) {
        EventType.NOTIFICATION -> "通知转发开关已关闭"
        EventType.SMS -> "短信转发开关已关闭"
        EventType.INCOMING_CALL,
        EventType.MISSED_CALL,
        -> "通话转发开关已关闭"
    }

    private fun eventTypeLabel(type: EventType): String = when (type) {
        EventType.NOTIFICATION -> "应用通知"
        EventType.SMS -> "短信"
        EventType.INCOMING_CALL -> "来电"
        EventType.MISSED_CALL -> "未接来电"
    }

    private fun shouldHandleEvent(event: ForwardEvent, barkConfig: BarkConfig): Boolean {
        return when (event.type) {
            EventType.NOTIFICATION -> barkConfig.notificationsEnabled
            EventType.SMS -> barkConfig.smsEnabled
            EventType.INCOMING_CALL,
            EventType.MISSED_CALL,
            -> barkConfig.callsEnabled
        }
    }

    private fun dedupeWindowSeconds(event: ForwardEvent, filterConfig: NotificationFilterConfig): Int {
        return when {
            event.type == EventType.NOTIFICATION ->
                if (!filterConfig.duplicateFilterEnabled) 0 else filterConfig.duplicateWindowSeconds
            event.captureSource == CaptureSource.NOTIFICATION_FALLBACK -> FALLBACK_DEDUPE_SECONDS
            else -> 0
        }
    }

    private fun createPayload(
        event: ForwardEvent,
        iconUrl: String?,
        barkConfig: BarkConfig,
        filterConfig: NotificationFilterConfig,
    ): BarkPushPayload {
        val chatRendering = if (filterConfig.chatOptimizationEnabled) {
            ChatNotificationOptimizer.optimize(event, event.sourceLabel)
        } else {
            null
        }

        val title: String
        val body: String
        val group: String
        if (chatRendering != null) {
            title = chatRendering.title
            body = chatRendering.body
            group = chatRendering.group
        } else {
            when (event.type) {
                EventType.NOTIFICATION -> {
                    val lines = listOfNotNull(
                        event.title.takeIf { it.isNotBlank() && it != event.sourceLabel },
                        event.body.takeIf { it.isNotBlank() },
                    )
                    title = event.sourceLabel
                    body = lines.joinToString("\n").ifBlank { "收到一条通知" }
                    group = event.packageName ?: "notifications"
                }

                EventType.SMS -> {
                    val sender = event.sender ?: event.phoneNumber ?: "未知发件人"
                    title = "短信：$sender"
                    body = event.body.ifBlank { event.title.ifBlank { "收到一条新短信" } }
                    group = "sms"
                }

                EventType.INCOMING_CALL -> {
                    title = "来电"
                    body = event.sender ?: event.phoneNumber ?: "未知来电"
                    group = "calls"
                }

                EventType.MISSED_CALL -> {
                    title = "未接来电"
                    body = event.sender ?: event.phoneNumber ?: "未知来电"
                    group = "calls"
                }
            }
        }

        val level = if (event.type == EventType.INCOMING_CALL) "timeSensitive" else "active"
        val call = if (event.type == EventType.INCOMING_CALL && barkConfig.ringIncomingCalls && event.shouldRing) 1 else null

        return BarkPushPayload(
            title = title,
            body = body,
            group = group,
            level = level,
            call = call,
            icon = iconUrl,
            image = event.imageUrl,
        )
    }

    private companion object {
        const val FALLBACK_DEDUPE_SECONDS = 10
        val LOG_TIME_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault())
    }
}

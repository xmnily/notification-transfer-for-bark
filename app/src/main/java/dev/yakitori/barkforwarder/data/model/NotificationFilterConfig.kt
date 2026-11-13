package dev.yakitori.barkforwarder.data.model

data class NotificationFilterConfig(
    val duplicateFilterEnabled: Boolean = true,
    val duplicateWindowSeconds: Int = 5,
    val filterOngoingNotifications: Boolean = true,
    val chatOptimizationEnabled: Boolean = true,
    val historyLogsEnabled: Boolean = true,
)

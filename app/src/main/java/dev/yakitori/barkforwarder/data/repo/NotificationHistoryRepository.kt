package dev.yakitori.barkforwarder.data.repo

import dev.yakitori.barkforwarder.data.db.NotificationHistoryDao
import dev.yakitori.barkforwarder.data.db.NotificationHistoryEntity
import dev.yakitori.barkforwarder.data.model.EventType
import dev.yakitori.barkforwarder.data.model.ForwardEvent
import dev.yakitori.barkforwarder.data.model.HistoryStatus
import dev.yakitori.barkforwarder.data.model.NotificationHistory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class NotificationHistoryRepository(
    private val dao: NotificationHistoryDao,
) {
    fun observeHistory(): Flow<List<NotificationHistory>> {
        return dao.observeAll().map { entities -> entities.map { it.toModel() } }
    }

    suspend fun recordNotification(
        event: ForwardEvent,
        status: HistoryStatus,
        renderedTitle: String,
        renderedBody: String,
        forwardedAt: Long = System.currentTimeMillis(),
        log: String = "",
        filterReason: String = "",
    ) {
        val packageName = event.packageName ?: return
        if (event.type != EventType.NOTIFICATION) return

        dao.insert(
            NotificationHistoryEntity(
                id = UUID.randomUUID().toString(),
                packageName = packageName,
                sourceLabel = event.sourceLabel,
                title = event.title,
                body = event.body,
                renderedTitle = renderedTitle,
                renderedBody = renderedBody,
                postedAt = event.postedAt,
                forwardedAt = forwardedAt,
                log = log,
                status = status.name,
                filterReason = filterReason,
            ),
        )
    }

    suspend fun clearHistory() {
        dao.deleteAll()
    }
}

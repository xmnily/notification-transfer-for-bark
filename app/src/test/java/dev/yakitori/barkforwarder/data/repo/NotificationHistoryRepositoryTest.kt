package dev.yakitori.barkforwarder.data.repo

import com.google.common.truth.Truth.assertThat
import dev.yakitori.barkforwarder.data.db.NotificationHistoryDao
import dev.yakitori.barkforwarder.data.db.NotificationHistoryEntity
import dev.yakitori.barkforwarder.data.model.CaptureSource
import dev.yakitori.barkforwarder.data.model.EventType
import dev.yakitori.barkforwarder.data.model.ForwardEvent
import dev.yakitori.barkforwarder.data.model.HistoryStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class NotificationHistoryRepositoryTest {
    @Test
    fun `records only notification events`() = runTest {
        val dao = FakeNotificationHistoryDao()
        val repository = NotificationHistoryRepository(dao)

        repository.recordNotification(
            event = forwardEvent(type = EventType.NOTIFICATION, packageName = "jp.naver.line.android"),
            status = HistoryStatus.SUCCESS,
            renderedTitle = "LINE",
            renderedBody = "Battery low",
            forwardedAt = 2_000L,
        )
        repository.recordNotification(
            event = forwardEvent(type = EventType.SMS, packageName = "jp.naver.line.android"),
            status = HistoryStatus.SUCCESS,
            renderedTitle = "SMS",
            renderedBody = "Ignored",
            forwardedAt = 3_000L,
        )

        assertThat(dao.entries).hasSize(1)
        assertThat(dao.entries.single().renderedBody).isEqualTo("Battery low")
        assertThat(dao.entries.single().status).isEqualTo(HistoryStatus.SUCCESS.name)
    }

    @Test
    fun `stores processing log with history entry`() = runTest {
        val dao = FakeNotificationHistoryDao()
        val repository = NotificationHistoryRepository(dao)

        repository.recordNotification(
            event = forwardEvent(type = EventType.NOTIFICATION, packageName = "com.tencent.mm"),
            status = HistoryStatus.SUCCESS,
            renderedTitle = "微信",
            renderedBody = "在吗",
            forwardedAt = 2_000L,
            log = "10:00:00.000  收到通知\n10:00:00.320  响应：HTTP 200",
        )

        assertThat(dao.entries.single().log).isEqualTo("10:00:00.000  收到通知\n10:00:00.320  响应：HTTP 200")
    }

    @Test
    fun `stores failed and filtered entries with reason`() = runTest {
        val dao = FakeNotificationHistoryDao()
        val repository = NotificationHistoryRepository(dao)

        repository.recordNotification(
            event = forwardEvent(type = EventType.NOTIFICATION, packageName = "com.tencent.mobileqq"),
            status = HistoryStatus.FAILED,
            renderedTitle = "QQ",
            renderedBody = "消息",
            forwardedAt = 1_000L,
            filterReason = "HTTP 500",
        )
        repository.recordNotification(
            event = forwardEvent(type = EventType.NOTIFICATION, packageName = "com.tencent.mobileqq", postedAt = 2_000L),
            status = HistoryStatus.FILTERED,
            renderedTitle = "QQ",
            renderedBody = "消息",
            forwardedAt = 2_000L,
            filterReason = "持续性通知",
        )

        val models = dao.entries.map { it.toModel() }
        assertThat(models.map { it.status })
            .containsExactly(HistoryStatus.FAILED, HistoryStatus.FILTERED)
        assertThat(models.map { it.filterReason })
            .containsExactly("HTTP 500", "持续性通知")
    }

    @Test
    fun `keeps all history entries without pruning`() = runTest {
        val dao = FakeNotificationHistoryDao()
        val repository = NotificationHistoryRepository(dao)

        repeat(205) { index ->
            repository.recordNotification(
                event = forwardEvent(
                    type = EventType.NOTIFICATION,
                    packageName = "jp.naver.line.android",
                    postedAt = index.toLong(),
                ),
                status = HistoryStatus.SUCCESS,
                renderedTitle = "LINE",
                renderedBody = "Message $index",
                forwardedAt = index.toLong(),
            )
        }

        assertThat(dao.entries).hasSize(205)
        val bodies = dao.entries.map { it.renderedBody }
        assertThat(bodies).contains("Message 0")
        assertThat(bodies).contains("Message 204")
    }

    @Test
    fun `clear history deletes every entry`() = runTest {
        val dao = FakeNotificationHistoryDao()
        val repository = NotificationHistoryRepository(dao)

        repository.recordNotification(
            event = forwardEvent(type = EventType.NOTIFICATION, packageName = "com.tencent.mm"),
            status = HistoryStatus.SUCCESS,
            renderedTitle = "微信",
            renderedBody = "在吗",
            forwardedAt = 1_000L,
        )
        repository.clearHistory()

        assertThat(dao.entries).isEmpty()
    }

    private fun forwardEvent(
        type: EventType,
        packageName: String,
        postedAt: Long = 1_000L,
    ): ForwardEvent {
        return ForwardEvent(
            type = type,
            captureSource = CaptureSource.DIRECT,
            packageName = packageName,
            sourceLabel = "LINE",
            title = "Battery low",
            body = "Right now",
            postedAt = postedAt,
            dedupeKey = "dedupe-$postedAt",
        )
    }

    private class FakeNotificationHistoryDao : NotificationHistoryDao {
        val entries = mutableListOf<NotificationHistoryEntity>()
        private val flow = MutableStateFlow<List<NotificationHistoryEntity>>(emptyList())

        override fun observeAll(): Flow<List<NotificationHistoryEntity>> = flow

        override suspend fun insert(entry: NotificationHistoryEntity) {
            entries += entry
            publish()
        }

        override suspend fun deleteAll() {
            entries.clear()
            publish()
        }

        private fun publish() {
            flow.value = entries.sortedByDescending { it.forwardedAt }
        }
    }
}

package dev.yakitori.barkforwarder.domain

import dev.yakitori.barkforwarder.data.model.EventType
import dev.yakitori.barkforwarder.data.model.ForwardEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatNotificationOptimizerTest {
    private fun event(
        packageName: String = "com.tencent.mm",
        title: String,
        body: String,
        type: EventType = EventType.NOTIFICATION,
    ) = ForwardEvent(
        type = type,
        packageName = packageName,
        sourceLabel = "微信",
        title = title,
        body = body,
        postedAt = 0L,
        dedupeKey = "key",
    )

    @Test
    fun genericTitleUsesSenderFromBody() {
        val rendered = ChatNotificationOptimizer.optimize(
            event(title = "微信", body = "张三：晚上一起吃饭吗"),
            sourceLabel = "微信",
        )

        requireNotNull(rendered)
        assertEquals("张三", rendered.title)
        assertEquals("晚上一起吃饭吗", rendered.body)
        assertEquals("微信 · 张三", rendered.group)
    }

    @Test
    fun conversationTitleKeepsBodyWhenSenderDiffers() {
        val rendered = ChatNotificationOptimizer.optimize(
            event(title = "家庭群", body = "张三：收到"),
            sourceLabel = "微信",
        )

        requireNotNull(rendered)
        assertEquals("家庭群", rendered.title)
        assertEquals("张三：收到", rendered.body)
        assertEquals("微信 · 家庭群", rendered.group)
    }

    @Test
    fun redundantSenderPrefixIsStripped() {
        val rendered = ChatNotificationOptimizer.optimize(
            event(title = "张三", body = "张三：在吗"),
            sourceLabel = "微信",
        )

        requireNotNull(rendered)
        assertEquals("张三", rendered.title)
        assertEquals("在吗", rendered.body)
    }

    @Test
    fun nonChatPackageIsNotOptimized() {
        assertNull(
            ChatNotificationOptimizer.optimize(
                event(packageName = "com.example.app", title = "标题", body = "正文"),
                sourceLabel = "示例",
            ),
        )
    }

    @Test
    fun nonNotificationEventIsNotOptimized() {
        assertNull(
            ChatNotificationOptimizer.optimize(
                event(title = "张三", body = "在吗", type = EventType.SMS),
                sourceLabel = "微信",
            ),
        )
    }
}

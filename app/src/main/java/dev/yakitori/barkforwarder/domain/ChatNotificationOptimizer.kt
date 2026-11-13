package dev.yakitori.barkforwarder.domain

import dev.yakitori.barkforwarder.data.model.EventType
import dev.yakitori.barkforwarder.data.model.ForwardEvent
import java.util.Locale

/** 把微信 / QQ 通知改写为「会话名作标题、消息内容作正文」，并按会话分组。 */
object ChatNotificationOptimizer {
    data class Rendered(
        val title: String,
        val body: String,
        val group: String,
    )

    private val chatPackages = setOf(
        "com.tencent.mm",       // 微信
        "com.tencent.mobileqq", // QQ
        "com.tencent.tim",      // TIM
    )

    private val genericTitles = setOf(
        "微信", "wechat", "qq", "qq消息", "tim", "tim消息", "qq邮箱提醒",
    )

    private const val SEPARATORS = "：:"

    fun isChatPackage(packageName: String?): Boolean =
        packageName != null && packageName in chatPackages

    fun optimize(event: ForwardEvent, sourceLabel: String): Rendered? {
        if (event.type != EventType.NOTIFICATION) return null
        if (!isChatPackage(event.packageName)) return null

        val rawTitle = event.title.trim()
        val rawBody = event.body.trim()

        val titleIsGeneric = rawTitle.isBlank() ||
            rawTitle.equals(sourceLabel, ignoreCase = true) ||
            genericTitles.contains(rawTitle.lowercase(Locale.ROOT))

        var conversation: String
        var content: String
        if (titleIsGeneric) {
            val parsed = splitSenderAndContent(rawBody)
            if (parsed != null) {
                conversation = parsed.first
                content = parsed.second
            } else {
                conversation = sourceLabel
                content = rawBody
            }
        } else {
            conversation = rawTitle
            content = rawBody
            val parsed = splitSenderAndContent(rawBody)
            if (parsed != null && parsed.first.equals(rawTitle, ignoreCase = true)) {
                content = parsed.second
            }
        }

        if (conversation.isBlank()) conversation = sourceLabel
        if (content.isBlank()) content = rawBody

        return Rendered(
            title = conversation,
            body = content,
            group = "$sourceLabel · $conversation",
        )
    }

    private fun splitSenderAndContent(text: String): Pair<String, String>? {
        val index = text.indexOfFirst { it in SEPARATORS }
        if (index <= 0 || index >= text.length - 1) return null
        val sender = text.substring(0, index).trim()
        val content = text.substring(index + 1).trim()
        if (sender.isBlank() || content.isBlank()) return null
        return sender to content
    }
}

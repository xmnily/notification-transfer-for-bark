package dev.yakitori.barkforwarder.domain

import dev.yakitori.barkforwarder.data.model.BarkConfig
import java.net.URI

data class BarkEndpoint(
    val serverUrl: String,
    val deviceKey: String,
)

object BarkEndpointParser {
    private const val DEFAULT_SERVER_URL = "https://api.day.app/push"

    fun parse(input: String): BarkEndpoint? {
        val trimmed = input.trim().removeSuffix("/")
        if (trimmed.isBlank()) return null

        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            parseUrl(trimmed)
        } else {
            BarkEndpoint(
                serverUrl = DEFAULT_SERVER_URL,
                deviceKey = trimmed.trim('/'),
            )
        }
    }

    fun toDisplayValue(config: BarkConfig): String {
        if (config.deviceKey.isBlank()) return ""
        val serverUrl = config.serverUrl.ifBlank { DEFAULT_SERVER_URL }
        val uri = runCatching { URI(serverUrl) }.getOrNull() ?: return "https://api.day.app/${config.deviceKey}"
        val pathSegments = uri.path.orEmpty().split('/').filter { it.isNotBlank() }
        val displaySegments = when (pathSegments.lastOrNull()) {
            "push" -> pathSegments.dropLast(1) + config.deviceKey
            else -> pathSegments + config.deviceKey
        }
        val normalizedPath = displaySegments.joinToString(prefix = "/", separator = "/")
        return "${uri.scheme}://${uri.rawAuthority}$normalizedPath"
    }

    private fun parseUrl(input: String): BarkEndpoint? {
        val uri = runCatching { URI(input) }.getOrNull() ?: return null
        val scheme = uri.scheme ?: return null
        val authority = uri.rawAuthority ?: return null
        val segments = uri.path.orEmpty().split('/').filter { it.isNotBlank() }
        if (segments.isEmpty()) return null

        val deviceKey = segments.last()
        if (deviceKey.equals("push", ignoreCase = true)) return null

        val baseSegments = segments.dropLast(1)
        val normalizedPath = (baseSegments + "push").joinToString(prefix = "/", separator = "/")
        return BarkEndpoint(
            serverUrl = "$scheme://$authority$normalizedPath",
            deviceKey = deviceKey,
        )
    }
}

data class ValidatedBarkSettings(
    val endpoint: BarkEndpoint,
)

object BarkSettingsValidator {
    fun validate(
        barkUrlOrKey: String,
    ): ValidatedBarkSettings {
        val endpoint = BarkEndpointParser.parse(barkUrlOrKey)
            ?: throw IllegalArgumentException("请输入 Bark 链接或设备 Key。")

        return ValidatedBarkSettings(
            endpoint = endpoint,
        )
    }
}

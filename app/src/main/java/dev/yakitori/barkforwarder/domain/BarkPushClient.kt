package dev.yakitori.barkforwarder.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

data class BarkPushResult(
    val statusCode: Int,
    val body: String,
)

class BarkPushClient(private val httpClient: OkHttpClient) {
    fun formBody(deviceKey: String, payload: BarkPushPayload): FormBody =
        FormBody.Builder()
            .add("device_key", deviceKey)
            .add("title", payload.title)
            .add("body", payload.body)
            .add("group", payload.group)
            .add("level", payload.level)
            .apply {
                payload.call?.let { add("call", it.toString()) }
                payload.icon?.let { add("icon", it) }
                payload.image?.let { add("image", it) }
            }
            .build()

    suspend fun send(serverUrl: String, deviceKey: String, payload: BarkPushPayload): BarkPushResult =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(serverUrl)
                .post(formBody(deviceKey, payload))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException(
                        "Bark 推送失败，HTTP ${response.code}" +
                            if (responseBody.isBlank()) "" else "：$responseBody",
                    )
                }
                BarkPushResult(statusCode = response.code, body = responseBody)
            }
        }
}

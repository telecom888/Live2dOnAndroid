package com.bangdream.pet.voice

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * mimo-v2.5-tts-voiceclone（免费）音色克隆合成。
 * 文档：docs/mimo-tts-voiceclone.txt（已实测：返回 message.audio.data base64 WAV）
 */
class VoiceCloneClient(
    private val baseUrl: String,
    private val model: String,
    private val apiKey: String,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun synthesize(text: String, sampleFile: File): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val sampleBytes = sampleFile.readBytes()
            if (sampleBytes.size > 10 * 1024 * 1024) return@runCatching null
            val mime = when (sampleFile.extension.lowercase()) {
                "wav" -> "audio/wav"
                else -> "audio/mpeg"
            }
            val b64 = Base64.getEncoder().encodeToString(sampleBytes)
            val body = JSONObject()
                .put("model", model)
                .put(
                    "messages",
                    org.json.JSONArray()
                        .put(JSONObject().put("role", "user").put("content", ""))
                        .put(JSONObject().put("role", "assistant").put("content", text)),
                )
                .put("audio", JSONObject().put("format", "wav").put("voice", "data:$mime;base64,$b64"))
                .toString()

            val endpoint = if (baseUrl.endsWith("/chat/completions", ignoreCase = true)) baseUrl else "$baseUrl/chat/completions"
            val request = Request.Builder()
                .url(endpoint)
                .header("Authorization", "Bearer $apiKey")
                .post(body.toRequestBody(jsonMediaType))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val json = JSONObject(response.body?.string().orEmpty())
                val audio = json.optJSONArray("choices")
                    ?.optJSONObject(0)?.optJSONObject("message")?.optJSONObject("audio") ?: return@use null
                Base64.getDecoder().decode(audio.optString("data"))
            }
        }.getOrNull()
    }
}

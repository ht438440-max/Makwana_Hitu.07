package com.example.rtmp

import java.net.URI

data class ParsedRtmpUrl(
    val isTls: Boolean,
    val host: String,
    val port: Int,
    val app: String,
    val tcUrl: String
)

object RtmpUrlParser {
    fun parse(rawUrl: String): Result<ParsedRtmpUrl> {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("RTMP Server URL cannot be empty."))
        }
        val lower = trimmed.lowercase()
        if (!lower.startsWith("rtmp://") && !lower.startsWith("rtmps://")) {
            return Result.failure(
                IllegalArgumentException("URL must start with rtmp:// or rtmps://")
            )
        }
        return try {
            val isTls = lower.startsWith("rtmps://")
            val normalizedForUri = if (isTls) {
                "https://" + trimmed.substring("rtmps://".length)
            } else {
                "http://" + trimmed.substring("rtmp://".length)
            }
            val uri = URI(normalizedForUri)
            val host = uri.host?.takeIf { it.isNotBlank() }
                ?: return Result.failure(IllegalArgumentException("Invalid RTMP host in URL."))
            val defaultPort = if (isTls) 443 else 1935
            val port = if (uri.port > 0) uri.port else defaultPort
            val rawPath = uri.path?.trim('/') ?: ""
            val app = rawPath.ifBlank { "live" }
            val scheme = if (isTls) "rtmps" else "rtmp"
            val tcUrl = "$scheme://$host:$port/$app"
            Result.success(
                ParsedRtmpUrl(
                    isTls = isTls,
                    host = host,
                    port = port,
                    app = app,
                    tcUrl = tcUrl
                )
            )
        } catch (t: Throwable) {
            Result.failure(IllegalArgumentException("Malformed RTMP URL: ${t.message}"))
        }
    }
}

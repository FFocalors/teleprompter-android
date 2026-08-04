package com.zhy20.teleprompter.data.serialization

import com.zhy20.teleprompter.core.model.CountdownOption
import com.zhy20.teleprompter.core.model.GuideMode
import com.zhy20.teleprompter.core.model.PlaybackOrientation
import com.zhy20.teleprompter.core.model.PlaybackSettings
import com.zhy20.teleprompter.core.model.PlaybackTextAlignment
import com.zhy20.teleprompter.core.model.RhythmMode
import java.util.logging.Level
import java.util.logging.Logger
import org.json.JSONObject

object PlaybackSettingsSerializer {
    const val SchemaVersion = 1
    private val logger = Logger.getLogger("PlaybackSettingsSerializer")

    fun encode(settings: PlaybackSettings): String = JSONObject().apply {
        put("schemaVersion", SchemaVersion)
        put("backgroundColor", settings.backgroundColor)
        put("textColor", settings.textColor)
        put("fontSize", settings.fontSize)
        put("orientation", settings.orientation.name)
        put("textAlignment", settings.textAlignment.name)
        put("mirrorEnabled", settings.mirrorEnabled)
        put("rhythmMode", settings.rhythmMode.name)
        put("speedMultiplier", settings.speedMultiplier.toDouble())
        put("targetDurationSeconds", settings.targetDurationSeconds)
        put("countdown", settings.countdown.name)
        put("guideMode", settings.guideMode.name)
        put("guideLinePosition", settings.guideLinePosition.toDouble())
        put("displayPresetId", settings.displayPresetId)
    }.toString()

    fun decode(json: String, fallback: PlaybackSettings = PlaybackSettings()): PlaybackSettings = runCatching {
        val root = JSONObject(json)
        require(root.optInt("schemaVersion", -1) == SchemaVersion) { "Unsupported PlaybackSettings schema" }
        PlaybackSettings(
            backgroundColor = root.optString("backgroundColor", fallback.backgroundColor),
            textColor = root.optString("textColor", fallback.textColor),
            fontSize = root.optInt("fontSize", fallback.fontSize).coerceIn(24, 160),
            orientation = root.enumOr("orientation", fallback.orientation),
            textAlignment = root.enumOr("textAlignment", fallback.textAlignment),
            mirrorEnabled = root.optBoolean("mirrorEnabled", fallback.mirrorEnabled),
            rhythmMode = root.enumOr("rhythmMode", fallback.rhythmMode),
            speedMultiplier = root.optDouble("speedMultiplier", fallback.speedMultiplier.toDouble()).toFloat().coerceIn(.25f, 4f),
            targetDurationSeconds = root.optInt("targetDurationSeconds", fallback.targetDurationSeconds).coerceAtLeast(1),
            countdown = root.enumOr("countdown", fallback.countdown),
            guideMode = root.enumOr("guideMode", fallback.guideMode),
            guideLinePosition = root.optDouble("guideLinePosition", fallback.guideLinePosition.toDouble()).toFloat().coerceIn(.1f, .9f),
            displayPresetId = root.optNullableString("displayPresetId") ?: fallback.displayPresetId,
        )
    }.getOrElse { error ->
        logger.log(Level.WARNING, "Invalid PlaybackSettings JSON; using defaults", error)
        fallback
    }

    private inline fun <reified T : Enum<T>> JSONObject.enumOr(key: String, fallback: T): T =
        optString(key).let { value -> enumValues<T>().firstOrNull { it.name == value } } ?: fallback

    private fun JSONObject.optNullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf(String::isNotBlank)
}

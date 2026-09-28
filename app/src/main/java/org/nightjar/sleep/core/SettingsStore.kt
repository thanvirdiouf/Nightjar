package org.nightjar.sleep.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import org.nightjar.sleep.core.analysis.AnalysisConfig
import org.nightjar.sleep.core.analysis.SensingMode

data class AppSettings(
    val mode: SensingMode = SensingMode.ACCELEROMETER,
    val theme: String = "DARK",
    val lowThreshold: Float = 0.08f,
    val highThreshold: Float = 0.35f,
    val recordNoise: Boolean = false,
    val retentionDays: Int = 7,
    val alarmHour: Int = 7,
    val alarmMinute: Int = 0,
    val wakeWindowMinutes: Int = 30,
    val rampSeconds: Int = 30,
    val alarmVolume: Float = 0.8f,
    val alarmTone: String = "Dawn",
    val alarmToneUri: String = "",
    val snoozeMinutes: Int = 5,
    val soundTimerMinutes: Int = 30,
    val stopSoundsOnSleep: Boolean = true
) {
    fun analysisConfig() = AnalysisConfig(lowThreshold, highThreshold)
    fun toJson(): JSONObject = JSONObject().apply {
        put("mode", mode.name); put("theme", theme); put("lowThreshold", lowThreshold.toDouble()); put("highThreshold", highThreshold.toDouble())
        put("recordNoise", recordNoise); put("retentionDays", retentionDays)
        put("alarmHour", alarmHour); put("alarmMinute", alarmMinute); put("wakeWindowMinutes", wakeWindowMinutes)
        put("rampSeconds", rampSeconds); put("alarmVolume", alarmVolume.toDouble())
        put("alarmTone", alarmTone); put("alarmToneUri", alarmToneUri); put("snoozeMinutes", snoozeMinutes)
        put("soundTimerMinutes", soundTimerMinutes); put("stopSoundsOnSleep", stopSoundsOnSleep)
    }
    companion object {
        fun fromJson(json: JSONObject): AppSettings {
            val low = json.optDouble("lowThreshold", 0.08).toFloat().takeIf { it.isFinite() }?.coerceIn(0.01f, 0.6f) ?: 0.08f
            val high = json.optDouble("highThreshold", 0.35).toFloat().takeIf { it.isFinite() }?.coerceIn(low + 0.02f, 1f) ?: maxOf(0.35f, low + 0.02f)
            return AppSettings(
                runCatching { SensingMode.valueOf(json.optString("mode", "ACCELEROMETER")) }.getOrDefault(SensingMode.ACCELEROMETER),
                json.optString("theme", "DARK").takeIf { it in setOf("DARK", "LIGHT", "SYSTEM") } ?: "DARK",
                low, high, json.optBoolean("recordNoise", false),
                json.optInt("retentionDays", 7).coerceIn(1, 90), json.optInt("alarmHour", 7).coerceIn(0, 23),
                json.optInt("alarmMinute", 0).coerceIn(0, 59), json.optInt("wakeWindowMinutes", 30).coerceIn(0, 60),
                json.optInt("rampSeconds", 30).coerceIn(5, 120), json.optDouble("alarmVolume", 0.8).toFloat().takeIf { it.isFinite() }?.coerceIn(0.1f, 1f) ?: 0.8f,
                json.optString("alarmTone", "Dawn"), json.optString("alarmToneUri", ""),
                json.optInt("snoozeMinutes", 5).coerceIn(1, 30), json.optInt("soundTimerMinutes", 30).coerceIn(0, 180),
                json.optBoolean("stopSoundsOnSleep", true)
            )
        }
    }
}

class SettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("nightjar-settings", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(runCatching {
        AppSettings.fromJson(JSONObject(preferences.getString("settings", "{}")!!))
    }.getOrDefault(AppSettings()))
    val flow = mutable.asStateFlow()
    val current: AppSettings get() = mutable.value
    @Synchronized fun update(transform: (AppSettings) -> AppSettings) {
        val next = AppSettings.fromJson(transform(current).toJson())
        preferences.edit().putString("settings", next.toJson().toString()).apply()
        mutable.value = next
    }
    fun restore(settings: AppSettings) = update { settings }
}

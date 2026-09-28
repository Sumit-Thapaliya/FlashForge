package com.flashlightstudio.viewmodel

import android.app.Application
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.content.Context
import kotlinx.coroutines.isActive
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flashlightstudio.data.PatternSegment
import com.flashlightstudio.data.SavedPattern
import com.flashlightstudio.data.StudioPreferences
import com.flashlightstudio.hardware.TorchController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random
import java.util.Locale

class StudioViewModel(app: Application) : AndroidViewModel(app) {
    val torch = TorchController(app)
    private val preferences = StudioPreferences(app)
    val savedPatterns = preferences.patterns.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())
    val darkTheme = preferences.darkTheme.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), true)
    val haptics = preferences.haptics.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), true)
    private val _running = MutableStateFlow(false); val running: StateFlow<Boolean> = _running.asStateFlow()
    private val _status = MutableStateFlow<String?>(null); val status: StateFlow<String?> = _status.asStateFlow()
    private var effectJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var beatJob: Job? = null

    fun startPattern(segments: List<PatternSegment>, repeat: Boolean = false, vibration: Boolean = false) {
        if (segments.isEmpty()) { _status.value = "Add at least one timeline segment"; return }
        stopEffects()
        effectJob = viewModelScope.launch {
            _running.value = true
            try {
                do {
                    for (segment in segments) {
                        torch.setEnabled(segment.on)
                        if (vibration && segment.on) vibrate(segment.durationMs) else cancelVibration()
                        delay(segment.durationMs.coerceIn(50, 5000))
                    }
                } while (repeat && isActive)
            } finally { torch.setEnabled(false); cancelVibration(); _running.value = false }
        }
    }
    fun sos() = startPattern(listOf(true to 1L, false to 1L, true to 1L, false to 1L, true to 1L, false to 3L, true to 3L, false to 1L, true to 3L, false to 1L, true to 3L, false to 3L, true to 1L, false to 1L, true to 1L, false to 1L, true to 1L, false to 7L).map { PatternSegment(it.first, it.second * 200) }, true)
    fun beacon() = startPattern(listOf(PatternSegment(true, 900), PatternSegment(false, 1500)), true)
    fun rapid() = startPattern(listOf(PatternSegment(true, 120), PatternSegment(false, 180)), true)
    fun slowBeacon() = startPattern(listOf(PatternSegment(true, 700), PatternSegment(false, 1800)), true)
    fun startStrobe(onMs: Long, offMs: Long) = startPattern(listOf(PatternSegment(true, onMs), PatternSegment(false, offMs)), true)
    fun stopEffects() { effectJob?.cancel(); effectJob = null; stopBeat(); torch.setEnabled(false); cancelVibration(); _running.value = false }
    fun startTimer(seconds: Int) {
        stopEffects()
        effectJob = viewModelScope.launch { _running.value = true; torch.setEnabled(true); delay(seconds.coerceAtLeast(1) * 1000L); torch.setEnabled(false); _running.value = false }
    }
    fun savePattern(name: String, segments: List<PatternSegment>) { viewModelScope.launch { preferences.savePattern(SavedPattern(name.trim().ifBlank { "My pattern" }, segments)) } }
    fun deletePattern(name: String) { viewModelScope.launch { preferences.deletePattern(name) } }
    fun setTheme(dark: Boolean) { viewModelScope.launch { preferences.setDarkTheme(dark) } }
    fun setHaptics(value: Boolean) { viewModelScope.launch { preferences.setHaptics(value) } }

    fun morse(text: String, unitMs: Long = 180): List<PatternSegment> {
        val alphabet = mapOf('A' to ".-", 'B' to "-...", 'C' to "-.-.", 'D' to "-..", 'E' to ".", 'F' to "..-.", 'G' to "--.", 'H' to "....", 'I' to "..", 'J' to ".---", 'K' to "-.-", 'L' to ".-..", 'M' to "--", 'N' to "-.", 'O' to "---", 'P' to ".--.", 'Q' to "--.-", 'R' to ".-.", 'S' to "...", 'T' to "-", 'U' to "..-", 'V' to "...-", 'W' to ".--", 'X' to "-..-", 'Y' to "-.--", 'Z' to "--..", '0' to "-----", '1' to ".----", '2' to "..---", '3' to "...--", '4' to "....-", '5' to ".....", '6' to "-....", '7' to "--...", '8' to "---..", '9' to "----.")
        val result = mutableListOf<PatternSegment>()
        text.uppercase().filter { it.isLetterOrDigit() || it == ' ' }.forEachIndexed { i, char ->
            if (char == ' ') { if (result.isNotEmpty()) result += PatternSegment(false, unitMs * 7) }
            else alphabet[char]?.forEachIndexed { j, mark -> result += PatternSegment(true, unitMs * if (mark == '-') 3 else 1); if (j < alphabet.getValue(char).lastIndex) result += PatternSegment(false, unitMs) }
            if (char != ' ' && i < text.lastIndex && text.getOrNull(i + 1) != ' ') result += PatternSegment(false, unitMs * 3)
        }
        return result
    }
    fun morseString(text: String): String {
        val codes = mapOf('A' to ".-", 'B' to "-...", 'C' to "-.-.", 'D' to "-..", 'E' to ".", 'F' to "..-.", 'G' to "--.", 'H' to "....", 'I' to "..", 'J' to ".---", 'K' to "-.-", 'L' to ".-..", 'M' to "--", 'N' to "-.", 'O' to "---", 'P' to ".--.", 'Q' to "--.-", 'R' to ".-.", 'S' to "...", 'T' to "-", 'U' to "..-", 'V' to "...-", 'W' to ".--", 'X' to "-..-", 'Y' to "-.--", 'Z' to "--..", '0' to "-----", '1' to ".----", '2' to "..---", '3' to "...--", '4' to "....-", '5' to ".....", '6' to "-....", '7' to "--...", '8' to "---..", '9' to "----.")
        return text.toUpperCase(Locale.ROOT).split(' ').joinToString(" / ") { word -> word.mapNotNull { codes[it] }.joinToString(" ") }
    }
    fun randomPattern(): List<PatternSegment> = List(Random.nextInt(5, 10)) { PatternSegment(Random.nextBoolean(), Random.nextLong(100, 900)) }

    fun startBeat(sensitivity: Float) {
        if (Build.VERSION.SDK_INT < 23) { _status.value = "Audio beat mode is unsupported"; return }
        stopEffects()
        beatJob = viewModelScope.launch(Dispatchers.IO) {
            val sampleRate = 44100
            val minBuffer = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (minBuffer <= 0) { _status.value = "Microphone audio is unavailable"; return@launch }
            val recorder = runCatching { AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuffer * 2) }.getOrNull()
            if (recorder == null || recorder.state != AudioRecord.STATE_INITIALIZED) { recorder?.release(); _status.value = "Could not initialize microphone"; return@launch }
            audioRecord = recorder
            val buffer = ShortArray(minBuffer / 2)
            var lastPeak = 0L
            try {
                recorder.startRecording(); _running.value = true
                while (isActive) {
                    val count = recorder.read(buffer, 0, buffer.size)
                    if (count < 0) break
                    if (count > 0) {
                        var sum = 0.0
                        for (i in 0 until count) { val value = buffer[i].toDouble(); sum += value * value }
                        val rms = kotlin.math.sqrt(sum / count) / Short.MAX_VALUE
                        val now = System.currentTimeMillis()
                        if (rms > (0.08 * (1.5 - sensitivity.coerceIn(0f, 1f))) && now - lastPeak > 230) {
                            lastPeak = now
                            withContext(Dispatchers.Main) { torch.setEnabled(true); delay(90); torch.setEnabled(false) }
                        }
                    }
                }
            } catch (_: Exception) { _status.value = "Beat mode stopped" }
            finally { runCatching { recorder.stop() }; recorder.release(); audioRecord = null; _running.value = false; withContext(Dispatchers.Main) { torch.setEnabled(false) } }
        }
    }
    fun stopBeat() { beatJob?.cancel(); beatJob = null; runCatching { audioRecord?.stop() } }
    private fun vibrator(): Vibrator? {
        val app = getApplication<Application>()
        return if (Build.VERSION.SDK_INT >= 31) app.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    @Suppress("DEPRECATION")
    private fun vibrate(duration: Long) {
        val deviceVibrator = vibrator() ?: return
        if (!deviceVibrator.hasVibrator()) return
        runCatching { if (Build.VERSION.SDK_INT >= 26) deviceVibrator.vibrate(VibrationEffect.createOneShot(duration.coerceAtMost(5000), VibrationEffect.DEFAULT_AMPLITUDE)) else deviceVibrator.vibrate(duration) }
    }
    private fun cancelVibration() { runCatching { vibrator()?.cancel() } }
    override fun onCleared() { stopEffects(); super.onCleared() }
}

package org.nightjar.sleep.core.audio

import android.content.Context
import java.io.*
import kotlin.math.*
import kotlin.random.Random

object WavFiles {
    fun write(file: File, pcm: ShortArray, sampleRate: Int = 16_000) {
        file.parentFile?.mkdirs()
        DataOutputStream(BufferedOutputStream(FileOutputStream(file))).use { out ->
            fun int(value: Int) { out.writeInt(Integer.reverseBytes(value)) }
            fun short(value: Int) { out.writeShort(java.lang.Short.reverseBytes(value.toShort()).toInt()) }
            out.writeBytes("RIFF"); int(36 + pcm.size * 2); out.writeBytes("WAVE")
            out.writeBytes("fmt "); int(16); short(1); short(1); int(sampleRate); int(sampleRate * 2); short(2); short(16)
            out.writeBytes("data"); int(pcm.size * 2)
            pcm.forEach { out.writeShort(java.lang.Short.reverseBytes(it).toInt()) }
        }
    }
}

/** Original procedural sounds. No external audio assets or network requests. */
object SynthAudio {
    val alarmTones = listOf("Dawn", "Soft bells", "Warm pulse")
    val ambientSounds = listOf("Rain", "Ocean", "White noise", "Pink noise", "Brown noise", "Fan")
    fun file(context: Context, name: String): File {
        val selected = name.takeIf { it in alarmTones || it in ambientSounds } ?: "Dawn"
        val file = File(context.cacheDir, "sounds/" + selected.replace(" ", "_") + ".wav")
        if (!file.isFile) WavFiles.write(file, samples(selected, 12))
        return file
    }
    fun samples(name: String, seconds: Int = 12, rate: Int = 16_000): ShortArray {
        require(seconds in 1..60 && rate > 0)
        val random = Random(name.hashCode())
        var brown = 0.0
        var pink1 = 0.0; var pink2 = 0.0; var pink3 = 0.0
        var drop = 0.0
        val notes = doubleArrayOf(523.25, 659.25, 783.99, 659.25)
        val count = seconds * rate
        return ShortArray(count) { index ->
            val t = index / rate.toDouble()
            val white = random.nextDouble(-1.0, 1.0)
            brown = (brown + white * 0.025) * 0.997
            pink1 = 0.997 * pink1 + 0.003 * white
            pink2 = 0.985 * pink2 + 0.015 * white
            pink3 = 0.95 * pink3 + 0.05 * white
            if (random.nextDouble() > 0.9995) drop = 0.6
            drop *= 0.985
            val beat = t % 1.5
            val note = notes[(t / 1.5).toInt() % notes.size]
            val envelope = (1 - exp(-beat * 25)) * exp(-beat * 2.5)
            val value = when (name) {
                "White noise" -> white * 0.22
                "Pink noise" -> (pink1 * 7 + pink2 * 3 + pink3) * 0.45
                "Brown noise" -> brown * 0.8
                "Rain" -> white * 0.15 + drop * sin(2 * PI * 2700 * t) * 0.16
                "Ocean" -> white * (0.09 + 0.12 * (0.5 + 0.5 * sin(2 * PI * t / seconds))) + brown * 0.35
                "Fan" -> sin(2 * PI * 60 * t) * 0.08 + sin(2 * PI * 120 * t) * 0.04 + white * 0.09
                "Soft bells" -> envelope * (sin(2 * PI * note * t) * 0.42 + sin(2 * PI * note * 2.01 * t) * 0.16)
                "Warm pulse" -> sin(2 * PI * 440 * t) * (0.25 + 0.2 * sin(2 * PI * t / 3))
                else -> envelope * (sin(2 * PI * note * t) * 0.5 + sin(2 * PI * note * 0.5 * t) * 0.13)
            }
            val edge = minOf(1.0, index / (rate * 0.02), (count - 1 - index) / (rate * 0.02)).coerceAtLeast(0.0)
            (value * edge * 32767).roundToInt().coerceIn(-32767, 32767).toShort()
        }
    }
}

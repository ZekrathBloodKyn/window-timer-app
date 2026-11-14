package dev.wolsey.windowtimer.alerts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/**
 * Plays a quarter-mark alert: the quarter's imported sound file if it has one,
 * otherwise generated beeps built from its AlertConfig.
 * Everything plays on the alarm stream, so it's audible even with media volume down.
 */
object Alerts {
    private const val RATE = 22050
    private const val GAP_MS = 150
    private const val RISE_SEMITONES = 3

    private val mainHandler = Handler(Looper.getMainLooper())

    // Stops whatever sound is currently playing, if any.
    private var stopCurrent: (() -> Unit)? = null

    // ---- Public API --------------------------------------------------------

    /** index: 0 = 25%, 1 = 50%, 2 = 75%, 3 = 100%. */
    fun play(context: Context, index: Int, config: AlertConfig) {
        stop() // never layer two sounds on top of each other
        if (config.customName != null && playCustom(context, index)) return
        playBeeps(config)
    }

    fun stop() {
        stopCurrent?.invoke()
        stopCurrent = null
    }

    /** How long this alert will take to play, so callers know when it's done. */
    fun durationMs(context: Context, index: Int, config: AlertConfig): Long {
        if (config.customName != null) {
            customDurationMs(context, index)?.let { return it }
        }
        return beepsDurationMs(config)
    }

    // ---- Custom sound files ------------------------------------------------

    private fun alarmAttributes() = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    /** Returns false if the file couldn't be set up, so the caller can fall back to beeps. */
    private fun playCustom(context: Context, index: Int): Boolean {
        val file = AlertSettings.soundFile(context, index)
        if (!file.exists()) return false
        return try {
            val player = MediaPlayer()
            var released = false
            val release = {
                if (!released) {
                    released = true
                    try { player.release() } catch (_: Exception) {}
                }
            }
            player.setAudioAttributes(alarmAttributes())
            player.setDataSource(file.path)
            player.setOnPreparedListener { it.start() }
            player.setOnCompletionListener { release() }
            player.setOnErrorListener { _, _, _ -> release(); true }
            // Cap long files so they can't run on indefinitely.
            mainHandler.postDelayed({ release() }, AlertSettings.MAX_PLAY_MS)
            stopCurrent = { release() }
            player.prepareAsync()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun customDurationMs(context: Context, index: Int): Long? {
        val file = AlertSettings.soundFile(context, index)
        if (!file.exists()) return null
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.path)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.coerceAtMost(AlertSettings.MAX_PLAY_MS)
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    // ---- Generated beeps ---------------------------------------------------

    private fun beepsDurationMs(config: AlertConfig): Long =
        config.beeps.toLong() * config.beepMs + (config.beeps - 1).toLong() * GAP_MS

    private fun tone(freq: Double, ms: Int, vol: Double = 0.9): ShortArray {
        val n = RATE * ms / 1000
        val out = ShortArray(n)
        val fade = (RATE * 0.01).toInt() // 10 ms fade in/out to avoid clicks
        for (i in 0 until n) {
            val env = when {
                i < fade -> i / fade.toDouble()
                i > n - fade -> (n - i) / fade.toDouble()
                else -> 1.0
            }
            out[i] = (sin(2 * PI * freq * i / RATE) * env * vol * Short.MAX_VALUE)
                .toInt().toShort()
        }
        return out
    }

    private fun gap() = ShortArray(RATE * GAP_MS / 1000)

    private fun buildBeeps(config: AlertConfig): ShortArray {
        val parts = mutableListOf<ShortArray>()
        for (i in 0 until config.beeps) {
            val freq = if (config.rising)
                config.pitchHz * 2.0.pow(i * RISE_SEMITONES / 12.0)
            else
                config.pitchHz.toDouble()
            parts += tone(freq, config.beepMs)
            if (i < config.beeps - 1) parts += gap()
        }
        val all = ShortArray(parts.sumOf { it.size })
        var pos = 0
        for (p in parts) {
            p.copyInto(all, pos)
            pos += p.size
        }
        return all
    }

    private fun playBeeps(config: AlertConfig) {
        val data = buildBeeps(config)
        val track = AudioTrack.Builder()
            .setAudioAttributes(alarmAttributes())
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(data.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(data, 0, data.size)
        track.notificationMarkerPosition = data.size
        track.setPlaybackPositionUpdateListener(
            object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(t: AudioTrack) { t.release() }
                override fun onPeriodicNotification(t: AudioTrack) {}
            }
        )
        stopCurrent = {
            try { track.stop() } catch (_: Exception) {}
            try { track.release() } catch (_: Exception) {}
        }
        track.play()
    }
}
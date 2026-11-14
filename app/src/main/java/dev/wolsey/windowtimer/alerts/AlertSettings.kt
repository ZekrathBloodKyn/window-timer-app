package dev.wolsey.windowtimer.alerts

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.edit
import java.io.File

/** Sound settings for one quarter mark. */
data class AlertConfig(
    val enabled: Boolean,
    val pitchHz: Int,    // 200..2000
    val beeps: Int,      // 1..6
    val beepMs: Int,     // 100..1000
    val rising: Boolean, // each beep steps up in pitch (chime-style)
    val customName: String? = null // display name of imported sound; null = generated beeps
)

object AlertSettings {
    private const val PREFS = "alert_settings"

    const val PITCH_MIN = 200
    const val PITCH_MAX = 2000
    const val BEEPS_MIN = 1
    const val BEEPS_MAX = 6
    const val LENGTH_MIN = 100
    const val LENGTH_MAX = 1000

    const val MAX_FILE_BYTES = 10L * 1024 * 1024
    const val MAX_PLAY_MS = 22_000L

    /**
     * Index 0..2 = early, middle and late intervals, 3 = the end of the job,
     * 4 = the end of the first part when interior and exterior run separately.
     */
    val defaults = listOf(
        AlertConfig(enabled = true, pitchHz = 660,  beeps = 1, beepMs = 600, rising = false),
        AlertConfig(enabled = true, pitchHz = 880,  beeps = 2, beepMs = 400, rising = false),
        AlertConfig(enabled = true, pitchHz = 1175, beeps = 3, beepMs = 300, rising = false),
        AlertConfig(enabled = true, pitchHz = 523,  beeps = 4, beepMs = 350, rising = true),
        AlertConfig(enabled = true, pitchHz = 988,  beeps = 2, beepMs = 300, rising = true)
    )

    /** Where a quarter's imported sound lives in app-private storage. */
    fun soundFile(context: Context, index: Int) = File(context.filesDir, "alert_q$index.snd")

    fun load(context: Context): List<AlertConfig> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return defaults.mapIndexed { i, d ->
            val name = prefs.getString("q${i}_custom", null)
            AlertConfig(
                enabled = prefs.getBoolean("q${i}_enabled", d.enabled),
                pitchHz = prefs.getInt("q${i}_pitch", d.pitchHz).coerceIn(PITCH_MIN, PITCH_MAX),
                beeps = prefs.getInt("q${i}_beeps", d.beeps).coerceIn(BEEPS_MIN, BEEPS_MAX),
                beepMs = prefs.getInt("q${i}_length", d.beepMs).coerceIn(LENGTH_MIN, LENGTH_MAX),
                rising = prefs.getBoolean("q${i}_rising", d.rising),
                // Only trust the saved name if the copied file is still there.
                customName = name?.takeIf { soundFile(context, i).exists() }
            )
        }
    }

    fun save(context: Context, index: Int, config: AlertConfig) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putBoolean("q${index}_enabled", config.enabled)
            .putInt("q${index}_pitch", config.pitchHz)
            .putInt("q${index}_beeps", config.beeps)
            .putInt("q${index}_length", config.beepMs)
            .putBoolean("q${index}_rising", config.rising)
            if (config.customName != null) {
                putString("q${index}_custom", config.customName)
            } else {
                remove("q${index}_custom")
            }
        }
    }

    /**
     * Copies the picked audio file into app storage and checks it's playable.
     * Returns its display name, or null if it was too big or not valid audio.
     */
    fun importSound(context: Context, index: Int, uri: Uri): String? {
        val dest = soundFile(context, index)
        val tmp = File(context.filesDir, "alert_q$index.tmp")
        try {
            var copied = 0L
            context.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        copied += n
                        if (copied > MAX_FILE_BYTES) { cleanup(tmp); return null }
                        output.write(buffer, 0, n)
                    }
                }
            } ?: run { cleanup(tmp); return null }

            // Reject anything that isn't audio the system can read.
            val retriever = MediaMetadataRetriever()
            val duration = try {
                retriever.setDataSource(tmp.path)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            } catch (_: Exception) {
                null
            } finally {
                retriever.release()
            }
            if (duration == null || duration <= 0) { cleanup(tmp); return null }

            if (dest.exists()) dest.delete()
            if (!tmp.renameTo(dest)) { cleanup(tmp); return null }
            return displayName(context, uri) ?: "Custom sound"
        } catch (_: Exception) {
            cleanup(tmp)
            return null
        }
    }

    /** Removes a quarter's imported sound file. */
    fun deleteSound(context: Context, index: Int) {
        soundFile(context, index).delete()
    }

    private fun cleanup(tmp: File) {
        tmp.delete()
    }

    private fun displayName(context: Context, uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
}
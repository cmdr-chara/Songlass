package dev.cmdrchara.songlass.debug

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.sound.DefaultSoundTarget
import dev.cmdrchara.songlass.ui.AndroidDefaultSoundStore
import dev.cmdrchara.songlass.ui.AndroidDefaultSoundWriter
import dev.cmdrchara.songlass.ui.DefaultSoundResult
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Debug/DUMP-only probe: real owned media IO, in-memory defaults, never a system-sound write. */
class DefaultSoundProbeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "dev.cmdrchara.songlass.debug.DEFAULT_SOUND_PROBE") return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeout(25_000) { runProbe(context.applicationContext) }
            } catch (failure: Throwable) {
                Log.e("DefaultSoundProbe", "FAIL ${failure.javaClass.simpleName}: ${failure.message}")
            } finally { pending.finish() }
        }
    }

    private suspend fun runProbe(context: Context) {
        val resolver = context.contentResolver
        val types = listOf(RingtoneManager.TYPE_RINGTONE, RingtoneManager.TYPE_ALARM, RingtoneManager.TYPE_NOTIFICATION)
        fun realDefaults() = types.map { RingtoneManager.getActualDefaultRingtoneUri(context, it)?.toString() }
        val originals = realDefaults()
        val id = UUID.randomUUID().toString()
        val name = "sound-probe-$id.wav"
        val relative = "${Environment.DIRECTORY_MUSIC}/SonglassSoundProbe-$id/"
        val source = resolver.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, name)
            put(MediaStore.Audio.Media.RELATIVE_PATH, relative)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }) ?: error("fixture insert")
        val ownedCopies = mutableSetOf<Uri>()
        val cases = mutableListOf<String>()
        try {
            resolver.openOutputStream(source, "w")!!.use { output ->
                context.assets.open("tag-editor-qa/fixture.wav").use { it.copyTo(output) }
            }
            check(resolver.update(source, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null) == 1)
            var track: Track? = null
            repeat(50) {
                if (track == null) {
                    resolver.query(source, arrayOf(MediaStore.Audio.Media.DATE_ADDED, MediaStore.Audio.Media.DURATION,
                        MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.GENERATION_MODIFIED), null, null, null)?.use { row ->
                        if (row.moveToFirst() && row.getLong(0) > 0 && row.getLong(1) > 0 && !row.isNull(2)) {
                            track = Track("probe-$id", "Default sound probe", source.toString(), durationMs = row.getLong(1),
                                folder = relative.trim('/'), fileName = name, dateAddedMs = row.getLong(0) * 1_000,
                                absoluteFilePath = row.getString(2), artworkRevision = row.getLong(3))
                        }
                    }
                    if (track == null) delay(100)
                }
            }
            val selected = checkNotNull(track) { "fixture metadata unavailable" }
            val slots = mutableMapOf<Int, Uri>()
            var permitted = true
            var commits = 0
            val fake = object : AndroidDefaultSoundStore {
                override fun canWrite() = permitted
                override fun getDefault(type: Int) = slots[type]
                override fun setDefault(type: Int, uri: Uri) { slots[type] = uri; ownedCopies += uri; commits++ }
            }
            val writer = AndroidDefaultSoundWriter(context, fake)
            for (target in DefaultSoundTarget.entries) {
                val result = writer.apply(selected, target) { true }
                check(result == DefaultSoundResult.Applied) { "${target.name} returned $result" }
                cases += "copy-${target.name}"
            }
            check(commits == 2)
            permitted = false
            check(writer.apply(selected, DefaultSoundTarget.Ringtone) { true } == DefaultSoundResult.PermissionRequired)
            check(commits == 2);cases += "permission-denied"
            permitted = true
            check(writer.apply(selected.copy(fileName = "different.wav"), DefaultSoundTarget.Ringtone) { true } == DefaultSoundResult.SourceChanged)
            check(commits == 2);cases += "identity-mismatch"
            check(writer.apply(selected.copy(uri = "content://settings/system/ringtone"), DefaultSoundTarget.Ringtone) { true } == DefaultSoundResult.SourceChanged)
            cases += "alias-rejected"
            var checks = 0
            check(writer.apply(selected, DefaultSoundTarget.Ringtone) { ++checks < 3 } == DefaultSoundResult.SourceChanged)
            check(commits == 2);cases += "request-invalidated-during-copy"
            check(realDefaults() == originals) { "real defaults changed" }
            Log.i("DefaultSoundProbe", JSONObject().put("result", "PASS").put("cases", JSONArray(cases))
                .put("real_defaults_unchanged", true).toString())
        } finally {
            // Only UUID-bound source and newly recorded copies; actual defaults are read-only here.
            check(realDefaults() == originals) { "cleanup stopped: defaults changed" }
            for (uri in ownedCopies) {
                check(uri.toString() !in originals)
                resolver.delete(uri, "${MediaStore.Audio.Media.OWNER_PACKAGE_NAME}=?", arrayOf(context.packageName))
            }
            resolver.delete(source, "${MediaStore.Audio.Media.OWNER_PACKAGE_NAME}=? AND ${MediaStore.Audio.Media.DISPLAY_NAME}=?",
                arrayOf(context.packageName, name))
        }
    }
}

package com.teneeduu.jo.music

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import org.json.JSONArray
import java.io.File

data class Track(val id: String, val title: String, val uri: Uri, val bundled: Boolean)

/**
 * Songs shipped in the APK plus songs the user imported. Imports are copied into
 * Jo's private storage so they keep working after the original file is moved or
 * deleted. The play order is stored separately, keyed by the same
 * "bundled:" / "added:" ids the iOS version uses.
 */
class MusicLibrary(private val context: Context) {

    private val prefs = context.getSharedPreferences("music", Context.MODE_PRIVATE)
    private val folder = File(context.filesDir, "Music").apply { mkdirs() }

    fun load(): List<Track> {
        val bundled = context.assets.list(ASSET_DIR).orEmpty()
            .filter { it.substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS }
            .map { name ->
                Track("bundled:$name", name.substringBeforeLast('.'), Uri.parse("asset:///$ASSET_DIR/$name"), bundled = true)
            }
            .sortedBy { it.title }

        val added = folder.listFiles().orEmpty()
            .filter { it.extension.lowercase() in AUDIO_EXTENSIONS }
            .map { file -> Track("added:${file.name}", file.nameWithoutExtension, Uri.fromFile(file), bundled = false) }
            .sortedBy { it.title }

        val all = bundled + added
        val byId = all.associateBy { it.id }
        val saved = savedOrder()
        // Keep the user's order; anything new goes to the end.
        val ordered = saved.mapNotNull { byId[it] } + all.filter { it.id !in saved }
        saveOrder(ordered)
        return ordered
    }

    fun saveOrder(tracks: List<Track>) {
        prefs.edit().putString(ORDER_KEY, JSONArray(tracks.map { it.id }).toString()).apply()
    }

    /** Copies one picked file in; returns its track id, or null if it isn't usable audio. */
    fun import(uri: Uri): String? {
        val resolver = context.contentResolver
        val displayName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            ?: return null

        var name = displayName.substringAfterLast('/').trim()
        if (name.isEmpty()) return null
        if (name.substringAfterLast('.', "").lowercase() !in AUDIO_EXTENSIONS) {
            val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(resolver.getType(uri))
                ?.lowercase()
                ?.takeIf { it in AUDIO_EXTENSIONS }
                ?: return null
            name = "$name.$extension"
        }

        val target = File(folder, name)
        resolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        return "added:$name"
    }

    fun delete(track: Track) {
        if (!track.bundled) track.uri.path?.let { File(it).delete() }
    }

    private fun savedOrder(): List<String> {
        val raw = prefs.getString(ORDER_KEY, null) ?: return emptyList()
        val array = JSONArray(raw)
        return (0 until array.length()).map { array.getString(it) }
    }

    private companion object {
        const val ASSET_DIR = "music"
        const val ORDER_KEY = "order"
        val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "wav", "flac", "ogg")
    }
}

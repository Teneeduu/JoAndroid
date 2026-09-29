package com.teneeduu.jo.music

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class NowPlaying(
    val trackId: String? = null,
    val title: String? = null,
    val isPlaying: Boolean = false,
)

/**
 * The UI side of the music player. The library order is the source of truth;
 * while the playing queue still mirrors it, edits are applied to the queue in
 * place so playback never hiccups. Otherwise the queue is rebuilt on next play.
 */
class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val library = MusicLibrary(application)

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val _nowPlaying = MutableStateFlow(NowPlaying())
    val nowPlaying: StateFlow<NowPlaying> = _nowPlaying.asStateFlow()

    private var controller: MediaController? = null
    private val controllerFuture = MediaController.Builder(
        application,
        SessionToken(application, ComponentName(application, PlaybackService::class.java)),
    ).buildAsync()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish(player)
    }

    init {
        reload()
        controllerFuture.addListener(
            {
                runCatching { controllerFuture.get() }.onSuccess { connected ->
                    controller = connected
                    connected.addListener(listener)
                    publish(connected)
                }
            },
            ContextCompat.getMainExecutor(application),
        )
    }

    fun reload() {
        viewModelScope.launch {
            _tracks.value = withContext(Dispatchers.IO) { library.load() }
        }
    }

    fun play(track: Track) {
        val player = controller ?: return
        val index = _tracks.value.indexOfFirst { it.id == track.id }
        if (index < 0) return
        if (queueMatches(player)) {
            player.seekTo(index, 0)
        } else {
            player.setMediaItems(_tracks.value.map { it.toMediaItem() }, index, 0)
        }
        player.prepare()
        player.play()
    }

    fun toggle() {
        val player = controller ?: return
        when {
            player.isPlaying -> player.pause()
            player.mediaItemCount > 0 && queueMatches(player) -> {
                player.prepare()
                player.play()
            }
            else -> _tracks.value.firstOrNull()?.let(::play)
        }
    }

    fun next() {
        val player = controller ?: return
        if (player.mediaItemCount == 0) _tracks.value.firstOrNull()?.let(::play) else player.seekToNextMediaItem()
    }

    fun previous() {
        val player = controller ?: return
        if (player.mediaItemCount == 0) _tracks.value.lastOrNull()?.let(::play) else player.seekToPreviousMediaItem()
    }

    fun stop() {
        val player = controller ?: return
        player.stop()
        player.clearMediaItems()
    }

    fun move(index: Int, delta: Int) {
        val target = index + delta
        val list = _tracks.value.toMutableList()
        if (index !in list.indices || target !in list.indices) return

        val player = controller
        val mirrored = player != null && queueMatches(player)
        list.add(target, list.removeAt(index))
        _tracks.value = list
        library.saveOrder(list)
        if (mirrored) player?.moveMediaItem(index, target)
    }

    fun remove(track: Track) {
        if (track.bundled) return
        val index = _tracks.value.indexOfFirst { it.id == track.id }
        if (index < 0) return

        val player = controller
        val mirrored = player != null && queueMatches(player)
        val list = _tracks.value.toMutableList().apply { removeAt(index) }
        _tracks.value = list
        library.saveOrder(list)
        if (mirrored) player?.removeMediaItem(index)
        viewModelScope.launch(Dispatchers.IO) { library.delete(track) }
    }

    fun import(uris: List<Uri>) {
        viewModelScope.launch {
            val player = controller
            val mirrored = player != null && queueMatches(player)
            val before = _tracks.value.map { it.id }.toSet()

            val fresh = withContext(Dispatchers.IO) {
                uris.forEach { library.import(it) }
                library.load()
            }
            _tracks.value = fresh

            val added = fresh.filter { it.id !in before }
            if (mirrored && added.isNotEmpty()) player?.addMediaItems(added.map { it.toMediaItem() })
        }
    }

    private fun queueMatches(player: Player): Boolean {
        val tracks = _tracks.value
        if (player.mediaItemCount != tracks.size) return false
        return tracks.indices.all { player.getMediaItemAt(it).mediaId == tracks[it].id }
    }

    private fun publish(player: Player) {
        val item = player.currentMediaItem
        _nowPlaying.value = NowPlaying(
            trackId = item?.mediaId,
            title = item?.mediaMetadata?.title?.toString(),
            isPlaying = player.isPlaying,
        )
    }

    private fun Track.toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setUri(uri)
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist("Jo").build())
        .build()

    override fun onCleared() {
        controller?.removeListener(listener)
        MediaController.releaseFuture(controllerFuture)
        super.onCleared()
    }
}

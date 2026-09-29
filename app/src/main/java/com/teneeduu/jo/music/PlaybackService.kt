package com.teneeduu.jo.music

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.teneeduu.jo.MainActivity

/**
 * Hosts the player in a foreground service so music keeps going with the screen
 * off, and gives the lock screen, notification shade, headphones and Bluetooth
 * their controls — the Android counterpart of iOS background audio plus Now Playing.
 */
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            // Pause when headphones are pulled out instead of blasting the speaker.
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply { repeatMode = Player.REPEAT_MODE_ALL }

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setCallback(ResolveOwnItems())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    // Swiping Jo away from recents keeps the music going, like on iOS; only an
    // idle player lets the service go.
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    /**
     * Media item URIs are stripped when a controller hands items to the session,
     * so they travel in requestMetadata and get restored here — but only for Jo's
     * own UI, so no other app can make Jo open arbitrary files.
     */
    private inner class ResolveOwnItems : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            if (controller.packageName != packageName) {
                return Futures.immediateFuture(mutableListOf())
            }
            val resolved = mediaItems
                .map { item -> item.buildUpon().setUri(item.requestMetadata.mediaUri).build() }
                .toMutableList()
            return Futures.immediateFuture(resolved)
        }
    }
}

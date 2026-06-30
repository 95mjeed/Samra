package com.samra.downloader.playback

import android.content.Intent
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/** Hosts the ExoPlayer + MediaSession so audio plays in the background with
 *  a media notification and lockscreen controls. */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setHandleAudioBecomingNoisy(true)
            .build()
        session = MediaSession.Builder(this, player)
            .setCallback(AllowlistCallback(packageName))
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}

/**
 * Restricts who may connect to the media session (security review M2): this app itself plus
 * trusted system media controllers (system UI notification/lockscreen, Android Auto, Assistant,
 * Bluetooth/AVRCP, Wear). Unknown third-party apps are rejected so they can't read now-playing
 * metadata or drive playback. Kept permissive for system components so device controls keep working.
 */
private class AllowlistCallback(private val selfPkg: String) : MediaSession.Callback {
    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): MediaSession.ConnectionResult {
        val pkg = controller.packageName
        val trusted = pkg == selfPkg ||
            pkg == "com.android.systemui" ||
            pkg == "com.android.bluetooth" ||
            pkg == "com.google.android.projection.gearhead" ||      // Android Auto
            pkg == "com.google.android.googlequicksearchbox" ||     // Assistant / Google
            pkg == "com.google.android.wearable.app" ||             // Wear OS
            pkg == "com.google.android.apps.wearables.maestro.companion"
            // NOTE: no broad `com.android.*` prefix match — package-name prefixes are NOT
            // reserved, so any sideloaded app could name itself `com.android.evil` and slip
            // through. The exact system package names above can't be spoofed (the real OS
            // owns them), and SystemUI/Bluetooth lockscreen controls use those exact names.
        return if (trusted) super.onConnect(session, controller)
        else MediaSession.ConnectionResult.reject()
    }
}

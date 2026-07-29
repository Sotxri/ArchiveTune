/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.sonos

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import moe.rukamori.archivetune.MainActivity
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.playback.MusicService
import timber.log.Timber
import java.io.IOException

@AndroidEntryPoint
class AudioStreamingService : Service() {

    private var audioServer: SonosAudioServer? = null
    private var musicService: MusicService? = null
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.service
            isBound = true
            Timber.d("Bound to MusicService")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
            isBound = false
            Timber.d("Unbound from MusicService")
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.e("AudioStreamingService", "!!! AudioStreamingService CREATED !!!")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        bindService(
            Intent(this, MusicService::class.java),
            connection,
            BIND_AUTO_CREATE
        )

        startServer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopServer()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startServer() {
        val localIp = moe.rukamori.archivetune.utils.getLocalIpv4Address()
        Log.e("AudioStreamingService", "!!! Starting server on $localIp:$SERVER_PORT !!!")

        audioServer = SonosAudioServer(SERVER_PORT) {
            musicService?.let { service ->
                val resolvedUrl = service.getCurrentResolvedStreamUrl() ?: return@let null
                
                SonosAudioServer.TrackInfo(
                    url = resolvedUrl,
                    mimeType = service.getCurrentMimeType()
                )
            }
        }

        try {
            audioServer?.start()
            Timber.i("Sonos Audio Server started on port $SERVER_PORT")
        } catch (e: IOException) {
            Timber.e(e, "Failed to start Sonos Audio Server")
        }
    }

    private fun stopServer() {
        audioServer?.stop()
        audioServer = null
        Timber.i("Sonos Audio Server stopped")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Sonos Streaming Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Streaming to Sonos")
            .setContentText("Local audio server is running")
            .setSmallIcon(R.drawable.small_icon)
            .setContentIntent(pendingIntent)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 889
        private const val CHANNEL_ID = "sonos_streaming_channel"
        private const val SERVER_PORT = 8080
    }
}

package com.hooandee.colores.effects

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.hooandee.colores.ColoresApplication
import com.hooandee.colores.MainActivity
import com.hooandee.colores.R
import com.hooandee.colores.audio.AndroidPlaybackCapture
import com.hooandee.colores.audio.AudioCaptureStatus
import kotlinx.coroutines.launch

class EffectsService : Service() {
    private var projection: MediaProjection? = null
    private var projectionCallback: MediaProjection.Callback? = null
    private var audioForegroundActive = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val command = resolveEffectsServiceCommand(intent != null, intent?.action)
        val policy = effectsServiceCommandPolicy(command)
        Log.i(TAG, "start command=$command action=${intent?.action}")
        application.effectsServiceGate.onServiceStarted()
        when (command) {
            EffectsServiceCommand.START_AUDIO -> startAudioCapture(requireNotNull(intent))
            EffectsServiceCommand.STOP_AUDIO -> {
                requireNotNull(intent)
                startForegroundCompat(mediaProjection = false)
                stopAudioCapture(intent.audioStopStatus(), reconcile = policy.reconcileController)
            }
            EffectsServiceCommand.RESTORE -> {
                startForegroundCompat(mediaProjection = false)
                application.applicationScope.launch {
                    if (!application.restoreRuntime()) stopSelf(startId)
                }
            }
            EffectsServiceCommand.KEEP_ALIVE -> startForegroundCompat(mediaProjection = audioForegroundActive)
        }
        return START_STICKY
    }

    override fun onTimeout(startId: Int) {
        stopAudioCapture(AudioCaptureStatus.AUTHORIZATION_REQUIRED)
        stopSelf()
    }

    override fun onDestroy() {
        val currentStatus = application.audioLevelSource.state.value.status
        val terminalStatus =
            currentStatus.takeIf { it == AudioCaptureStatus.ERROR || it == AudioCaptureStatus.REVOKED }
                ?: AudioCaptureStatus.AUTHORIZATION_REQUIRED
        Log.i(TAG, "destroy status=$terminalStatus")
        stopAudioCapture(terminalStatus, reconcile = false)
        application.effectsServiceGate.onServiceStopped()
        super.onDestroy()
    }

    private fun startForegroundCompat(mediaProjection: Boolean) {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val type =
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                    if (mediaProjection) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION else 0
            startForeground(NOTIFICATION_ID, notification, type)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startAudioCapture(intent: Intent) {
        stopAudioCapture(AudioCaptureStatus.STARTING)
        audioForegroundActive = true
        startForegroundCompat(mediaProjection = true)
        val resultData = intent.projectionData()
        if (resultData == null || intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED) != Activity.RESULT_OK) {
            stopAudioCapture(AudioCaptureStatus.AUTHORIZATION_REQUIRED)
            return
        }
        runCatching {
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val active = manager.getMediaProjection(Activity.RESULT_OK, resultData) ?: error("MediaProjection unavailable")
            val callback =
                object : MediaProjection.Callback() {
                    override fun onStop() {
                        stopAudioCapture(AudioCaptureStatus.REVOKED, stopProjection = false)
                        stopSelf()
                    }
                }
            active.registerCallback(callback, Handler(Looper.getMainLooper()))
            projection = active
            projectionCallback = callback
            application.audioCaptureSession.start(AndroidPlaybackCapture(this, active)) { error ->
                Log.e(TAG, "audio capture failed", error)
                stopAudioCapture(AudioCaptureStatus.ERROR)
                stopSelf()
            }
            Log.i(TAG, "audio capture started")
        }.onFailure {
            Log.e(TAG, "audio capture setup failed", it)
            stopAudioCapture(AudioCaptureStatus.ERROR)
            stopSelf()
        }
    }

    private fun stopAudioCapture(
        status: AudioCaptureStatus,
        stopProjection: Boolean = true,
        reconcile: Boolean = true,
    ) {
        Log.i(TAG, "stop audio status=$status projection=${projection != null}")
        audioForegroundActive = false
        application.audioCaptureSession.stop(status)
        val active = projection
        val callback = projectionCallback
        projection = null
        projectionCallback = null
        if (active != null && callback != null) runCatching { active.unregisterCallback(callback) }
        if (stopProjection) runCatching { active?.stop() }
        if (shouldReconcileAudioController(reconcile, application.lightingController.snapshot.value.mode)) {
            application.lightingController.onAudioStateChanged()
        }
    }

    private fun buildNotification(): Notification {
        ensureChannel()
        val contentIntent =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val builder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(this, CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(this)
            }
        return builder
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(getString(R.string.service_notification_text))
            .setSmallIcon(R.drawable.ic_stat_colores)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel =
            NotificationChannel(CHANNEL_ID, getString(R.string.service_channel_name), NotificationManager.IMPORTANCE_LOW)
                .apply { description = getString(R.string.service_channel_description) }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_PROJECTION_DATA = "projection_data"
        private const val EXTRA_STOP_STATUS = "stop_status"
        private const val CHANNEL_ID = "colores_effects"
        private const val NOTIFICATION_ID = 1001
        private const val TAG = "ColoresEffects"

        fun startAudio(
            context: Context,
            resultCode: Int,
            resultData: Intent,
        ) {
            startService(
                context,
                Intent(context, EffectsService::class.java)
                    .setAction(ACTION_START_AUDIO)
                    .putExtra(EXTRA_RESULT_CODE, resultCode)
                    .putExtra(EXTRA_PROJECTION_DATA, resultData),
            )
        }

        fun stopAudio(
            context: Context,
            status: AudioCaptureStatus = AudioCaptureStatus.AUTHORIZATION_REQUIRED,
        ) {
            startService(
                context,
                Intent(context, EffectsService::class.java)
                    .setAction(ACTION_STOP_AUDIO)
                    .putExtra(EXTRA_STOP_STATUS, status.name),
            )
        }

        fun restore(context: Context) {
            startService(context, Intent(context, EffectsService::class.java).setAction(ACTION_RESTORE))
        }

        private fun startService(context: Context, intent: Intent) {
            val command = resolveEffectsServiceCommand(intentPresent = true, action = intent.action)
            val policy = effectsServiceCommandPolicy(command)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && policy.startMode == EffectsServiceStartMode.FOREGROUND) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private val application: ColoresApplication
        get() = getApplication() as ColoresApplication

    @Suppress("DEPRECATION")
    private fun Intent.projectionData(): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(EXTRA_PROJECTION_DATA, Intent::class.java)
        } else {
            getParcelableExtra(EXTRA_PROJECTION_DATA)
        }

    private fun Intent.audioStopStatus(): AudioCaptureStatus =
        runCatching { AudioCaptureStatus.valueOf(getStringExtra(EXTRA_STOP_STATUS).orEmpty()) }
            .getOrDefault(AudioCaptureStatus.AUTHORIZATION_REQUIRED)
}

package com.hooandee.colores.effects

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.Activity
import android.content.Context
import android.content.Intent
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
import com.hooandee.colores.ambient.AmbientCaptureConfig
import com.hooandee.colores.ambient.AmbientCaptureStatus
import com.hooandee.colores.ambient.AmbientSamplingMode
import com.hooandee.colores.audio.AndroidPlaybackCapture
import com.hooandee.colores.audio.AudioCaptureStatus
import com.hooandee.colores.device.LedGridCell
import com.hooandee.colores.control.AppMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class EffectsService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var projection: MediaProjection? = null
    private var projectionCallback: MediaProjection.Callback? = null
    private var projectionOwner: ProjectionOwner? = null

    override fun onCreate() {
        super.onCreate()
        serviceScope.launch {
            application.lightingController.snapshot.collect { snapshot ->
                when {
                    projectionOwner == ProjectionOwner.AUDIO && snapshot.mode != AppMode.AUDIO ->
                        stopAudioCapture(AudioCaptureStatus.AUTHORIZATION_REQUIRED, reconcile = false)
                    projectionOwner == ProjectionOwner.AMBIENT && snapshot.mode != AppMode.AMBIENT ->
                        stopAmbientCapture(AmbientCaptureStatus.AUTHORIZATION_REQUIRED, reconcile = false)
                }
            }
        }
    }

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
            EffectsServiceCommand.START_AMBIENT -> startAmbientCapture(requireNotNull(intent))
            EffectsServiceCommand.STOP_AMBIENT -> {
                requireNotNull(intent)
                startForegroundCompat(mediaProjection = false)
                stopAmbientCapture(intent.ambientStopStatus(), reconcile = policy.reconcileController)
            }
            EffectsServiceCommand.UPDATE_AMBIENT -> intent?.ambientConfig()?.let(application.ambientCaptureSession::updateConfig)
            EffectsServiceCommand.RESTORE -> {
                startForegroundCompat(mediaProjection = false)
                application.applicationScope.launch {
                    if (!application.restoreRuntime()) stopSelf(startId)
                }
            }
            EffectsServiceCommand.KEEP_ALIVE -> startForegroundCompat(mediaProjection = projectionOwner != null)
        }
        return START_STICKY
    }

    override fun onTimeout(startId: Int) {
        stopAudioCapture(AudioCaptureStatus.AUTHORIZATION_REQUIRED)
        stopAmbientCapture(AmbientCaptureStatus.AUTHORIZATION_REQUIRED)
        stopSelf()
    }

    override fun onDestroy() {
        val currentStatus = application.audioLevelSource.state.value.status
        val terminalStatus =
            currentStatus.takeIf { it == AudioCaptureStatus.ERROR || it == AudioCaptureStatus.REVOKED }
                ?: AudioCaptureStatus.AUTHORIZATION_REQUIRED
        Log.i(TAG, "destroy status=$terminalStatus")
        stopAudioCapture(terminalStatus, reconcile = false)
        val ambientStatus = application.ambientFrameSource.state.value.status
        val ambientTerminal =
            ambientStatus.takeIf { it == AmbientCaptureStatus.ERROR || it == AmbientCaptureStatus.REVOKED }
                ?: AmbientCaptureStatus.AUTHORIZATION_REQUIRED
        stopAmbientCapture(ambientTerminal, reconcile = false)
        serviceScope.cancel()
        application.effectsServiceGate.onServiceStopped()
        super.onDestroy()
    }

    private fun startForegroundCompat(mediaProjection: Boolean) {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val type = foregroundServiceTypes(Build.VERSION.SDK_INT, mediaProjection)
            startForeground(NOTIFICATION_ID, notification, type)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startAudioCapture(intent: Intent) {
        stopAmbientCapture(AmbientCaptureStatus.AUTHORIZATION_REQUIRED)
        stopAudioCapture(AudioCaptureStatus.STARTING)
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
                        if (projection === active && projectionOwner == ProjectionOwner.AUDIO) {
                            stopAudioCapture(AudioCaptureStatus.REVOKED, stopProjection = false)
                            stopSelf()
                        }
                    }
                }
            active.registerCallback(callback, Handler(Looper.getMainLooper()))
            projectionOwner = ProjectionOwner.AUDIO
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
        application.audioCaptureSession.stop(status)
        if (projectionOwner == ProjectionOwner.AUDIO) {
            val active = projection
            val callback = projectionCallback
            projection = null
            projectionCallback = null
            projectionOwner = null
            if (active != null && callback != null) runCatching { active.unregisterCallback(callback) }
            if (stopProjection) runCatching { active?.stop() }
        }
        if (shouldReconcileAudioController(reconcile, application.lightingController.snapshot.value.mode)) {
            application.lightingController.onAudioStateChanged()
        }
    }

    private fun startAmbientCapture(intent: Intent) {
        stopAudioCapture(AudioCaptureStatus.AUTHORIZATION_REQUIRED)
        stopAmbientCapture(AmbientCaptureStatus.STARTING)
        startForegroundCompat(mediaProjection = true)
        val resultData = intent.projectionData()
        val config = intent.ambientConfig()
        if (resultData == null || config == null || intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED) != Activity.RESULT_OK) {
            stopAmbientCapture(AmbientCaptureStatus.AUTHORIZATION_REQUIRED)
            return
        }
        runCatching {
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val active = manager.getMediaProjection(Activity.RESULT_OK, resultData) ?: error("MediaProjection unavailable")
            val callback =
                object : MediaProjection.Callback() {
                    override fun onStop() {
                        if (projection === active && projectionOwner == ProjectionOwner.AMBIENT) {
                            stopAmbientCapture(AmbientCaptureStatus.REVOKED, stopProjection = false)
                            stopSelf()
                        }
                    }
                }
            active.registerCallback(callback, Handler(Looper.getMainLooper()))
            projectionOwner = ProjectionOwner.AMBIENT
            projection = active
            projectionCallback = callback
            application.ambientCaptureSession.start(active, config) { error ->
                Log.e(TAG, "ambient capture failed", error)
                stopAmbientCapture(AmbientCaptureStatus.ERROR)
                stopSelf()
            }
            Log.i(TAG, "ambient capture started fps=${config.captureFps} mode=${config.samplingMode}")
        }.onFailure {
            Log.e(TAG, "ambient capture setup failed", it)
            stopAmbientCapture(AmbientCaptureStatus.ERROR)
            stopSelf()
        }
    }

    private fun stopAmbientCapture(
        status: AmbientCaptureStatus,
        stopProjection: Boolean = true,
        reconcile: Boolean = true,
    ) {
        application.ambientCaptureSession.stop(status)
        if (projectionOwner == ProjectionOwner.AMBIENT) {
            val active = projection
            val callback = projectionCallback
            projection = null
            projectionCallback = null
            projectionOwner = null
            if (active != null && callback != null) runCatching { active.unregisterCallback(callback) }
            if (stopProjection) runCatching { active?.stop() }
        }
        if (shouldReconcileAmbientController(reconcile, application.lightingController.snapshot.value.mode)) {
            application.lightingController.onAmbientStateChanged()
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
        private const val EXTRA_CAPTURE_FPS = "capture_fps"
        private const val EXTRA_SAMPLING_MODE = "sampling_mode"
        private const val EXTRA_ZONES = "zones"
        private const val EXTRA_PER_ZONE = "per_zone"
        private const val EXTRA_GRID_STICKS = "grid_sticks"
        private const val EXTRA_GRID_ROWS = "grid_rows"
        private const val EXTRA_GRID_COLUMNS = "grid_columns"
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

        fun startAmbient(
            context: Context,
            resultCode: Int,
            resultData: Intent,
            config: AmbientCaptureConfig,
        ) {
            startService(
                context,
                ambientIntent(context, ACTION_START_AMBIENT, config)
                    .putExtra(EXTRA_RESULT_CODE, resultCode)
                    .putExtra(EXTRA_PROJECTION_DATA, resultData),
            )
        }

        fun updateAmbient(
            context: Context,
            config: AmbientCaptureConfig,
        ) {
            startService(context, ambientIntent(context, ACTION_UPDATE_AMBIENT, config))
        }

        fun stopAmbient(
            context: Context,
            status: AmbientCaptureStatus = AmbientCaptureStatus.AUTHORIZATION_REQUIRED,
        ) {
            startService(
                context,
                Intent(context, EffectsService::class.java)
                    .setAction(ACTION_STOP_AMBIENT)
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

        private fun ambientIntent(
            context: Context,
            action: String,
            config: AmbientCaptureConfig,
        ): Intent {
            val grid = config.gridLayout
            return Intent(context, EffectsService::class.java)
                .setAction(action)
                .putExtra(EXTRA_CAPTURE_FPS, config.captureFps)
                .putExtra(EXTRA_SAMPLING_MODE, config.samplingMode.name)
                .putExtra(EXTRA_ZONES, config.zones)
                .putExtra(EXTRA_PER_ZONE, config.supportsPerZone)
                .putExtra(EXTRA_GRID_STICKS, grid?.map { it.stick ?: -1 }?.toIntArray())
                .putExtra(EXTRA_GRID_ROWS, grid?.map { it.row }?.toIntArray())
                .putExtra(EXTRA_GRID_COLUMNS, grid?.map { it.col }?.toIntArray())
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

    private fun Intent.ambientStopStatus(): AmbientCaptureStatus =
        runCatching { AmbientCaptureStatus.valueOf(getStringExtra(EXTRA_STOP_STATUS).orEmpty()) }
            .getOrDefault(AmbientCaptureStatus.AUTHORIZATION_REQUIRED)

    private fun Intent.ambientConfig(): AmbientCaptureConfig? {
        val zones = getIntExtra(EXTRA_ZONES, 0)
        if (zones <= 0) return null
        val sticks = getIntArrayExtra(EXTRA_GRID_STICKS)
        val rows = getIntArrayExtra(EXTRA_GRID_ROWS)
        val columns = getIntArrayExtra(EXTRA_GRID_COLUMNS)
        val grid =
            if (sticks?.size == zones && rows?.size == zones && columns?.size == zones) {
                List(zones) { index ->
                    LedGridCell(sticks[index].takeIf { it >= 0 }, rows[index], columns[index], position = null)
                }
            } else {
                null
            }
        return AmbientCaptureConfig(
            zones = zones,
            gridLayout = grid,
            supportsPerZone = getBooleanExtra(EXTRA_PER_ZONE, false),
            captureFps = getIntExtra(EXTRA_CAPTURE_FPS, 10),
            samplingMode =
                runCatching { AmbientSamplingMode.valueOf(getStringExtra(EXTRA_SAMPLING_MODE).orEmpty()) }
                    .getOrDefault(AmbientSamplingMode.FULL_SCENE),
        )
    }

    private enum class ProjectionOwner {
        AUDIO,
        AMBIENT,
    }
}

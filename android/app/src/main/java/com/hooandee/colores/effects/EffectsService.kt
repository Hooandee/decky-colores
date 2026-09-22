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
import com.hooandee.colores.ambient.keepsCaptureActive
import com.hooandee.colores.audio.AndroidPlaybackCapture
import com.hooandee.colores.audio.AudioCaptureStatus
import com.hooandee.colores.device.LedGridCell
import com.hooandee.colores.control.ServiceOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class EffectsService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var projectionCallback: MediaProjection.Callback? = null
    private var projectionOwner: CaptureOwner? = null
    private var captureLeaseHeld = false
    private var foreground = false
    private val settler by lazy { EffectsServiceSettler { application.effectsServiceGate.releaseIfUnowned() } }

    override fun onCreate() {
        super.onCreate()
        serviceScope.launch {
            application.lightingController.snapshot.collect { snapshot ->
                val owner = projectionOwner ?: return@collect
                if (snapshot.mode == owner.mode) return@collect
                if (!shouldEndCapture(owner, snapshot.mode, application.profileCoordinator.globalMode())) return@collect
                when (owner) {
                    CaptureOwner.AUDIO -> stopAudioCapture(AudioCaptureStatus.AUTHORIZATION_REQUIRED, reconcile = false)
                    CaptureOwner.AMBIENT -> stopAmbientCapture(AmbientCaptureStatus.AUTHORIZATION_REQUIRED, reconcile = false)
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
        settler.onStartCommand(startId)
        when (command) {
            EffectsServiceCommand.START_AUDIO -> {
                val start = requireNotNull(intent)
                val failure = enterForeground(mediaProjection = start.hasProjectionConsent())
                if (failure == null) {
                    startAudioCapture(start)
                } else {
                    stopAudioCapture(
                        if (foregroundRefusalRequiresAuthorization(failure)) AudioCaptureStatus.AUTHORIZATION_REQUIRED else AudioCaptureStatus.ERROR,
                    )
                    recoverFromForegroundRefusal()
                }
            }
            EffectsServiceCommand.STOP_AUDIO -> {
                stopAudioCapture(requireNotNull(intent).audioStopStatus(), reconcile = policy.reconcileController)
                settle()
            }
            EffectsServiceCommand.START_AMBIENT -> {
                val start = requireNotNull(intent)
                val failure = enterForeground(mediaProjection = start.hasProjectionConsent())
                if (failure == null) {
                    startAmbientCapture(start)
                } else {
                    stopAmbientCapture(
                        if (foregroundRefusalRequiresAuthorization(failure)) AmbientCaptureStatus.AUTHORIZATION_REQUIRED else AmbientCaptureStatus.ERROR,
                    )
                    recoverFromForegroundRefusal()
                }
            }
            EffectsServiceCommand.STOP_AMBIENT -> {
                stopAmbientCapture(requireNotNull(intent).ambientStopStatus(), reconcile = policy.reconcileController)
                settle()
            }
            EffectsServiceCommand.UPDATE_AMBIENT -> {
                intent?.ambientConfig()?.let(application.ambientCaptureSession::updateConfig)
                settle()
            }
            EffectsServiceCommand.RESTORE -> {
                if (enterForeground(mediaProjection = false) == null) restore() else recoverFromForegroundRefusal()
            }
            EffectsServiceCommand.KEEP_ALIVE -> {
                if (enterForeground(mediaProjection = projectionOwner != null) == null) settle() else recoverFromForegroundRefusal()
            }
        }
        return START_STICKY
    }

    override fun onTimeout(startId: Int) {
        stopAudioCapture(AudioCaptureStatus.AUTHORIZATION_REQUIRED, releaseLease = false)
        stopAmbientCapture(AmbientCaptureStatus.AUTHORIZATION_REQUIRED, releaseLease = false)
        stopSelf()
    }

    override fun onDestroy() {
        val currentStatus = application.audioLevelSource.state.value.status
        val terminalStatus =
            currentStatus.takeIf { it == AudioCaptureStatus.ERROR || it == AudioCaptureStatus.REVOKED }
                ?: AudioCaptureStatus.AUTHORIZATION_REQUIRED
        Log.i(TAG, "destroy status=$terminalStatus")
        stopAudioCapture(terminalStatus, reconcile = false, releaseLease = false)
        val ambientStatus = application.ambientFrameSource.state.value.status
        val ambientTerminal =
            ambientStatus.takeIf { it == AmbientCaptureStatus.ERROR || it == AmbientCaptureStatus.REVOKED }
                ?: AmbientCaptureStatus.AUTHORIZATION_REQUIRED
        stopAmbientCapture(ambientTerminal, reconcile = false, releaseLease = false)
        mainHandler.removeCallbacksAndMessages(null)
        serviceScope.cancel()
        captureLeaseHeld = false
        foreground = false
        application.effectsServiceGate.onServiceStopped()
        super.onDestroy()
    }

    private fun enterForeground(mediaProjection: Boolean): Throwable? =
        runCatching { startForegroundCompat(mediaProjection) }
            .fold(
                onSuccess = {
                    foreground = true
                    application.effectsServiceGate.onServiceStarted()
                    null
                },
                onFailure = {
                    Log.w(TAG, "foreground refused projection=$mediaProjection", it)
                    it
                },
            )

    private fun recoverFromForegroundRefusal() {
        if (foreground) {
            settle()
            return
        }
        application.effectsServiceGate.onForegroundRefused()
        stopSelf()
    }

    private fun restore() {
        settler.beginRestore()
        val restoring =
            application.applicationScope.async {
                runCatching {
                    application.restoreRuntime()
                    application.lightingController.awaitIdle()
                }.onFailure { if (it is CancellationException) throw it else Log.e(TAG, "restore failed", it) }
            }
        serviceScope.launch {
            runCatching { restoring.await() }
            settler.endRestore()?.let(::stopWhenUnowned)
        }
    }

    private fun settle() {
        settler.settle()?.let(::stopWhenUnowned)
    }

    private fun stopWhenUnowned(latestStartId: Int) {
        Log.i(TAG, "no consumers, stopping")
        stopSelf(latestStartId)
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

    private fun acquireCaptureLease() {
        if (captureLeaseHeld) return
        captureLeaseHeld = true
        application.effectsServiceGate.setRequired(ServiceOwner.CAPTURE, true)
    }

    private fun releaseCaptureLease() {
        if (!captureLeaseHeld) return
        captureLeaseHeld = false
        val gate = application.effectsServiceGate
        gate.setRequired(ServiceOwner.CAPTURE, false)
        if (foreground && gate.hasOwners()) {
            runCatching { startForegroundCompat(mediaProjection = false) }
                .onFailure { Log.w(TAG, "foreground downgrade failed", it) }
        }
    }

    private fun postOnMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else mainHandler.post(action)
    }

    private fun startAudioCapture(intent: Intent) {
        acquireCaptureLease()
        stopAmbientCapture(AmbientCaptureStatus.AUTHORIZATION_REQUIRED, releaseLease = false)
        stopAudioCapture(AudioCaptureStatus.STARTING, releaseLease = false)
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
                        postOnMain {
                            if (projection === active && projectionOwner == CaptureOwner.AUDIO) {
                                stopAudioCapture(AudioCaptureStatus.REVOKED, stopProjection = false)
                            }
                        }
                    }
                }
            active.registerCallback(callback, mainHandler)
            projectionOwner = CaptureOwner.AUDIO
            projection = active
            projectionCallback = callback
            application.audioCaptureSession.start(AndroidPlaybackCapture(this, active)) { error ->
                Log.e(TAG, "audio capture failed", error)
                postOnMain {
                    if (projection === active && projectionOwner == CaptureOwner.AUDIO) {
                        stopAudioCapture(AudioCaptureStatus.ERROR)
                    }
                }
            }
            Log.i(TAG, "audio capture started")
        }.onFailure {
            Log.e(TAG, "audio capture setup failed", it)
            stopAudioCapture(AudioCaptureStatus.ERROR)
        }
    }

    private fun stopAudioCapture(
        status: AudioCaptureStatus,
        stopProjection: Boolean = true,
        reconcile: Boolean = true,
        releaseLease: Boolean = true,
    ) {
        Log.i(TAG, "stop audio status=$status projection=${projection != null}")
        application.audioCaptureSession.stop(status)
        if (projectionOwner == CaptureOwner.AUDIO) {
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
        if (releaseLease && projectionOwner == null) releaseCaptureLease()
    }

    private fun startAmbientCapture(intent: Intent) {
        acquireCaptureLease()
        stopAudioCapture(AudioCaptureStatus.AUTHORIZATION_REQUIRED, releaseLease = false)
        stopAmbientCapture(AmbientCaptureStatus.STARTING, releaseLease = false)
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
                        postOnMain {
                            if (projection === active && projectionOwner == CaptureOwner.AMBIENT) {
                                stopAmbientCapture(AmbientCaptureStatus.REVOKED, stopProjection = false)
                            }
                        }
                    }
                }
            active.registerCallback(callback, mainHandler)
            projectionOwner = CaptureOwner.AMBIENT
            projection = active
            projectionCallback = callback
            application.ambientCaptureSession.start(active, config) { error ->
                Log.e(TAG, "ambient capture failed", error)
                postOnMain {
                    if (projection === active && projectionOwner == CaptureOwner.AMBIENT) {
                        stopAmbientCapture(AmbientCaptureStatus.ERROR)
                    }
                }
            }
            Log.i(TAG, "ambient capture started fps=${config.captureFps} mode=${config.samplingMode}")
        }.onFailure {
            Log.e(TAG, "ambient capture setup failed", it)
            stopAmbientCapture(AmbientCaptureStatus.ERROR)
        }
    }

    private fun stopAmbientCapture(
        status: AmbientCaptureStatus,
        stopProjection: Boolean = true,
        reconcile: Boolean = true,
        releaseLease: Boolean = true,
    ) {
        application.ambientCaptureSession.stop(status)
        if (projectionOwner == CaptureOwner.AMBIENT) {
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
        if (releaseLease && projectionOwner == null) releaseCaptureLease()
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
            val application = context.applicationContext as ColoresApplication
            val live = application.audioLevelSource.state.value.status.let {
                it == AudioCaptureStatus.STARTING || it == AudioCaptureStatus.CAPTURING || it == AudioCaptureStatus.NO_AUDIO
            }
            if (!shouldDispatchCaptureStop(application.effectsServiceGate.active, live)) return
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
            val application = context.applicationContext as ColoresApplication
            val live = application.ambientFrameSource.state.value.status.keepsCaptureActive
            if (!shouldDispatchCaptureStop(application.effectsServiceGate.active, live)) return
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
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && policy.startMode == EffectsServiceStartMode.FOREGROUND) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }.onFailure { Log.w(TAG, "command ${intent.action} refused", it) }
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
    private fun Intent.hasProjectionConsent(): Boolean =
        hasProjectionConsent(
            resultCode = getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED),
            resultDataPresent = projectionData() != null,
            okCode = Activity.RESULT_OK,
        )

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

}

package com.hooandee.colores.effects

import android.content.pm.PackageManager
import android.Manifest
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
import com.hooandee.colores.report.ColoresLog
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
import com.hooandee.colores.control.keepsAudioCaptureActive
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
    private var hold: CaptureHold? = null
    private var projectionOwner: CaptureOwner? = null
    private val releasePausedHold = Runnable { releaseHold(reconcile = true) }
    private var captureLeaseHeld = false
    private var foreground = false
    private val settler by lazy { EffectsServiceSettler { application.effectsServiceGate.releaseIfUnowned() } }

    override fun onCreate() {
        super.onCreate()
        serviceScope.launch {
            application.lightingController.snapshot.collect { snapshot ->
                val held = hold ?: return@collect
                val action =
                    captureHoldAction(
                        owner = projectionOwner,
                        fullScreen = held.fullScreen,
                        mode = snapshot.mode,
                        globalMode = application.profileCoordinator.globalMode(),
                        power = snapshot.effectivePower,
                    )
                applyHoldAction(action)
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
        ColoresLog.i(TAG, "start command=$command action=${intent?.action}")
        settler.onStartCommand(startId)
        when (command) {
            EffectsServiceCommand.START_AUDIO ->
                startCaptureInForeground(requireNotNull(intent), ::startAudioCapture) { requiresAuthorization ->
                    stopAudioCapture(if (requiresAuthorization) AudioCaptureStatus.AUTHORIZATION_REQUIRED else AudioCaptureStatus.ERROR)
                }
            EffectsServiceCommand.STOP_AUDIO -> {
                stopAudioCapture(requireNotNull(intent).audioStopStatus(), reconcile = policy.reconcileController)
                settle()
            }
            EffectsServiceCommand.START_AMBIENT ->
                startCaptureInForeground(requireNotNull(intent), ::startAmbientCapture) { requiresAuthorization ->
                    stopAmbientCapture(if (requiresAuthorization) AmbientCaptureStatus.AUTHORIZATION_REQUIRED else AmbientCaptureStatus.ERROR)
                }
            EffectsServiceCommand.STOP_AMBIENT -> {
                stopAmbientCapture(requireNotNull(intent).ambientStopStatus(), reconcile = policy.reconcileController)
                settle()
            }
            EffectsServiceCommand.UPDATE_AMBIENT -> {
                intent?.ambientConfig()?.let {
                    application.lastAmbientConfig = it
                    application.ambientCaptureSession.updateConfig(it)
                    syncAmbientAudio()
                }
                settle()
            }
            EffectsServiceCommand.RESTORE -> {
                if (enterForeground(mediaProjection = false) == null) restore() else recoverFromForegroundRefusal()
            }
            EffectsServiceCommand.KEEP_ALIVE -> {
                if (enterForeground(mediaProjection = hold != null) == null) settle() else recoverFromForegroundRefusal()
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
        ColoresLog.i(TAG, "destroy status=$terminalStatus")
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
                    ColoresLog.w(TAG, "foreground refused projection=$mediaProjection", it)
                    it
                },
            )

    private fun startCaptureInForeground(
        intent: Intent,
        startCapture: (Intent) -> Unit,
        stopRefusedCapture: (requiresAuthorization: Boolean) -> Unit,
    ) {
        val failure = enterForeground(mediaProjection = intent.hasProjectionConsent())
        if (failure == null) {
            startCapture(intent)
        } else {
            stopRefusedCapture(foregroundRefusalRequiresAuthorization(failure))
            recoverFromForegroundRefusal()
        }
    }

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
                }.onFailure { if (it is CancellationException) throw it else ColoresLog.e(TAG, "restore failed", it) }
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
        ColoresLog.i(TAG, "no consumers, stopping")
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
                .onFailure { ColoresLog.w(TAG, "foreground downgrade failed", it) }
        }
    }

    private fun postOnMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else mainHandler.post(action)
    }

    private fun startAudioCapture(intent: Intent) {
        acquireCaptureLease()
        releaseHold(reconcile = false, releaseLease = false)
        application.audioCaptureSession.stop(AudioCaptureStatus.STARTING)
        val resultData = intent.projectionData()
        if (resultData == null || intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED) != Activity.RESULT_OK) {
            stopAudioCapture(AudioCaptureStatus.AUTHORIZATION_REQUIRED)
            return
        }
        runCatching {
            openHold(resultData, fullScreen = false)
            resumeAudio()
        }.onFailure {
            ColoresLog.e(TAG, "audio capture setup failed", it)
            stopAudioCapture(AudioCaptureStatus.ERROR)
        }
    }

    private fun startAmbientCapture(intent: Intent) {
        acquireCaptureLease()
        releaseHold(reconcile = false, releaseLease = false)
        application.ambientCaptureSession.stop(AmbientCaptureStatus.STARTING)
        val resultData = intent.projectionData()
        val config = intent.ambientConfig()
        if (resultData == null || config == null || intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED) != Activity.RESULT_OK) {
            stopAmbientCapture(AmbientCaptureStatus.AUTHORIZATION_REQUIRED)
            return
        }
        application.lastAmbientConfig = config
        runCatching {
            openHold(resultData, fullScreen = true)
            resumeAmbient()
        }.onFailure {
            ColoresLog.e(TAG, "ambient capture setup failed", it)
            stopAmbientCapture(AmbientCaptureStatus.ERROR)
        }
    }

    private fun openHold(
        resultData: Intent,
        fullScreen: Boolean,
    ) {
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val active = manager.getMediaProjection(Activity.RESULT_OK, resultData) ?: error("MediaProjection unavailable")
        val callback =
            object : MediaProjection.Callback() {
                override fun onStop() {
                    postOnMain { if (hold?.projection === active) onProjectionRevoked() }
                }
            }
        active.registerCallback(callback, mainHandler)
        hold = CaptureHold(active, callback, fullScreen)
    }

    private fun applyHoldAction(action: CaptureHoldAction) {
        when (action) {
            CaptureHoldAction.KEEP -> Unit
            CaptureHoldAction.PAUSE -> pauseHold()
            CaptureHoldAction.RESUME_AUDIO -> resumeAudio()
            CaptureHoldAction.RESUME_AMBIENT -> resumeAmbient()
            CaptureHoldAction.RELEASE -> {
                releaseHold(reconcile = false)
                settle()
            }
        }
    }

    private fun resumeAudio() {
        val held = hold ?: return
        mainHandler.removeCallbacks(releasePausedHold)
        stopConsumer()
        projectionOwner = CaptureOwner.AUDIO
        refreshNotification()
        val capture =
            runCatching { AndroidPlaybackCapture(this, held.projection) }.getOrElse {
                ColoresLog.w(TAG, "audio capture unavailable on held projection", it)
                stopAudioCapture(if (it is SecurityException) AudioCaptureStatus.AUTHORIZATION_REQUIRED else AudioCaptureStatus.ERROR)
                return
            }
        application.audioCaptureSession.start(capture) { error ->
            ColoresLog.e(TAG, "audio capture failed", error)
            postOnMain { if (hold === held && projectionOwner == CaptureOwner.AUDIO) stopAudioCapture(AudioCaptureStatus.ERROR) }
        }
        ColoresLog.i(TAG, "audio capture started reused=${held.used}")
        CaptureReminder.cancel(this)
        held.used = true
        application.lightingController.onAudioStateChanged()
    }

    private fun resumeAmbient() {
        val held = hold ?: return
        val session = application.ambientCaptureSession
        val config = application.lastAmbientConfig
        mainHandler.removeCallbacks(releasePausedHold)
        stopConsumer()
        val resumed =
            when {
                session.live -> session.resume(config)
                !held.used && config != null ->
                    runCatching {
                        session.start(held.projection, config) { error ->
                            ColoresLog.e(TAG, "ambient capture failed", error)
                            postOnMain { if (hold === held && projectionOwner == CaptureOwner.AMBIENT) stopAmbientCapture(AmbientCaptureStatus.ERROR) }
                        }
                    }.onFailure { ColoresLog.e(TAG, "ambient capture start failed", it) }.isSuccess
                else -> false
            }
        if (!resumed) {
            releaseHold(reconcile = true)
            return
        }
        projectionOwner = CaptureOwner.AMBIENT
        refreshNotification()
        ColoresLog.i(TAG, "ambient capture started reused=${held.used} fps=${config?.captureFps} mode=${config?.samplingMode} audio=${config?.audio}")
        CaptureReminder.cancel(this)
        held.used = true
        syncAmbientAudio()
        application.lightingController.onAmbientStateChanged()
    }

    private fun syncAmbientAudio() {
        val held = hold ?: return
        if (projectionOwner != CaptureOwner.AMBIENT) return
        val session = application.ambientAudioSession
        val wanted =
            application.lastAmbientConfig?.audio == true &&
                checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        when {
            wanted && !session.running ->
                runCatching { AndroidPlaybackCapture(this, held.projection) }
                    .onSuccess { capture ->
                        session.start(capture) { error ->
                            ColoresLog.w(TAG, "ambient audio failed", error)
                            postOnMain { if (hold === held) application.ambientAudioSession.stop(AudioCaptureStatus.ERROR) }
                        }
                        ColoresLog.i(TAG, "ambient audio started")
                    }.onFailure { ColoresLog.w(TAG, "ambient audio unavailable", it) }
            !wanted && session.running -> {
                session.stop(AudioCaptureStatus.AUTHORIZATION_REQUIRED)
                ColoresLog.i(TAG, "ambient audio stopped")
            }
        }
        refreshNotification()
    }

    private fun pauseHold() {
        if (hold == null || projectionOwner == null) return
        ColoresLog.i(TAG, "capture paused owner=$projectionOwner")
        val owner = projectionOwner
        stopConsumer()
        refreshNotification()
        when (owner) {
            CaptureOwner.AUDIO -> application.lightingController.onAudioStateChanged()
            CaptureOwner.AMBIENT -> application.lightingController.onAmbientStateChanged()
            null -> Unit
        }
        mainHandler.removeCallbacks(releasePausedHold)
        mainHandler.postDelayed(releasePausedHold, PAUSED_HOLD_TIMEOUT_MS)
    }

    private fun stopConsumer(
        audioStatus: AudioCaptureStatus = AudioCaptureStatus.AUTHORIZATION_REQUIRED,
        ambientStatus: AmbientCaptureStatus = AmbientCaptureStatus.AUTHORIZATION_REQUIRED,
    ) {
        when (projectionOwner) {
            CaptureOwner.AUDIO -> application.audioCaptureSession.stop(audioStatus)
            CaptureOwner.AMBIENT -> {
                application.ambientCaptureSession.pause(ambientStatus)
                application.ambientAudioSession.stop(AudioCaptureStatus.AUTHORIZATION_REQUIRED)
            }
            null -> Unit
        }
        projectionOwner = null
    }

    private fun onProjectionRevoked() {
        val owner = projectionOwner
        projectionOwner = null
        application.audioCaptureSession.stop(if (owner == CaptureOwner.AUDIO) AudioCaptureStatus.REVOKED else AudioCaptureStatus.AUTHORIZATION_REQUIRED)
        application.ambientCaptureSession.stop(if (owner == CaptureOwner.AMBIENT) AmbientCaptureStatus.REVOKED else AmbientCaptureStatus.AUTHORIZATION_REQUIRED)
        application.ambientAudioSession.stop(AudioCaptureStatus.AUTHORIZATION_REQUIRED)
        releaseHold(stopProjection = false, reconcile = true)
    }

    private fun releaseHold(
        stopProjection: Boolean = true,
        reconcile: Boolean,
        releaseLease: Boolean = true,
    ) {
        mainHandler.removeCallbacks(releasePausedHold)
        stopConsumer()
        if (application.ambientCaptureSession.live) application.ambientCaptureSession.stop(AmbientCaptureStatus.AUTHORIZATION_REQUIRED)
        val held = hold
        hold = null
        if (held != null) {
            ColoresLog.i(TAG, "capture released")
            runCatching { held.projection.unregisterCallback(held.callback) }
            if (stopProjection) runCatching { held.projection.stop() }
            refreshNotification()
        }
        val mode = application.lightingController.snapshot.value.mode
        if (shouldReconcileAudioController(reconcile, mode)) application.lightingController.onAudioStateChanged()
        if (shouldReconcileAmbientController(reconcile, mode)) application.lightingController.onAmbientStateChanged()
        if (releaseLease) releaseCaptureLease()
    }

    private fun stopAudioCapture(
        status: AudioCaptureStatus,
        reconcile: Boolean = true,
        releaseLease: Boolean = true,
    ) {
        ColoresLog.i(TAG, "stop audio status=$status held=${hold != null}")
        val owner = projectionOwner
        application.audioCaptureSession.stop(status)
        if (owner == CaptureOwner.AUDIO || owner == null) {
            if (owner == CaptureOwner.AUDIO) projectionOwner = null
            releaseHold(reconcile = false, releaseLease = releaseLease)
        }
        if (shouldReconcileAudioController(reconcile, application.lightingController.snapshot.value.mode)) {
            application.lightingController.onAudioStateChanged()
        }
    }

    private fun stopAmbientCapture(
        status: AmbientCaptureStatus,
        reconcile: Boolean = true,
        releaseLease: Boolean = true,
    ) {
        val owner = projectionOwner
        application.ambientCaptureSession.stop(status)
        application.ambientAudioSession.stop(AudioCaptureStatus.AUTHORIZATION_REQUIRED)
        if (owner == CaptureOwner.AMBIENT || owner == null) {
            if (owner == CaptureOwner.AMBIENT) projectionOwner = null
            releaseHold(reconcile = false, releaseLease = releaseLease)
        }
        if (shouldReconcileAmbientController(reconcile, application.lightingController.snapshot.value.mode)) {
            application.lightingController.onAmbientStateChanged()
        }
    }

    private fun refreshNotification() {
        if (!foreground) return
        runCatching { getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, buildNotification()) }
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
            .setContentText(
                getString(
                    when {
                        projectionOwner == CaptureOwner.AMBIENT && application.ambientAudioSession.running ->
                            R.string.service_notification_ambient_audio
                        projectionOwner == CaptureOwner.AMBIENT -> R.string.service_notification_ambient
                        projectionOwner == CaptureOwner.AUDIO -> R.string.service_notification_audio
                        hold != null -> R.string.service_notification_paused
                        else -> R.string.service_notification_text
                    },
                ),
            )
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
        private const val EXTRA_AMBIENT_AUDIO = "ambient_audio"
        private const val EXTRA_ZONES = "zones"
        private const val EXTRA_PER_ZONE = "per_zone"
        private const val EXTRA_GRID_STICKS = "grid_sticks"
        private const val EXTRA_GRID_ROWS = "grid_rows"
        private const val EXTRA_GRID_COLUMNS = "grid_columns"
        private const val CHANNEL_ID = "colores_effects"
        private const val NOTIFICATION_ID = 1001
        private const val TAG = "ColoresEffects"
        private const val PAUSED_HOLD_TIMEOUT_MS = 30 * 60 * 1_000L

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
            val live = application.audioLevelSource.state.value.status.keepsAudioCaptureActive
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
            }.onFailure { ColoresLog.w(TAG, "command ${intent.action} refused", it) }
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
                .putExtra(EXTRA_AMBIENT_AUDIO, config.audio)
                .putExtra(EXTRA_ZONES, config.zones)
                .putExtra(EXTRA_PER_ZONE, config.supportsPerZone)
                .putExtra(EXTRA_GRID_STICKS, grid?.map { it.stick ?: -1 }?.toIntArray())
                .putExtra(EXTRA_GRID_ROWS, grid?.map { it.row }?.toIntArray())
                .putExtra(EXTRA_GRID_COLUMNS, grid?.map { it.col }?.toIntArray())
        }
    }

    private val application: ColoresApplication
        get() = getApplication() as ColoresApplication

    private fun Intent.hasProjectionConsent(): Boolean =
        hasProjectionConsent(
            resultCode = getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED),
            resultDataPresent = projectionData() != null,
            okCode = Activity.RESULT_OK,
        )

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
            audio = getBooleanExtra(EXTRA_AMBIENT_AUDIO, false),
        )
    }
}

private class CaptureHold(
    val projection: MediaProjection,
    val callback: MediaProjection.Callback,
    val fullScreen: Boolean,
) {
    var used = false
}

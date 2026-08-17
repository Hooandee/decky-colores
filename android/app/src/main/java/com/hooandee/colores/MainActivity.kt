package com.hooandee.colores

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.media.projection.MediaProjectionConfig
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import com.hooandee.colores.permission.WriteSettingsPermission
import com.hooandee.colores.ui.ColoresScreen
import com.hooandee.colores.ui.ColoresTheme
import com.hooandee.colores.ui.ColoresViewModel

class MainActivity : AppCompatActivity() {
    private val viewModel by viewModels<ColoresViewModel>()
    private var projectionRequest = ProjectionRequest.NONE
    private var afterNotificationPermission: (() -> Unit)? = null
    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (result.resultCode == Activity.RESULT_OK && data != null) {
                when (projectionRequest) {
                    ProjectionRequest.AUDIO -> viewModel.activateAudio(result.resultCode, data)
                    ProjectionRequest.AMBIENT -> viewModel.activateAmbient(result.resultCode, data)
                    ProjectionRequest.NONE -> Unit
                }
            } else {
                when (projectionRequest) {
                    ProjectionRequest.AUDIO -> viewModel.onAudioAuthorizationDenied()
                    ProjectionRequest.AMBIENT -> viewModel.onAmbientAuthorizationDenied()
                    ProjectionRequest.NONE -> Unit
                }
            }
            projectionRequest = ProjectionRequest.NONE
        }
    private val audioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) launchProjectionConsent(ProjectionRequest.AUDIO) else viewModel.onAudioAuthorizationDenied()
        }
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            afterNotificationPermission?.also { afterNotificationPermission = null }?.invoke()
        }
    private val screenOnReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?,
            ) {
                if (intent?.action == Intent.ACTION_SCREEN_ON) viewModel.onScreenOn()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerScreenOnReceiver()
        setContent {
            val appearance by (application as ColoresApplication).appPreferences.appearance.collectAsState()
            ColoresTheme(appearance) {
                ColoresScreen(
                    viewModel = viewModel,
                    onGrantPermission = { startActivity(WriteSettingsPermission.createGrantIntent(this)) },
                    onAudioCaptureRequest = ::requestAudioCapture,
                    onAmbientCaptureRequest = {
                        requestNotificationPermissionIfNeeded { launchProjectionConsent(ProjectionRequest.AMBIENT) }
                    },
                    onGrantUsage = {
                        startActivity((application as ColoresApplication).usageAccess.settingsIntent())
                    },
                    appearance = appearance,
                    onThemeModeChange = (application as ColoresApplication).appPreferences::setThemeMode,
                    onAccentChange = (application as ColoresApplication).appPreferences::setAccent,
                    currentLanguageTag = AppCompatDelegate.getApplicationLocales()[0]?.toLanguageTag(),
                    onLanguageChange = { language ->
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.languageTag))
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    override fun onDestroy() {
        unregisterReceiver(screenOnReceiver)
        super.onDestroy()
    }

    override fun onStop() {
        viewModel.onAppBackground()
        super.onStop()
    }

    @Suppress("DEPRECATION")
    private fun registerScreenOnReceiver() {
        val filter = IntentFilter(Intent.ACTION_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenOnReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenOnReceiver, filter)
        }
    }

    private fun requestAudioCapture() {
        requestNotificationPermissionIfNeeded(::requestAudioPermission)
    }

    private fun requestAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            launchProjectionConsent(ProjectionRequest.AUDIO)
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun requestNotificationPermissionIfNeeded(onReady: () -> Unit) {
        val granted = ContextCompat.checkSelfPermission(this, NOTIFICATION_PERMISSION) == PackageManager.PERMISSION_GRANTED
        if (shouldRequestNotificationPermission(Build.VERSION.SDK_INT, granted)) {
            afterNotificationPermission = onReady
            notificationPermissionLauncher.launch(NOTIFICATION_PERMISSION)
        } else {
            onReady()
        }
    }

    private fun launchProjectionConsent(request: ProjectionRequest) {
        projectionRequest = request
        val manager = getSystemService(MediaProjectionManager::class.java)
        val intent =
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                shouldCaptureDefaultDisplay(request, Build.VERSION.SDK_INT)
            ) {
                manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
            } else {
                manager.createScreenCaptureIntent()
            }
        projectionLauncher.launch(intent)
    }
}

internal enum class ProjectionRequest {
    NONE,
    AUDIO,
    AMBIENT,
}

internal fun shouldCaptureDefaultDisplay(
    request: ProjectionRequest,
    sdk: Int,
): Boolean = request == ProjectionRequest.AMBIENT && sdk >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

internal fun shouldRequestNotificationPermission(
    sdk: Int,
    granted: Boolean,
): Boolean = sdk >= Build.VERSION_CODES.TIRAMISU && !granted

private const val NOTIFICATION_PERMISSION = "android.permission.POST_NOTIFICATIONS"

package com.hooandee.colores

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.media.projection.MediaProjectionConfig
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
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
    private var afterNotificationPermission = ProjectionRequest.NONE
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
            val pending = afterNotificationPermission
            afterNotificationPermission = ProjectionRequest.NONE
            continueCaptureRequest(pending)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        projectionRequest = restoredProjectionRequest(savedInstanceState?.getString(STATE_PROJECTION_REQUEST))
        afterNotificationPermission = restoredProjectionRequest(savedInstanceState?.getString(STATE_AFTER_NOTIFICATION))
        setContent {
            val appearance by (application as ColoresApplication).appPreferences.appearance.collectAsState()
            ColoresTheme(appearance) {
                ColoresScreen(
                    viewModel = viewModel,
                    onGrantPermission = {
                        startFirstAvailable(
                            WriteSettingsPermission.createGrantIntent(this),
                            Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS),
                        )
                    },
                    onAudioCaptureRequest = ::requestAudioCapture,
                    onAmbientCaptureRequest = {
                        requestNotificationPermissionIfNeeded(ProjectionRequest.AMBIENT)
                    },
                    onGrantUsage = {
                        startFirstAvailable(
                            (application as ColoresApplication).usageAccess.settingsIntent(),
                            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
                        )
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_PROJECTION_REQUEST, projectionRequest.name)
        outState.putString(STATE_AFTER_NOTIFICATION, afterNotificationPermission.name)
    }

    override fun onStop() {
        viewModel.onAppBackground()
        super.onStop()
    }

    private fun startFirstAvailable(vararg intents: Intent) {
        val launched =
            launchFirstAvailable(intents.asList()) { intent ->
                try {
                    startActivity(intent)
                    true
                } catch (_: ActivityNotFoundException) {
                    false
                }
            }
        if (!launched) Log.w(TAG, "no settings activity for ${intents.firstOrNull()?.action}")
    }

    private fun requestAudioCapture() {
        requestNotificationPermissionIfNeeded(ProjectionRequest.AUDIO)
    }

    private fun requestAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            launchProjectionConsent(ProjectionRequest.AUDIO)
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun requestNotificationPermissionIfNeeded(request: ProjectionRequest) {
        val granted = ContextCompat.checkSelfPermission(this, NOTIFICATION_PERMISSION) == PackageManager.PERMISSION_GRANTED
        if (shouldRequestNotificationPermission(Build.VERSION.SDK_INT, granted)) {
            afterNotificationPermission = request
            notificationPermissionLauncher.launch(NOTIFICATION_PERMISSION)
        } else {
            continueCaptureRequest(request)
        }
    }

    private fun continueCaptureRequest(request: ProjectionRequest) {
        when (request) {
            ProjectionRequest.AUDIO -> requestAudioPermission()
            ProjectionRequest.AMBIENT -> launchProjectionConsent(ProjectionRequest.AMBIENT)
            ProjectionRequest.NONE -> Unit
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

internal fun restoredProjectionRequest(saved: String?): ProjectionRequest =
    ProjectionRequest.entries.firstOrNull { it.name == saved } ?: ProjectionRequest.NONE

internal fun <T> launchFirstAvailable(
    candidates: List<T>,
    launch: (T) -> Boolean,
): Boolean = candidates.any(launch)

internal fun shouldCaptureDefaultDisplay(
    request: ProjectionRequest,
    sdk: Int,
): Boolean = request == ProjectionRequest.AMBIENT && sdk >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

internal fun shouldRequestNotificationPermission(
    sdk: Int,
    granted: Boolean,
): Boolean = sdk >= Build.VERSION_CODES.TIRAMISU && !granted

private const val NOTIFICATION_PERMISSION = "android.permission.POST_NOTIFICATIONS"
private const val STATE_PROJECTION_REQUEST = "projection_request"
private const val STATE_AFTER_NOTIFICATION = "after_notification_permission"
private const val TAG = "ColoresMain"

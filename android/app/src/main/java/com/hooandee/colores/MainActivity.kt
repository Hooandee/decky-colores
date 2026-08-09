package com.hooandee.colores

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
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
                    onAmbientCaptureRequest = { launchProjectionConsent(ProjectionRequest.AMBIENT) },
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
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            launchProjectionConsent(ProjectionRequest.AUDIO)
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun launchProjectionConsent(request: ProjectionRequest) {
        projectionRequest = request
        val manager = getSystemService(MediaProjectionManager::class.java)
        projectionLauncher.launch(manager.createScreenCaptureIntent())
    }

    private enum class ProjectionRequest {
        NONE,
        AUDIO,
        AMBIENT,
    }
}

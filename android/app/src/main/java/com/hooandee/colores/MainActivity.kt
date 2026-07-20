package com.hooandee.colores

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.hooandee.colores.permission.WriteSettingsPermission
import com.hooandee.colores.ui.ColoresScreen
import com.hooandee.colores.ui.ColoresTheme
import com.hooandee.colores.ui.ColoresViewModel

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<ColoresViewModel>()
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
            ColoresTheme {
                ColoresScreen(
                    viewModel = viewModel,
                    onGrantPermission = { startActivity(WriteSettingsPermission.createGrantIntent(this)) },
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

    @Suppress("DEPRECATION")
    private fun registerScreenOnReceiver() {
        val filter = IntentFilter(Intent.ACTION_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenOnReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenOnReceiver, filter)
        }
    }
}

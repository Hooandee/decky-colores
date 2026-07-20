package com.hooandee.colores

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
}

package com.tlog

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.rememberNavController
import com.tlog.data.AppSettings
import com.tlog.ui.TLogNavHost
import com.tlog.ui.lock.BiometricGate
import com.tlog.ui.theme.TLogTheme
import com.tlog.viewmodel.TLogViewModel

class MainActivity : FragmentActivity() {
    private val viewModel: TLogViewModel by viewModels { TLogViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by viewModel.settingsFlow.collectAsState(initial = AppSettings())
            TLogTheme(
                themeMode = settings.themeMode,
                useDynamicColor = settings.useDynamicColor,
                oledBlack = settings.oledBlack
            ) {
                BiometricGate(enabled = settings.biometricLock) {
                    val nav = rememberNavController()
                    TLogNavHost(nav = nav, viewModel = viewModel)
                }
            }
        }
    }
}

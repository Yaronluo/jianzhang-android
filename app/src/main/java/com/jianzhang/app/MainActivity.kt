package com.jianzhang.app

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jianzhang.app.ui.FinanceViewModel
import com.jianzhang.app.ui.navigation.JianzhangApp
import com.jianzhang.app.ui.screens.OnboardingScreen
import com.jianzhang.app.ui.theme.JianzhangTheme
import com.jianzhang.app.quickcapture.SmartNotificationManager

class MainActivity : ComponentActivity() {
    private val viewModel: FinanceViewModel by viewModels()
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) SmartNotificationManager(this).restoreAll()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            SmartNotificationManager(this).restoreAll()
        }
        handleNavigationIntent(intent)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        setContent {
            val onboardingVisible by viewModel.onboardingVisible.collectAsStateWithLifecycle()
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val darkTheme = when (themeMode) {
                FinanceViewModel.THEME_DARK -> true
                FinanceViewModel.THEME_LIGHT -> false
                else -> isSystemInDarkTheme()
            }
            SideEffect {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        lightScrim = Color.TRANSPARENT,
                        darkScrim = Color.TRANSPARENT,
                        detectDarkMode = { darkTheme }
                    ),
                    navigationBarStyle = SystemBarStyle.auto(
                        lightScrim = Color.TRANSPARENT,
                        darkScrim = Color.TRANSPARENT,
                        detectDarkMode = { darkTheme }
                    )
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isStatusBarContrastEnforced = false
                    window.isNavigationBarContrastEnforced = false
                }
            }
            JianzhangTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (onboardingVisible) {
                        OnboardingScreen(
                            onSkip = viewModel::finishOnboarding,
                            onCreateAccount = viewModel::createOnboardingAccount
                        )
                    } else {
                        JianzhangApp(viewModel)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNavigationIntent(intent)
    }

    private fun handleNavigationIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_OPEN_QUICK_CAPTURE_SETTINGS -> viewModel.requestNavigation("settings")
            ACTION_OPEN_RECEIPT -> viewModel.requestNavigation("receipt")
            ACTION_OPEN_SMART_ITEMS -> {
                val cardId = intent.getStringExtra(EXTRA_SMART_CARD_ID)
                viewModel.requestNavigation(
                    cardId?.let { "smart-items?selectedId=$it" } ?: "smart-items"
                )
            }
            ACTION_EDIT_QUICK_CAPTURE -> intent.getStringExtra(EXTRA_QUICK_SESSION_ID)?.let {
                viewModel.requestNavigation("receipt-quick/$it")
            }
        }
    }

    companion object {
        const val ACTION_OPEN_QUICK_CAPTURE_SETTINGS = "com.jianzhang.app.OPEN_QUICK_CAPTURE_SETTINGS"
        const val ACTION_EDIT_QUICK_CAPTURE = "com.jianzhang.app.EDIT_QUICK_CAPTURE"
        const val ACTION_OPEN_RECEIPT = "com.jianzhang.app.OPEN_RECEIPT"
        const val ACTION_OPEN_SMART_ITEMS = "com.jianzhang.app.OPEN_SMART_ITEMS"
        const val EXTRA_QUICK_SESSION_ID = "quick_session_id"
        const val EXTRA_SMART_CARD_ID = "smart_card_id"
    }
}

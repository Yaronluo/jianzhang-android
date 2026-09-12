package com.jianzhang.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jianzhang.app.ui.FinanceViewModel
import com.jianzhang.app.ui.navigation.JianzhangApp
import com.jianzhang.app.ui.screens.OnboardingScreen
import com.jianzhang.app.ui.theme.JianzhangTheme
import androidx.compose.runtime.getValue

class MainActivity : ComponentActivity() {
    private val viewModel: FinanceViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val onboardingVisible by viewModel.onboardingVisible.collectAsStateWithLifecycle()
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val darkTheme = when (themeMode) {
                FinanceViewModel.THEME_DARK -> true
                FinanceViewModel.THEME_LIGHT -> false
                else -> isSystemInDarkTheme()
            }
            JianzhangTheme(darkTheme = darkTheme) {
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

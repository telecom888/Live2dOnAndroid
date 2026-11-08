package com.bangdream.pet.ui.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import com.bangdream.pet.I18n
import com.bangdream.pet.ThemeSettings
import com.bangdream.pet.ui.theme.BangDreamPetTheme

class LlmSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appContext = applicationContext
        I18n.init(appContext)
        setContent {
            val themeSettings = remember { ThemeSettings.load(appContext) }
            BangDreamPetTheme(
                darkMode = themeSettings.darkMode,
                dynamicColor = themeSettings.dynamicColorEnabled,
            ) {
                LlmSettingsScreen(onBack = ::finish)
            }
        }
    }
}

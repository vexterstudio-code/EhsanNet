package com.v2ray.ang.ui.compose

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private val LightColor = lightColorScheme(
    primary = Color(0xFF0057FF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD8E7FF),
    onPrimaryContainer = Color(0xFF001A4D),
    secondary = Color(0xFF0095EB),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD1F0FF),
    onSecondaryContainer = Color(0xFF002438),
    tertiary = Color(0xFF00A372),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFB5F7DF),
    onTertiaryContainer = Color(0xFF002116),
    error = Color(0xFFD9264E),
    errorContainer = Color(0xFFFFD9E0),
    onError = Color(0xFFFFFFFF),
    onErrorContainer = Color(0xFF410010),
    background = Color(0xFFF4F8FF),
    onBackground = Color(0xFF0B152C),
    surface = Color(0xFFF4F8FF),
    onSurface = Color(0xFF0B152C),
    surfaceVariant = Color(0xFFE1EBFA),
    onSurfaceVariant = Color(0xFF465A7D),
    outline = Color(0xFF7286AA),
    outlineVariant = Color(0xFFCAD8EE),
    inverseSurface = Color(0xFF101C36),
    inverseOnSurface = Color(0xFFEEF4FF),
    inversePrimary = Color(0xFF8BB8FF),
    scrim = Color(0xFF000000),
    surfaceTint = Color(0xFF0057FF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFCFDFF),
    surfaceContainer = Color(0xFFEEF4FF),
    surfaceContainerHigh = Color(0xFFE3ECFC),
    surfaceContainerHighest = Color(0xFFD7E3F8),
)

private val DarkColor = darkColorScheme(
    primary = Color(0xFF00C8FF),
    onPrimary = Color(0xFF002438),
    primaryContainer = Color(0xFF004380),
    onPrimaryContainer = Color(0xFFD1F2FF),
    secondary = Color(0xFF4D8BFF),
    onSecondary = Color(0xFF001C4D),
    secondaryContainer = Color(0xFF0B2E6B),
    onSecondaryContainer = Color(0xFFD8E6FF),
    tertiary = Color(0xFF00FFA3),
    onTertiary = Color(0xFF003321),
    tertiaryContainer = Color(0xFF005237),
    onTertiaryContainer = Color(0xFFA6FFDA),
    error = Color(0xFFFF5C80),
    errorContainer = Color(0xFF730020),
    onError = Color(0xFF420010),
    onErrorContainer = Color(0xFFFFD9E1),
    background = Color(0xFF070C18),
    onBackground = Color(0xFFEAF2FF),
    surface = Color(0xFF070C18),
    onSurface = Color(0xFFEAF2FF),
    surfaceVariant = Color(0xFF18243E),
    onSurfaceVariant = Color(0xFF93A8CE),
    outline = Color(0xFF566B93),
    outlineVariant = Color(0xFF213152),
    inverseSurface = Color(0xFFEAF2FF),
    inverseOnSurface = Color(0xFF0A1224),
    inversePrimary = Color(0xFF0057FF),
    scrim = Color(0xFF000000),
    surfaceTint = Color(0xFF00C8FF),
    surfaceContainerLowest = Color(0xFF040812),
    surfaceContainerLow = Color(0xFF0D1528),
    surfaceContainer = Color(0xFF111C34),
    surfaceContainerHigh = Color(0xFF162340),
    surfaceContainerHighest = Color(0xFF1D2D50),
)

// Semantic Colors
val colorPing = Color(0xFF00C982)
val colorPingRed = Color(0xFFFF3870)
val colorConfigType = Color(0xFF1E90FF)
val colorFabActive = Color(0xFF00C8FF)
val colorFabInactiveLight = Color(0xFF8AA0C4)
val colorFabInactiveDark = Color(0xFF4E6288)
val dividerColorLight = Color(0xFFD8E4F8)
val dividerColorDark = Color(0xFF1A2845)

// Cyber RGB Theme Colors
val cyberBlueDeep = Color(0xFF061638)
val cyberBlueMid = Color(0xFF0057FF)
val cyberBlueElectric = Color(0xFF00C8FF)
val cyberCyanNeon = Color(0xFF00F5FF)
val cyberRgbRed = Color(0xFFFF1744)
val cyberRgbOrange = Color(0xFFFF9100)
val cyberRgbYellow = Color(0xFFFFEA00)
val cyberRgbLime = Color(0xFF39FF14)
val cyberRgbViolet = Color(0xFF8B2CF5)
val cyberRgbMagenta = Color(0xFFFF00CC)
val cyberRgbEmerald = Color(0xFF00FFA3)
val cyberRgbBlue = Color(0xFF2979FF)
val cyberTrackDark = Color(0xFF0B152C)
val cyberTrackLight = Color(0xFFE9F2FF)
val cyberTrackBorderLight = Color(0x660088FF)
val cyberAlertRed = Color(0xFFFF2E63)
val cyberGold = Color(0xFFFFD15C)
val cyberGoldDeep = Color(0xFFFF8A00)
val cyberGoldSurfaceDark = Color(0xFF181428)
val cyberGoldSurfaceLight = Color(0xFF102042)
val cyberCardDark = Color(0xFF0E1830)
val cyberCardLight = Color(0xFFFFFFFF)
val cyberCardSelectedDark = Color(0xFF12264D)
val cyberCardSelectedLight = Color(0xFFEBF4FF)
val cyberCardBorderDark = Color(0xFF1D2F55)
val cyberCardBorderLight = Color(0xFFD6E5FA)
val cyberPingBgPositiveDark = Color(0x2200FFA3)
val cyberPingBgPositiveLight = Color(0x1A009E6D)
val cyberPingBgErrorDark = Color(0x22FF3870)
val cyberPingBgErrorLight = Color(0x1AD9264E)
val cyberTypeBgDark = Color(0x2200C8FF)
val cyberTypeBgLight = Color(0x140057FF)

// Toast Colors 85%
val toastNormalBgLight = Color(0xD916223C)
val toastNormalBgDark = Color(0xD91E2D4E)
val toastSuccessBg = Color(0xD9008F5D)
val toastErrorBg = Color(0xD9C81E4B)
val toastInfoBg = Color(0xD90057FF)
val toastIconCircleBg = Color(0x33FFFFFF)
val toastTextColor = Color.White

object ThemeManager {
    private val _themeMode = MutableStateFlow(
        MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT, "0") ?: "0"
    )
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _dynamicColorEnabled = MutableStateFlow(
        MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, true)
    )
    val dynamicColorEnabled: StateFlow<Boolean> = _dynamicColorEnabled.asStateFlow()

    fun setThemeMode(mode: String) {
        MmkvManager.encodeSettings(AppConfig.PREF_UI_MODE_NIGHT, mode)
        _themeMode.value = mode
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        MmkvManager.encodeSettings(AppConfig.PREF_DYNAMIC_COLOR, enabled)
        _dynamicColorEnabled.value = enabled
    }

    fun refresh() {
        _themeMode.value =
            MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT, "0") ?: "0"
        _dynamicColorEnabled.value =
            MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, true)
    }
}

@Composable
fun resolveDarkTheme(): Boolean {
    val mode by ThemeManager.themeMode.collectAsState()
    return when (mode) {
        "1" -> false
        "2" -> true
        else -> isSystemInDarkTheme()
    }
}

val LocalDarkTheme = compositionLocalOf { false }

private object NoIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        object : Modifier.Node() {}

    override fun hashCode(): Int = -1
    override fun equals(other: Any?): Boolean = other === this
}

// Reevaluate @OptIn(ExperimentalMaterial3Api::class) when Material 3 promotes LocalRippleConfiguration to stable.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTheme(
    darkTheme: Boolean = resolveDarkTheme(),
    content: @Composable () -> Unit
) {
    val dynamicColor by ThemeManager.dynamicColorEnabled.collectAsState()
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColor
        else -> LightColor
    }
    val snackbarController = rememberAppSnackbarController()

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            val window = activity.window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalDarkTheme provides darkTheme,
        LocalAppSnackbar provides snackbarController,
        LocalRippleConfiguration provides null,
    ) {
        MaterialTheme(
            colorScheme = colorScheme
        ) {
            CompositionLocalProvider(
                LocalIndication provides NoIndication,
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AppSnackbarBridge(controller = snackbarController)
                    content()
                    AppSnackbarHost(hostState = snackbarController.hostState)
                }
            }
        }
    }
}

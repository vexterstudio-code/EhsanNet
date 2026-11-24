package com.v2ray.ang.ui

import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.CoreNativeManager
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.ItemDivider
import com.v2ray.ang.ui.compose.LocalDarkTheme
import com.v2ray.ang.ui.compose.NavigationBarsSpacer
import com.v2ray.ang.ui.compose.SettingsMenuItem
import com.v2ray.ang.ui.compose.VersionInfoBlock
import com.v2ray.ang.ui.compose.cyberBlueDeep
import com.v2ray.ang.ui.compose.cyberBlueElectric
import com.v2ray.ang.ui.compose.cyberBlueMid
import com.v2ray.ang.ui.compose.cyberCardBorderDark
import com.v2ray.ang.ui.compose.cyberCardBorderLight
import com.v2ray.ang.ui.compose.cyberCardDark
import com.v2ray.ang.ui.compose.cyberCardLight
import com.v2ray.ang.ui.compose.cyberCardSelectedLight
import com.v2ray.ang.ui.compose.cyberCyanNeon
import com.v2ray.ang.ui.compose.cyberTrackLight

class AboutActivity : BaseComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    @Composable
    override fun ScreenContent() {
        AboutScreen(
            onBackClick = { finish() },
            onTranslatorsClick = {
                startActivity(Intent(this, TranslatorsActivity::class.java))
            }
        )
    }
}

@Composable
fun AboutScreen(
    onBackClick: () -> Unit,
    onTranslatorsClick: () -> Unit
) {
    var showOssDialog by remember { mutableStateOf(false) }
    val isDarkTheme = LocalDarkTheme.current

    val libVersion = remember { CoreNativeManager.getLibVersion() }
    val versionText = "v${BuildConfig.VERSION_NAME} ($libVersion)"
    val appIdText = BuildConfig.APPLICATION_ID

    val heroBrush = remember(isDarkTheme) {
        if (isDarkTheme) {
            Brush.linearGradient(
                colors = listOf(
                    cyberBlueDeep,
                    cyberBlueMid.copy(alpha = 0.55f),
                    cyberBlueDeep
                )
            )
        } else {
            Brush.linearGradient(
                colors = listOf(
                    cyberTrackLight,
                    cyberCardSelectedLight,
                    cyberTrackLight
                )
            )
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.title_about),
                onBackClick = onBackClick
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, if (isDarkTheme) cyberBlueElectric.copy(alpha = 0.45f) else cyberCardBorderLight),
                color = if (isDarkTheme) cyberBlueDeep else cyberTrackLight
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(heroBrush)
                        .padding(vertical = 24.dp, horizontal = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(82.dp)
                                .clip(CircleShape)
                                .background(if (isDarkTheme) cyberBlueDeep else Color.White)
                                .border(
                                    1.5.dp,
                                    if (isDarkTheme) cyberCyanNeon.copy(alpha = 0.55f) else cyberBlueMid.copy(alpha = 0.45f),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_ehsannet_logo),
                                contentDescription = stringResource(R.string.app_name),
                                tint = if (isDarkTheme) cyberCyanNeon else cyberBlueMid,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isDarkTheme) Color.White else cyberBlueDeep
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            shape = CircleShape,
                            color = if (isDarkTheme) cyberCyanNeon.copy(alpha = 0.16f) else cyberBlueMid.copy(alpha = 0.12f),
                            border = BorderStroke(
                                1.dp,
                                if (isDarkTheme) cyberCyanNeon.copy(alpha = 0.45f) else cyberBlueMid.copy(alpha = 0.35f)
                            )
                        ) {
                            Text(
                                text = "v${BuildConfig.VERSION_NAME}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDarkTheme) cyberCyanNeon else cyberBlueMid,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = if (isDarkTheme) cyberCardDark else cyberCardLight,
                border = BorderStroke(1.dp, if (isDarkTheme) cyberCardBorderDark else cyberCardBorderLight)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SettingsMenuItem(
                        icon = painterResource(R.drawable.license_24px),
                        title = stringResource(R.string.title_oss_license),
                        onClick = { showOssDialog = true }
                    )
                    ItemDivider()
                    SettingsMenuItem(
                        icon = painterResource(R.drawable.ic_translate_24dp),
                        title = stringResource(R.string.title_translators),
                        onClick = onTranslatorsClick
                    )
                }
            }

            VersionInfoBlock(
                versionText = versionText,
                appIdText = appIdText
            )
            NavigationBarsSpacer()
        }
    }

    if (showOssDialog) {
        AlertDialog(
            onDismissRequest = { showOssDialog = false },
            title = { Text(stringResource(R.string.title_oss_license)) },
            text = {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            loadUrl("file:///android_asset/open_source_licenses.html")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 300.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = { showOssDialog = false }) {
                    Text(stringResource(R.string.action_ok))
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.padding(bottom = 60.dp)
        )
    }
}

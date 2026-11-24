package com.v2ray.ang.ui.main

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.AppDivider
import com.v2ray.ang.ui.compose.LocalDarkTheme
import com.v2ray.ang.ui.compose.NavigationBarsSpacer
import com.v2ray.ang.ui.compose.cyberBlueDeep
import com.v2ray.ang.ui.compose.cyberBlueElectric
import com.v2ray.ang.ui.compose.cyberBlueMid
import com.v2ray.ang.ui.compose.cyberCardBorderLight
import com.v2ray.ang.ui.compose.cyberCardSelectedLight
import com.v2ray.ang.ui.compose.cyberCyanNeon
import com.v2ray.ang.ui.compose.cyberTrackLight
import com.v2ray.ang.ui.compose.verticalScrollbar

enum class MainDestination(@DrawableRes val iconRes: Int, @StringRes val labelRes: Int) {
    Subscriptions(R.drawable.ic_subscriptions_24dp, R.string.title_sub_setting),
    PerAppProxy(R.drawable.ic_per_apps_24dp, R.string.per_app_proxy_settings),
    Routing(R.drawable.ic_routing_24dp, R.string.routing_settings_title),
    UserAssets(R.drawable.ic_file_24dp, R.string.title_user_asset_setting),
    Settings(R.drawable.ic_settings_24dp, R.string.title_settings),
    Logcat(R.drawable.ic_logcat_24dp, R.string.title_logcat),
    CheckUpdate(R.drawable.ic_check_update_24dp, R.string.update_check_for_update),
    BackupRestore(R.drawable.ic_restore_24dp, R.string.title_configuration_backup_restore),
    About(R.drawable.ic_about_24dp, R.string.title_about)
}

private val primaryDrawerItems = listOf(
    MainDestination.Subscriptions,
    MainDestination.PerAppProxy,
    MainDestination.Routing,
    MainDestination.UserAssets,
    MainDestination.Settings
)

private val drawerItems = primaryDrawerItems + listOf(
    MainDestination.Logcat,
    MainDestination.CheckUpdate,
    MainDestination.BackupRestore,
    MainDestination.About
)

@Composable
fun MainDrawerContent(drawerState: DrawerState, onNavigate: (MainDestination) -> Unit) {
    val drawerScrollState = rememberScrollState()
    val isDarkTheme = LocalDarkTheme.current
    val headerBrush = remember(isDarkTheme) {
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
    val logoTint = if (isDarkTheme) cyberCyanNeon else cyberBlueMid
    val logoRingColor = if (isDarkTheme) cyberCyanNeon.copy(alpha = 0.55f) else cyberBlueMid.copy(alpha = 0.45f)
    val logoMedallionBg = if (isDarkTheme) cyberBlueDeep else Color.White
    val headerBorder = if (isDarkTheme) cyberBlueElectric.copy(alpha = 0.45f) else cyberCardBorderLight
    val titleColor = if (isDarkTheme) Color.White else cyberBlueDeep
    val badgeBg = if (isDarkTheme) cyberCyanNeon.copy(alpha = 0.16f) else cyberBlueMid.copy(alpha = 0.12f)
    val badgeBorder = if (isDarkTheme) cyberCyanNeon.copy(alpha = 0.45f) else cyberBlueMid.copy(alpha = 0.35f)
    val badgeTextColor = if (isDarkTheme) cyberCyanNeon else cyberBlueMid
    val itemShape = remember { RoundedCornerShape(14.dp) }

    ModalDrawerSheet(
        drawerState = drawerState,
        modifier = Modifier.fillMaxWidth(0.78f),
        drawerContainerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(drawerScrollState)
                .verticalScrollbar(drawerScrollState)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .height(172.dp),
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, headerBorder),
                color = if (isDarkTheme) cyberBlueDeep else cyberTrackLight
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(headerBrush)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(78.dp)
                            .clip(CircleShape)
                            .background(logoMedallionBg)
                            .border(1.5.dp, logoRingColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_ehsannet_logo),
                            contentDescription = stringResource(R.string.app_name),
                            tint = logoTint,
                            modifier = Modifier.size(46.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = titleColor
                        )
                        Surface(
                            shape = CircleShape,
                            color = badgeBg,
                            border = BorderStroke(1.dp, badgeBorder)
                        ) {
                            Text(
                                text = "v${BuildConfig.VERSION_NAME}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = badgeTextColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            drawerItems.forEachIndexed { index, item ->
                if (index == primaryDrawerItems.size) {
                    AppDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                }
                NavigationDrawerItem(
                    label = {
                        Text(
                            text = stringResource(item.labelRes),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    selected = false,
                    onClick = { onNavigate(item) },
                    icon = {
                        Icon(
                            painter = painterResource(item.iconRes),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    shape = itemShape,
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
            NavigationBarsSpacer()
        }
    }
}

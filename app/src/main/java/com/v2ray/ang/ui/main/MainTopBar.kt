package com.v2ray.ang.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.LocalDarkTheme
import com.v2ray.ang.ui.compose.cyberCyanNeon
import com.v2ray.ang.ui.compose.cyberGold
import com.v2ray.ang.ui.compose.cyberGoldDeep
import com.v2ray.ang.ui.compose.cyberGoldSurfaceDark
import com.v2ray.ang.ui.compose.cyberGoldSurfaceLight
import com.v2ray.ang.ui.compose.verticalScrollbar

private val MAX_MENU_HEIGHT = 420.dp

@Composable
fun MainTopBar(
    isLoading: Boolean,
    showSearch: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchClose: () -> Unit,
    onSearchToggle: (Boolean) -> Unit,
    onMenuClick: () -> Unit,
    onAction: (MainAction) -> Unit,
    onMoreMenuAction: (MainMoreMenuAction) -> Unit
) {
    var showImportMenu by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    val importMenuScrollState = rememberScrollState()
    val moreMenuScrollState = rememberScrollState()
    val isDarkTheme = LocalDarkTheme.current

    AppTopBar(
        title = stringResource(R.string.app_name),
        onBackClick = {},
        isLoading = isLoading,
        isSearchActive = showSearch,
        searchQuery = searchQuery,
        onSearchQueryChange = onSearchQueryChange,
        onSearchClose = onSearchClose,
        searchPlaceholder = stringResource(R.string.menu_item_search),
        navigationIcon = {
            if (showSearch) {
                IconButton(onClick = onSearchClose) {
                    Icon(
                        painterResource(R.drawable.ic_arrow_back_24dp),
                        contentDescription = stringResource(R.string.acc_back)
                    )
                }
            } else {
                IconButton(onClick = onMenuClick, modifier = Modifier.testTag("menu_button")) {
                    Icon(
                        painterResource(R.drawable.ic_menu_24dp),
                        contentDescription = stringResource(R.string.acc_open_menu)
                    )
                }
            }
        },
        titleContent = {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.2.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        actions = {
            if (!showSearch) {
                VipTopBarButton(
                    isDarkTheme = isDarkTheme,
                    onClick = { onAction(MainAction.OpenVipBot) }
                )
                IconButton(
                    onClick = { onSearchToggle(true) },
                    modifier = Modifier.testTag("search_button")
                ) {
                    Icon(
                        painterResource(R.drawable.ic_search_24dp),
                        contentDescription = stringResource(R.string.acc_search)
                    )
                }
            }
            Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                IconButton(
                    onClick = { showImportMenu = true },
                    modifier = Modifier.testTag("add_config_button")
                ) {
                    Icon(
                        painterResource(R.drawable.ic_add_24dp),
                        contentDescription = stringResource(R.string.acc_add)
                    )
                }
                DropdownMenu(
                    expanded = showImportMenu,
                    onDismissRequest = { showImportMenu = false },
                    scrollState = importMenuScrollState,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .heightIn(max = MAX_MENU_HEIGHT)
                        .verticalScrollbar(importMenuScrollState)
                ) {
                    ImportMenuContent(
                        onAction = { action ->
                            showImportMenu = false
                            onAction(action)
                        }
                    )
                }
            }
            Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.testTag("more_options_button")
                ) {
                    Icon(
                        painterResource(R.drawable.ic_more_vert_24dp),
                        contentDescription = stringResource(R.string.acc_more)
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    scrollState = moreMenuScrollState,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .heightIn(max = MAX_MENU_HEIGHT)
                        .verticalScrollbar(moreMenuScrollState)
                ) {
                    MoreMenuContent { action ->
                        showMenu = false
                        onMoreMenuAction(action)
                    }
                }
            }
        }
    )
}

@Composable
private fun VipTopBarButton(
    isDarkTheme: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val vipLabel = stringResource(R.string.title_vip)
    val vipAcc = stringResource(R.string.acc_vip)
    val borderBrush = remember {
        Brush.linearGradient(listOf(cyberGold, cyberGoldDeep, cyberCyanNeon))
    }
    val containerColor = if (isDarkTheme) cyberGoldSurfaceDark else cyberGoldSurfaceLight

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .testTag("vip_button")
            .semantics { contentDescription = vipAcc }
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .clip(CircleShape)
                .background(containerColor)
                .border(1.2.dp, borderBrush, CircleShape)
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_vip_crown_24dp),
                contentDescription = null,
                tint = cyberGold,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = vipLabel,
                color = cyberGold,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.7.sp,
                maxLines = 1
            )
        }
    }
}

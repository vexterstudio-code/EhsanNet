package com.v2ray.ang.ui.main

import android.graphics.Matrix
import android.graphics.SweepGradient
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.ui.compose.LocalDarkTheme
import com.v2ray.ang.ui.compose.QRCodeDialog
import com.v2ray.ang.ui.compose.cyberCyanNeon
import com.v2ray.ang.ui.compose.cyberRgbBlue
import com.v2ray.ang.ui.compose.cyberRgbEmerald
import com.v2ray.ang.ui.compose.cyberRgbLime
import com.v2ray.ang.ui.compose.cyberRgbMagenta
import com.v2ray.ang.ui.compose.cyberRgbOrange
import com.v2ray.ang.ui.compose.cyberRgbRed
import com.v2ray.ang.ui.compose.cyberRgbViolet
import com.v2ray.ang.ui.compose.cyberRgbYellow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    mainViewModel: MainViewModel,
    onAction: (MainAction) -> Unit,
    onNavigate: (MainDestination) -> Unit,
) {
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val groups = uiState.groups
    val isLoading by mainViewModel.isLoading.collectAsStateWithLifecycle()
    val isRunning = uiState.isRunning
    val displayText = mainViewModel.formatStatus(uiState.status)
    val selectedGuid = uiState.selectedGuid
    val doubleColumnDisplay = uiState.doubleColumnDisplay
    val confirmRemove = uiState.confirmRemove
    val shareQRCodeBitmap = uiState.shareQRCodeBitmap

    val isDarkTheme = LocalDarkTheme.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showDelAllConfirm by remember { mutableStateOf(false) }
    var showDelDuplicateConfirm by remember { mutableStateOf(false) }
    var showDelInvalidConfirm by remember { mutableStateOf(false) }
    var showRemoveConfirm by rememberSaveable(stateSaver = ServerDeleteTarget.Saver) {
        mutableStateOf<ServerDeleteTarget?>(null)
    }

    var shareTarget by remember { mutableStateOf<Triple<String, ProfileItem, Boolean>?>(null) }
    val removeServer: (String, String) -> Unit = remember(confirmRemove, onAction) {
        { guid, profileName ->
            if (confirmRemove) {
                showRemoveConfirm = ServerDeleteTarget(guid, profileName)
            } else {
                onAction(MainAction.RemoveServer(guid))
            }
        }
    }
    val onSelectServer = remember(onAction) { { guid: String -> onAction(MainAction.SelectServer(guid)) } }
    val onEditServer = remember(onAction) {
        { guid: String, profile: ProfileItem -> onAction(MainAction.EditServer(guid, profile)) }
    }
    val onOpenSubLink = remember(onAction) {
        { url: String -> onAction(MainAction.OpenSubscriptionLink(url)) }
    }

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { groups.size.coerceAtLeast(1) }
    )

    val lazyListStates = remember { mutableStateMapOf<String, LazyListState>() }
    val lazyGridStates = remember { mutableStateMapOf<String, LazyGridState>() }

    LaunchedEffect(groups) {
        val validGroupIds = groups.map { it.id }.toSet()
        lazyListStates.keys.retainAll(validGroupIds)
        lazyGridStates.keys.retainAll(validGroupIds)
    }

    LaunchedEffect(groups, uiState.selectedGroupId) {
        if (groups.isEmpty()) return@LaunchedEffect
        val selectedIndex = groups.indexOfFirst { it.id == uiState.selectedGroupId }
            .takeIf { it >= 0 } ?: 0
        if (!pagerState.isScrollInProgress && pagerState.settledPage != selectedIndex) {
            pagerState.scrollToPage(selectedIndex)
        }
    }

    val latestGroups by rememberUpdatedState(groups)
    // Height of the floating connection bar, so the server lists can scroll clear of it.
    var bottomBarHeightPx by remember { mutableIntStateOf(0) }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                val currentGroups = latestGroups
                if (page in currentGroups.indices) {
                    onAction(MainAction.SelectGroup(currentGroups[page].id))
                }
            }
    }

    MainDialogs(
        showDelAllConfirm = showDelAllConfirm,
        onDismissDelAll = { showDelAllConfirm = false },
        onConfirmDelAll = { showDelAllConfirm = false; onAction(MainAction.RemoveAllServers) },
        showDelDuplicateConfirm = showDelDuplicateConfirm,
        onDismissDelDuplicate = { showDelDuplicateConfirm = false },
        onConfirmDelDuplicate = { showDelDuplicateConfirm = false; onAction(MainAction.RemoveDuplicateServers) },
        showDelInvalidConfirm = showDelInvalidConfirm,
        onDismissDelInvalid = { showDelInvalidConfirm = false },
        onConfirmDelInvalid = { showDelInvalidConfirm = false; onAction(MainAction.RemoveInvalidServers) },
        showRemoveConfirm = showRemoveConfirm,
        onDismissRemove = { showRemoveConfirm = null },
        onConfirmRemove = { guid -> showRemoveConfirm = null; onAction(MainAction.RemoveServer(guid)) }
    )

    if (shareTarget != null) {
        val (guid, profile, more) = shareTarget!!
        ShareMethodDialog(
            guid = guid,
            profile = profile,
            more = more,
            onDismiss = { shareTarget = null },
            onAction = onAction,
            onRemove = removeServer,
        )
    }
    if (shareQRCodeBitmap != null) {
        QRCodeDialog(bitmap = shareQRCodeBitmap, onDismiss = { onAction(MainAction.DismissQRCodeDialog) })
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                MainDrawerContent(
                    drawerState = drawerState,
                    onNavigate = { route ->
                        scope.launch { drawerState.close() }
                        onNavigate(route)
                    }
                )
            }
        ) {
        Scaffold(
            contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
            topBar = {
                MainTopBar(
                    isLoading = isLoading,
                    showSearch = showSearch,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { query: String ->
                        searchQuery = query
                        onAction(MainAction.Search(query))
                    },
                    onSearchClose = {
                        searchQuery = ""
                        onAction(MainAction.Search(""))
                        showSearch = false
                    },
                    onSearchToggle = { show: Boolean -> showSearch = show },
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onAction = onAction,
                    onMoreMenuAction = { action ->
                        when (action) {
                            MainMoreMenuAction.RestartService -> onAction(MainAction.RestartService)
                            MainMoreMenuAction.DeleteAll -> showDelAllConfirm = true
                            MainMoreMenuAction.DeleteDuplicate -> showDelDuplicateConfirm = true
                            MainMoreMenuAction.DeleteInvalid -> showDelInvalidConfirm = true
                            MainMoreMenuAction.ExportAll -> onAction(MainAction.ExportAll)
                            MainMoreMenuAction.LocateSelected -> onAction(MainAction.LocateSelectedServer)
                            MainMoreMenuAction.SortByTestResults -> onAction(MainAction.SortByTestResults)
                            MainMoreMenuAction.TestAll -> onAction(MainAction.TestAllServers)
                            MainMoreMenuAction.TestAllRealPing -> onAction(MainAction.TestRealAllServers)
                            MainMoreMenuAction.UpdateSubscriptions -> onAction(MainAction.UpdateSubscriptions)
                        }
                    }
                )
            },
            floatingActionButton = {},
        ) { innerPadding ->
            val layoutDirection = LocalLayoutDirection.current
            val bottomBarHeight = with(LocalDensity.current) { bottomBarHeightPx.toDp() }

            Box(Modifier.fillMaxSize()) {
            if (groups.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        // No bottom padding: the lists run behind the floating connection bar.
                        .padding(
                            start = innerPadding.calculateStartPadding(layoutDirection),
                            top = innerPadding.calculateTopPadding(),
                            end = innerPadding.calculateEndPadding(layoutDirection)
                        )
                ) {
                    if (groups.size > 1) {
                        GroupTabBar(
                            groups = groups,
                            selectedTabIndex = pagerState.currentPage.coerceIn(0, groups.lastIndex),
                            mainViewModel = mainViewModel,
                            onTabClick = { targetIndex ->
                                scope.launch {
                                    pagerState.navigateToPageOptimized(
                                        targetPage = targetIndex,
                                        animateAdjacentPage = true
                                    )
                                }
                            }
                        )
                    }

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        userScrollEnabled = true,
                        beyondViewportPageCount = 1,
                        key = { page -> groups.getOrNull(page)?.id ?: "group-page-$page" }
                    ) { page ->
                        val group = groups.getOrNull(page) ?: return@HorizontalPager

                        GroupPagerPage(
                            groupId = group.id,
                            mainViewModel = mainViewModel,
                            selectedGuid = selectedGuid,
                            locateTarget = uiState.locateTarget,
                            doubleColumnDisplay = doubleColumnDisplay,
                            searchQuery = searchQuery,
                            lazyListStates = lazyListStates,
                            lazyGridStates = lazyGridStates,
                            onSelectServer = onSelectServer,
                            onEditServer = onEditServer,
                            onShareServer = { guid, profile ->
                                shareTarget = Triple(guid, profile, false)
                            },
                            onMoreServer = { guid, profile ->
                                shareTarget = Triple(guid, profile, true)
                            },
                            onRemoveServer = removeServer,
                            usageInfo = group.usageInfo,
                            groupTitle = group.remarks,
                            onOpenLink = onOpenSubLink,
                            contentPadding = PaddingValues(
                                start = 0.dp,
                                top = 0.dp,
                                end = 0.dp,
                                bottom = bottomBarHeight + 8.dp
                            )
                        )
                    }
                }
            }
            MainBottomBar(
                displayText = displayText,
                status = uiState.status,
                connectionUi = uiState.connectionUi,
                isRunning = isRunning,
                isDarkTheme = isDarkTheme,
                onAction = onAction,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { bottomBarHeightPx = it.height }
            )
            }
        }
        }
        RgbScreenBorderOverlay(
            isRunning = isRunning && uiState.rgbBorderEnabled,
            modifier = Modifier.matchParentSize()
        )
    }
}

@Composable
private fun RgbScreenBorderOverlay(
    isRunning: Boolean,
    modifier: Modifier = Modifier,
) {
    val borderAlpha by animateFloatAsState(
        targetValue = if (isRunning) 1f else 0f,
        animationSpec = tween(durationMillis = 420),
        label = "rgbBorderAlpha"
    )
    if (!isRunning && borderAlpha <= 0.01f) return

    val infiniteTransition = rememberInfiniteTransition(label = "rgbScreenBorder")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rgbBorderAngle"
    )
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rgbGlowPulse"
    )
    val matrix = remember { Matrix() }

    Box(
        modifier = modifier
            .testTag("rgb_screen_border")
            .drawWithCache {
                val intColors = intArrayOf(
                    cyberRgbRed.toArgb(),
                    cyberRgbOrange.toArgb(),
                    cyberRgbYellow.toArgb(),
                    cyberRgbLime.toArgb(),
                    cyberRgbEmerald.toArgb(),
                    cyberCyanNeon.toArgb(),
                    cyberRgbBlue.toArgb(),
                    cyberRgbViolet.toArgb(),
                    cyberRgbMagenta.toArgb(),
                    cyberRgbRed.toArgb(),
                )
                val cx = size.width / 2f
                val cy = size.height / 2f
                val sweepShader = SweepGradient(cx, cy, intColors, null)
                val brush = ShaderBrush(sweepShader)
                val outerAuraWidthPx = 16.dp.toPx()
                val midGlowWidthPx = 8.5.dp.toPx()
                val strokeWidthPx = 4.5.dp.toPx()
                val outerCornerRadiusPx = 22.dp.toPx()
                val innerCornerRadiusPx = 18.dp.toPx()

                onDrawBehind {
                    if (size.width <= 0f || size.height <= 0f) return@onDrawBehind
                    matrix.setRotate(angle, cx, cy)
                    sweepShader.setLocalMatrix(matrix)

                    val outerInset = outerAuraWidthPx / 2f
                    drawRoundRect(
                        brush = brush,
                        topLeft = Offset(outerInset, outerInset),
                        size = Size(
                            width = (size.width - outerAuraWidthPx).coerceAtLeast(0f),
                            height = (size.height - outerAuraWidthPx).coerceAtLeast(0f)
                        ),
                        cornerRadius = CornerRadius(outerCornerRadiusPx, outerCornerRadiusPx),
                        alpha = 0.22f * glowPulse * borderAlpha,
                        style = Stroke(width = outerAuraWidthPx)
                    )

                    val midInset = midGlowWidthPx / 2f
                    drawRoundRect(
                        brush = brush,
                        topLeft = Offset(midInset, midInset),
                        size = Size(
                            width = (size.width - midGlowWidthPx).coerceAtLeast(0f),
                            height = (size.height - midGlowWidthPx).coerceAtLeast(0f)
                        ),
                        cornerRadius = CornerRadius(outerCornerRadiusPx, outerCornerRadiusPx),
                        alpha = 0.48f * glowPulse * borderAlpha,
                        style = Stroke(width = midGlowWidthPx)
                    )

                    val strokeInset = strokeWidthPx / 2f
                    drawRoundRect(
                        brush = brush,
                        topLeft = Offset(strokeInset, strokeInset),
                        size = Size(
                            width = (size.width - strokeWidthPx).coerceAtLeast(0f),
                            height = (size.height - strokeWidthPx).coerceAtLeast(0f)
                        ),
                        cornerRadius = CornerRadius(innerCornerRadiusPx, innerCornerRadiusPx),
                        alpha = borderAlpha,
                        style = Stroke(width = strokeWidthPx)
                    )
                }
            }
    )
}

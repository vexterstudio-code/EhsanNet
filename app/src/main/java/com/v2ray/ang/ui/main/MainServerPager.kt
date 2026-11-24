package com.v2ray.ang.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.dto.LocateTarget
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.SubscriptionUsageInfo
import com.v2ray.ang.ui.compose.LocalDarkTheme
import com.v2ray.ang.ui.compose.ReorderableGridItem
import com.v2ray.ang.ui.compose.ReorderableListItem
import com.v2ray.ang.ui.compose.colorConfigType
import com.v2ray.ang.ui.compose.colorPing
import com.v2ray.ang.ui.compose.colorPingRed
import com.v2ray.ang.ui.compose.cyberBlueElectric
import com.v2ray.ang.ui.compose.cyberCardBorderDark
import com.v2ray.ang.ui.compose.cyberCardBorderLight
import com.v2ray.ang.ui.compose.cyberCardDark
import com.v2ray.ang.ui.compose.cyberCardLight
import com.v2ray.ang.ui.compose.cyberCardSelectedDark
import com.v2ray.ang.ui.compose.cyberCardSelectedLight
import com.v2ray.ang.ui.compose.cyberPingBgErrorDark
import com.v2ray.ang.ui.compose.cyberPingBgErrorLight
import com.v2ray.ang.ui.compose.cyberPingBgPositiveDark
import com.v2ray.ang.ui.compose.cyberPingBgPositiveLight
import com.v2ray.ang.ui.compose.cyberTypeBgDark
import com.v2ray.ang.ui.compose.cyberTypeBgLight
import com.v2ray.ang.ui.compose.verticalScrollbar
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.math.abs

private val SERVER_CARD_SHAPE = RoundedCornerShape(16.dp)
private val BADGE_PILL_SHAPE = RoundedCornerShape(8.dp)

@Composable
fun GroupPagerPage(
    groupId: String,
    mainViewModel: MainViewModel,
    selectedGuid: String?,
    locateTarget: LocateTarget?,
    doubleColumnDisplay: Boolean,
    searchQuery: String,
    lazyListStates: MutableMap<String, LazyListState>,
    lazyGridStates: MutableMap<String, LazyGridState>,
    onSelectServer: (String) -> Unit,
    onEditServer: (String, ProfileItem) -> Unit,
    onShareServer: (String, ProfileItem) -> Unit,
    onMoreServer: (String, ProfileItem) -> Unit,
    onRemoveServer: (String, String) -> Unit,
    usageInfo: SubscriptionUsageInfo?,
    groupTitle: String,
    onOpenLink: (String) -> Unit,
    contentPadding: PaddingValues
) {
    val groupStateFlow = remember(groupId) {
        mainViewModel.serverGroupState(groupId)
    }
    val groupState by groupStateFlow.collectAsStateWithLifecycle()
    val canReorder = groupId.isNotEmpty() && searchQuery.isEmpty()
    val actions = remember(
        onSelectServer,
        onEditServer,
        onShareServer,
        onMoreServer,
        onRemoveServer,
    ) {
        ServerRowActions(
            select = onSelectServer,
            edit = onEditServer,
            share = onShareServer,
            more = onMoreServer,
            remove = onRemoveServer,
        )
    }
    // The box floats over the list (the list scrolls underneath it), so the list is padded by the box height.
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    var infoHeightPx by remember { mutableIntStateOf(0) }
    val listPadding = if (usageInfo == null) {
        contentPadding
    } else {
        PaddingValues(
            start = contentPadding.calculateStartPadding(layoutDirection),
            top = contentPadding.calculateTopPadding() + with(density) { infoHeightPx.toDp() },
            end = contentPadding.calculateEndPadding(layoutDirection),
            bottom = contentPadding.calculateBottomPadding()
        )
    }

    Box(Modifier.fillMaxSize()) {
        if (groupState.rows.isEmpty()) {
            EmptyServerListHint(
                contentPadding = listPadding,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            ServerListPage(
                rows = groupState.rows,
                selectedGuid = selectedGuid,
                locateTarget = locateTarget?.takeIf { it.groupId == groupId },
                canReorder = canReorder,
                doubleColumnDisplay = doubleColumnDisplay,
                groupId = groupId,
                lazyListStates = lazyListStates,
                lazyGridStates = lazyGridStates,
                actions = actions,
                onLocateHandled = { mainViewModel.onAction(MainAction.LocateHandled) },
                onMoveServer = { fromIndex, toIndex ->
                    mainViewModel.moveServer(groupId, fromIndex, toIndex)
                },
                contentPadding = listPadding
            )
        }
        if (usageInfo != null) {
            SubscriptionInfoCard(
                info = usageInfo,
                fallbackTitle = groupTitle,
                onOpenLink = onOpenLink,
                onRefresh = { mainViewModel.onAction(MainAction.UpdateGroupSubscription(groupId)) },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .onSizeChanged { infoHeightPx = it.height }
            )
        }
    }
}

@Composable
private fun EmptyServerListHint(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.padding(contentPadding).padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_subscriptions_24dp),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.empty_servers_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

private class ServerRowActions(
    val select: (String) -> Unit,
    val edit: (String, ProfileItem) -> Unit,
    val share: (String, ProfileItem) -> Unit,
    val more: (String, ProfileItem) -> Unit,
    val remove: (String, String) -> Unit,
)

@Composable
private fun ServerListPage(
    rows: List<ServerRowUiModel>,
    selectedGuid: String?,
    locateTarget: LocateTarget?,
    canReorder: Boolean,
    doubleColumnDisplay: Boolean,
    groupId: String,
    lazyListStates: MutableMap<String, LazyListState>,
    lazyGridStates: MutableMap<String, LazyGridState>,
    actions: ServerRowActions,
    onLocateHandled: () -> Unit,
    onMoveServer: (Int, Int) -> Unit,
    contentPadding: PaddingValues
) {
    if (doubleColumnDisplay) {
        val gridState = remember(groupId) {
            lazyGridStates.getOrPut(groupId) { LazyGridState() }
        }
        val reorderableGridState = if (canReorder) {
            rememberReorderableLazyGridState(gridState) { from, to ->
                onMoveServer(from.index, to.index)
            }
        } else null

        LocateTargetEffect(locateTarget, rows, gridState, onLocateHandled)

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            modifier = Modifier
                .fillMaxSize()
                .verticalScrollbar(gridState),
            contentPadding = contentPadding
        ) {
            itemsIndexed(
                items = rows,
                key = { _, item -> item.guid },
                contentType = { _, _ -> "server_grid_card" }
            ) { _, row ->
                val isSelected = row.guid == selectedGuid
                if (canReorder && reorderableGridState != null) {
                    ReorderableItem(
                        reorderableGridState,
                        key = row.guid
                    ) { isDragging ->
                        ReorderableGridItem(
                            scope = this,
                            isDragging = isDragging
                        ) {
                            ServerListItem(
                                row = row,
                                isSelected = isSelected,
                                doubleColumnDisplay = true,
                                actions = actions
                            )
                        }
                    }
                } else {
                    ServerListItem(
                        row = row,
                        isSelected = isSelected,
                        doubleColumnDisplay = true,
                        actions = actions
                    )
                }
            }
        }
    } else {
        val listState = remember(groupId) {
            lazyListStates.getOrPut(groupId) { LazyListState() }
        }
        val reorderableState = if (canReorder) {
            rememberReorderableLazyListState(listState) { from, to ->
                onMoveServer(from.index, to.index)
            }
        } else null

        LocateTargetEffect(locateTarget, rows, listState, onLocateHandled)

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .verticalScrollbar(listState),
            contentPadding = contentPadding
        ) {
            itemsIndexed(
                items = rows,
                key = { _, item -> item.guid },
                contentType = { _, _ -> "server_list_card" }
            ) { _, row ->
                val isSelected = row.guid == selectedGuid
                if (canReorder && reorderableState != null) {
                    ReorderableItem(
                        reorderableState,
                        key = row.guid
                    ) { isDragging ->
                        ReorderableListItem(
                            scope = this,
                            isDragging = isDragging
                        ) {
                            ServerListItem(
                                row = row,
                                isSelected = isSelected,
                                doubleColumnDisplay = false,
                                actions = actions
                            )
                        }
                    }
                } else {
                    ServerListItem(
                        row = row,
                        isSelected = isSelected,
                        doubleColumnDisplay = false,
                        actions = actions
                    )
                }
            }
        }
    }
}

@Composable
private fun LocateTargetEffect(
    target: LocateTarget?,
    rows: List<ServerRowUiModel>,
    state: LazyListState,
    onHandled: () -> Unit,
) {
    if (target == null) return
    LaunchedEffect(target, rows) {
        val index = rows.indexOfFirst { it.guid == target.serverGuid }
        if (index < 0) return@LaunchedEffect
        state.scrollToItem(index, -state.layoutInfo.viewportSize.height / 3)
        onHandled()
    }
}

@Composable
private fun LocateTargetEffect(
    target: LocateTarget?,
    rows: List<ServerRowUiModel>,
    state: LazyGridState,
    onHandled: () -> Unit,
) {
    if (target == null) return
    LaunchedEffect(target, rows) {
        val index = rows.indexOfFirst { it.guid == target.serverGuid }
        if (index < 0) return@LaunchedEffect
        state.scrollToItem(index, -state.layoutInfo.viewportSize.height / 3)
        onHandled()
    }
}

@Composable
private fun ServerListItem(
    row: ServerRowUiModel,
    isSelected: Boolean,
    doubleColumnDisplay: Boolean,
    actions: ServerRowActions
) {
    val isDark = LocalDarkTheme.current
    val testResult = if (row.testDelayMillis == 0L) {
        ""
    } else {
        stringResource(R.string.server_test_delay_value, row.testDelayMillis)
    }
    val selectedStateDescription = if (isSelected) {
        stringResource(R.string.acc_selected_server)
    } else {
        null
    }

    val cardColor = when {
        isSelected && isDark -> cyberCardSelectedDark
        isSelected -> cyberCardSelectedLight
        isDark -> cyberCardDark
        else -> cyberCardLight
    }
    val borderColor = when {
        isSelected -> cyberBlueElectric.copy(alpha = if (isDark) 0.72f else 0.85f)
        isDark -> cyberCardBorderDark
        else -> cyberCardBorderLight
    }
    val indicatorColor = MaterialTheme.colorScheme.primary
    val outerHorizontalPadding = if (doubleColumnDisplay) 6.dp else 12.dp

    Surface(
        shape = SERVER_CARD_SHAPE,
        color = cardColor,
        border = BorderStroke(if (isSelected) 1.3.dp else 1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = outerHorizontalPadding, vertical = 4.dp)
            .clip(SERVER_CARD_SHAPE)
            .semantics {
                if (selectedStateDescription != null) {
                    stateDescription = selectedStateDescription
                }
            }
            .clickable { actions.select(row.guid) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    if (isSelected) {
                        val barWidth = 4.dp.toPx()
                        val verticalInset = 12.dp.toPx()
                        val barHeight = (size.height - verticalInset * 2f).coerceAtLeast(12.dp.toPx())
                        drawRoundRect(
                            color = indicatorColor,
                            topLeft = Offset(x = 6.dp.toPx(), y = (size.height - barHeight) / 2f),
                            size = Size(width = barWidth, height = barHeight),
                            cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                        )
                    }
                }
                .padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.remarks,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        lineBreak = LineBreak.Paragraph
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (doubleColumnDisplay) {
                    IconButton(
                        onClick = { actions.more(row.guid, row.profile) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .size(36.dp)
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_more_vert_24dp),
                            stringResource(R.string.acc_more),
                            Modifier.size(20.dp)
                        )
                    }
                } else {
                    IconButton(
                        onClick = { actions.share(row.guid, row.profile) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .size(36.dp)
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_share_24dp),
                            stringResource(R.string.title_configuration_share),
                            Modifier.size(19.dp)
                        )
                    }
                    IconButton(
                        onClick = { actions.edit(row.guid, row.profile) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .size(36.dp)
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_edit_24dp),
                            stringResource(R.string.acc_edit),
                            Modifier.size(19.dp)
                        )
                    }
                    IconButton(
                        onClick = { actions.remove(row.guid, row.remarks) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .size(36.dp)
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_delete_24dp),
                            stringResource(R.string.acc_delete),
                            Modifier.size(19.dp)
                        )
                    }
                }
            }

            if (row.statistics.isNotBlank() || row.subscriptionBadge.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (row.subscriptionBadge.isNotBlank()) {
                        Box(
                            Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                            Alignment.Center
                        ) {
                            Text(
                                text = row.subscriptionBadge.uppercase(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Text(
                        text = row.statistics,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clip(BADGE_PILL_SHAPE)
                        .background(if (isDark) cyberTypeBgDark else cyberTypeBgLight)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = row.typeDescription,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colorConfigType,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (testResult.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    val isError = row.testDelayMillis < 0L
                    val pingBg = when {
                        isError && isDark -> cyberPingBgErrorDark
                        isError -> cyberPingBgErrorLight
                        isDark -> cyberPingBgPositiveDark
                        else -> cyberPingBgPositiveLight
                    }
                    Box(
                        modifier = Modifier
                            .clip(BADGE_PILL_SHAPE)
                            .background(pingBg)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = testResult,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isError) colorPingRed else colorPing,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

internal suspend fun PagerState.navigateToPageOptimized(
    targetPage: Int,
    animateAdjacentPage: Boolean = true
) {
    if (pageCount <= 0) return
    val target = targetPage.coerceIn(0, pageCount - 1)
    val current = settledPage.coerceIn(0, pageCount - 1)
    if (target == current) return

    if (abs(target - current) == 1 && animateAdjacentPage) {
        animateScrollToPage(target)
    } else {
        scrollToPage(target)
    }
}

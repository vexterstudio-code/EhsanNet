package com.v2ray.ang.ui.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.cyberAlertRed
import com.v2ray.ang.ui.compose.cyberBlueDeep
import com.v2ray.ang.ui.compose.cyberBlueElectric
import com.v2ray.ang.ui.compose.cyberBlueMid
import com.v2ray.ang.ui.compose.cyberCyanNeon
import com.v2ray.ang.ui.compose.cyberPingBgErrorDark
import com.v2ray.ang.ui.compose.cyberPingBgErrorLight
import com.v2ray.ang.ui.compose.cyberPingBgPositiveDark
import com.v2ray.ang.ui.compose.cyberPingBgPositiveLight
import com.v2ray.ang.ui.compose.cyberRgbEmerald
import com.v2ray.ang.ui.compose.cyberRgbMagenta
import com.v2ray.ang.ui.compose.cyberRgbViolet
import com.v2ray.ang.ui.compose.cyberTrackBorderLight
import com.v2ray.ang.ui.compose.cyberTrackDark
import com.v2ray.ang.ui.compose.cyberTrackLight
import kotlin.math.PI
import kotlin.math.sin

private val KNOB_SIZE = 58.dp
private val TRACK_HEIGHT = 68.dp
private val ATTACHED_TAB_VISUAL_HEIGHT = 34.dp
private val ATTACHED_TAB_BOTTOM_EXTRA = 14.dp
private val TAB_SIDE_INSET = 44.dp
private val TAB_NECK_RADIUS = 8.dp
private val TAB_BOTTOM_CORNER_RADIUS = 16.dp
private val BAR_MAX_WIDTH = 490.dp

/**
 * Builds one seamless unified silhouette combining the main capsule pill and the attached
 * underside mode tabs with smooth concave neck fillets — eliminating double borders or shadow artifacts.
 */
private fun buildUnifiedPillWithTabsPath(
    width: Float,
    pillHeight: Float,
    tabHeight: Float,
    tabInsetPx: Float,
    neckRadiusPx: Float,
    tabCornerRadiusPx: Float,
    strokeInsetPx: Float = 0f,
): Path {
    val left = strokeInsetPx
    val top = strokeInsetPx
    val right = (width - strokeInsetPx).coerceAtLeast(left + 2f)
    val effectivePillH = (pillHeight - strokeInsetPx * 2f).coerceAtLeast(4f)
    val pillBottom = top + effectivePillH
    val pillR = effectivePillH / 2f
    val totalBottom = (pillHeight + tabHeight - strokeInsetPx).coerceAtLeast(pillBottom + 2f)

    val safeTabInset = tabInsetPx.coerceIn(pillR + neckRadiusPx + 2f, (width * 0.30f).coerceAtLeast(pillR + neckRadiusPx + 2f))
    val tabLeft = safeTabInset + strokeInsetPx
    val tabRight = (width - safeTabInset - strokeInsetPx).coerceAtLeast(tabLeft + tabCornerRadiusPx * 2f + 8f)
    val neckR = neckRadiusPx.coerceAtMost(tabHeight * 0.4f)
    val cornerR = tabCornerRadiusPx.coerceAtMost((totalBottom - pillBottom - neckR).coerceAtLeast(4f))

    return Path().apply {
        moveTo(left + pillR, top)
        lineTo(right - pillR, top)
        arcTo(
            rect = Rect(right - effectivePillH, top, right, pillBottom),
            startAngleDegrees = -90f,
            sweepAngleDegrees = 180f,
            forceMoveTo = false,
        )
        lineTo(tabRight + neckR, pillBottom)
        quadraticTo(tabRight, pillBottom, tabRight, pillBottom + neckR)
        lineTo(tabRight, totalBottom - cornerR)
        quadraticTo(tabRight, totalBottom, tabRight - cornerR, totalBottom)
        lineTo(tabLeft + cornerR, totalBottom)
        quadraticTo(tabLeft, totalBottom, tabLeft, totalBottom - cornerR)
        lineTo(tabLeft, pillBottom + neckR)
        quadraticTo(tabLeft, pillBottom, tabLeft - neckR, pillBottom)
        lineTo(left + pillR, pillBottom)
        arcTo(
            rect = Rect(left, top, left + effectivePillH, pillBottom),
            startAngleDegrees = 90f,
            sweepAngleDegrees = 180f,
            forceMoveTo = false,
        )
        close()
    }
}

/**
 * Unified Cyber RGB connection pill + rotating electric-blue RGB orb with live Download (↓) &
 * Upload (↑) telemetry and two seamlessly fused underside tabs (Normal / Boost).
 */
@Composable
fun MainBottomBar(
    displayText: String,
    status: MainStatus,
    connectionUi: ConnectionUiState,
    isRunning: Boolean,
    isDarkTheme: Boolean,
    onAction: (MainAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val textLayoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current
    val connStatus = connectionUi.connectionStatus
    val isScanningBoost = connStatus == ConnectionStatus.SCANNING_BOOST
    val isConnecting = connStatus == ConnectionStatus.CONNECTING
    val isDisconnecting = connStatus == ConnectionStatus.DISCONNECTING
    val isTransitioning = connStatus.isTransitioning
    val isTestingPing = status is MainStatus.Testing
    val effectivelyConnected = isRunning || connStatus == ConnectionStatus.CONNECTED

    val connectBurstAnim = remember { Animatable(0f) }
    val pingResultFlashAnim = remember { Animatable(0f) }
    var lastResultSuccess by remember { mutableStateOf(true) }

    LaunchedEffect(effectivelyConnected) {
        if (effectivelyConnected) {
            connectBurstAnim.snapTo(0f)
            connectBurstAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing),
            )
        }
    }

    LaunchedEffect(status) {
        if (status is MainStatus.ConnectionTest) {
            lastResultSuccess = status.result.delayMillis >= 0
            pingResultFlashAnim.snapTo(0f)
            pingResultFlashAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 750, easing = FastOutSlowInEasing),
            )
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "cyberRgbBar")
    val rgbAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rgbRingAngle",
    )
    val hudReverseAngle by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "hudReverseAngle",
    )
    val pulsePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulsePhase",
    )
    val scanBeamFraction by infiniteTransition.animateFloat(
        initialValue = -0.25f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1050, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pingScanBeam",
    )
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "cyberWave",
    )

    val rgbStops = remember {
        listOf(
            cyberCyanNeon,
            cyberBlueElectric,
            cyberBlueMid,
            cyberRgbViolet,
            cyberRgbMagenta,
            cyberRgbEmerald,
            cyberCyanNeon,
        )
    }

    val trackBaseColor by animateColorAsState(
        targetValue = if (isDarkTheme) cyberTrackDark else cyberTrackLight,
        animationSpec = tween(300),
        label = "trackBaseColor",
    )

    val knobInset = (TRACK_HEIGHT - KNOB_SIZE) / 2
    val activeGlow = effectivelyConnected || isConnecting || isScanningBoost || isTestingPing

    val buttonInteraction = remember { MutableInteractionSource() }
    val isButtonPressed by buttonInteraction.collectIsPressedAsState()
    val buttonScale by animateFloatAsState(
        targetValue = when {
            isButtonPressed -> 0.90f
            isConnecting || isScanningBoost -> 1.04f + 0.05f * sin(pulsePhase * 2f * PI.toFloat())
            isTestingPing -> 1.03f
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "cyberButtonScale",
    )

    val iconSpin = remember { Animatable(0f) }
    LaunchedEffect(isConnecting || isScanningBoost, effectivelyConnected) {
        if (isConnecting || isScanningBoost) {
            iconSpin.snapTo(0f)
            iconSpin.animateTo(
                targetValue = 720f,
                animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            )
        } else {
            iconSpin.animateTo(
                targetValue = 0f,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            )
        }
    }

    val pillHeightPx = with(density) { TRACK_HEIGHT.toPx() }
    val tabHeightPx = with(density) { ATTACHED_TAB_VISUAL_HEIGHT.toPx() }
    val tabInsetPx = with(density) { TAB_SIDE_INSET.toPx() }
    val neckRadiusPx = with(density) { TAB_NECK_RADIUS.toPx() }
    val tabCornerRadiusPx = with(density) { TAB_BOTTOM_CORNER_RADIUS.toPx() }

    val unifiedClipShape = remember(pillHeightPx, tabHeightPx, tabInsetPx, neckRadiusPx, tabCornerRadiusPx) {
        GenericShape { size, _ ->
            addPath(
                buildUnifiedPillWithTabsPath(
                    width = size.width,
                    pillHeight = pillHeightPx,
                    tabHeight = tabHeightPx,
                    tabInsetPx = tabInsetPx,
                    neckRadiusPx = neckRadiusPx,
                    tabCornerRadiusPx = tabCornerRadiusPx,
                )
            )
        }
    }

    val visualTotalHeight = TRACK_HEIGHT + ATTACHED_TAB_VISUAL_HEIGHT

    Box(
        modifier = modifier
            .widthIn(max = BAR_MAX_WIDTH)
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 0.dp)
            .height(visualTotalHeight + ATTACHED_TAB_BOTTOM_EXTRA),
    ) {
        // Single unified Cyber-Glass body + single continuous RGB/Blue border (no extra shapes behind)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(visualTotalHeight)
                .align(Alignment.TopCenter),
        ) {
            // 1. Unified background fill & single unbroken outer outline
            Canvas(modifier = Modifier.fillMaxSize()) {
                val fillPath = buildUnifiedPillWithTabsPath(
                    width = size.width,
                    pillHeight = pillHeightPx,
                    tabHeight = tabHeightPx,
                    tabInsetPx = tabInsetPx,
                    neckRadiusPx = neckRadiusPx,
                    tabCornerRadiusPx = tabCornerRadiusPx,
                )
                drawPath(path = fillPath, color = trackBaseColor)

                val strokeWidth = if (activeGlow) 2.0.dp.toPx() else 1.4.dp.toPx()
                val outlinePath = buildUnifiedPillWithTabsPath(
                    width = size.width,
                    pillHeight = pillHeightPx,
                    tabHeight = tabHeightPx,
                    tabInsetPx = tabInsetPx,
                    neckRadiusPx = neckRadiusPx,
                    tabCornerRadiusPx = tabCornerRadiusPx,
                    strokeInsetPx = strokeWidth / 2f,
                )

                val borderAlpha = when {
                    isTestingPing || isConnecting || isScanningBoost -> 0.96f
                    effectivelyConnected -> 0.88f
                    isDarkTheme -> 0.56f
                    else -> 0.66f
                }
                val borderBrush = if (activeGlow) {
                    Brush.sweepGradient(
                        colors = rgbStops.map { it.copy(alpha = borderAlpha) },
                        center = Offset(
                            x = size.width * (0.5f + 0.35f * sin(rgbAngle * PI.toFloat() / 180f)),
                            y = pillHeightPx / 2f,
                        ),
                    )
                } else {
                    Brush.horizontalGradient(
                        colors = listOf(
                            if (isDarkTheme) cyberBlueMid.copy(alpha = 0.55f) else cyberTrackBorderLight,
                            cyberBlueElectric.copy(alpha = borderAlpha),
                            cyberCyanNeon.copy(alpha = borderAlpha),
                        )
                    )
                }

                // Subtle soft outer neon halo directly along the exact unified path (never an oval/box)
                if (activeGlow) {
                    drawPath(
                        path = outlinePath,
                        brush = borderBrush,
                        alpha = 0.18f,
                        style = Stroke(width = strokeWidth * 3.2f, cap = StrokeCap.Round),
                    )
                }

                drawPath(
                    path = outlinePath,
                    brush = borderBrush,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )

                // Hairline seam & center divider inside the attached underside tab dock
                val safeInset = tabInsetPx.coerceIn(
                    pillHeightPx / 2f + neckRadiusPx + 2f,
                    (size.width * 0.30f).coerceAtLeast(pillHeightPx / 2f + neckRadiusPx + 2f),
                )
                val tabLeft = safeInset
                val tabRight = (size.width - safeInset).coerceAtLeast(tabLeft + 16f)
                val dividerColor = (if (isDarkTheme) cyberCyanNeon else cyberBlueMid).copy(alpha = 0.16f)
                drawLine(
                    color = dividerColor,
                    start = Offset(tabLeft + 10.dp.toPx(), pillHeightPx),
                    end = Offset(tabRight - 10.dp.toPx(), pillHeightPx),
                    strokeWidth = 0.8.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = dividerColor,
                    start = Offset((tabLeft + tabRight) / 2f, pillHeightPx + 7.dp.toPx()),
                    end = Offset((tabLeft + tabRight) / 2f, pillHeightPx + tabHeightPx - 7.dp.toPx()),
                    strokeWidth = 0.8.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }

            // 2. Clipped interior sweeps (orb glow, laser ping beam, connect shockwave) inside top pill
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(TRACK_HEIGHT)
                        .align(Alignment.TopCenter)
                        .clip(unifiedClipShape),
                ) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .testTag("status_bar_test_area")
                            .clip(CircleShape)
                            .clickable(
                                enabled = effectivelyConnected && !isScanningBoost,
                                onClick = { onAction(MainAction.TestCurrentServer) },
                            ),
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val radius = size.height / 2f
                            val orbCenter = Offset(
                                x = size.width - knobInset.toPx() - (KNOB_SIZE.toPx() / 2f),
                                y = size.height / 2f,
                            )
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        cyberBlueElectric.copy(
                                            alpha = when {
                                                isConnecting || isScanningBoost -> 0.36f
                                                effectivelyConnected -> 0.24f
                                                else -> 0.13f
                                            }
                                        ),
                                        Color.Transparent,
                                    ),
                                    center = orbCenter,
                                    radius = size.height * 1.45f,
                                ),
                                center = orbCenter,
                                radius = size.height * 1.45f,
                            )

                            if (isConnecting || isScanningBoost) {
                                val beamX = size.width * (1f - pulsePhase)
                                val beamWidth = size.width * 0.48f
                                drawRoundRect(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            cyberRgbViolet.copy(alpha = 0.22f),
                                            cyberCyanNeon.copy(alpha = 0.42f),
                                            cyberBlueElectric.copy(alpha = 0.25f),
                                            Color.Transparent,
                                        ),
                                        startX = beamX - beamWidth / 2f,
                                        endX = beamX + beamWidth / 2f,
                                    ),
                                    cornerRadius = CornerRadius(radius, radius),
                                )
                            }

                            if (isTestingPing) {
                                val scanX = size.width * scanBeamFraction
                                val scanWidth = size.width * 0.42f
                                drawRoundRect(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            cyberBlueMid.copy(alpha = 0.20f),
                                            cyberCyanNeon.copy(alpha = 0.48f),
                                            cyberRgbEmerald.copy(alpha = 0.34f),
                                            cyberRgbMagenta.copy(alpha = 0.20f),
                                            Color.Transparent,
                                        ),
                                        startX = scanX - scanWidth / 2f,
                                        endX = scanX + scanWidth / 2f,
                                    ),
                                    cornerRadius = CornerRadius(radius, radius),
                                )

                                val laserX = scanX.coerceIn(radius * 0.3f, size.width - radius * 0.3f)
                                drawLine(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            cyberCyanNeon.copy(alpha = 0.9f),
                                            cyberRgbEmerald.copy(alpha = 0.9f),
                                            Color.Transparent,
                                        )
                                    ),
                                    start = Offset(laserX, 4.dp.toPx()),
                                    end = Offset(laserX, size.height - 4.dp.toPx()),
                                    strokeWidth = 2.5.dp.toPx(),
                                    cap = StrokeCap.Round,
                                )
                            }

                            if (connectBurstAnim.value in 0.01f..0.99f) {
                                val p = connectBurstAnim.value
                                val burstRadius = (KNOB_SIZE.toPx() / 2f) + p * size.width
                                drawCircle(
                                    color = cyberCyanNeon.copy(alpha = (1f - p) * 0.45f),
                                    radius = burstRadius,
                                    center = orbCenter,
                                    style = Stroke(width = (3.dp.toPx() * (1f - p)).coerceAtLeast(1f)),
                                )
                            }

                            if (pingResultFlashAnim.value in 0.01f..0.99f) {
                                val p = pingResultFlashAnim.value
                                val flashColor = if (lastResultSuccess) cyberRgbEmerald else cyberAlertRed
                                drawRoundRect(
                                    color = flashColor.copy(alpha = (1f - p) * 0.26f),
                                    cornerRadius = CornerRadius(radius, radius),
                                )
                            }
                        }
                    }

                    // Status + Subscription + Live ↓/↑ Speed Telemetry inside the Pill
                    CompositionLocalProvider(LocalLayoutDirection provides textLayoutDirection) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .fillMaxWidth()
                                .absolutePadding(left = 18.dp, right = KNOB_SIZE + knobInset + 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AnimatedVisibility(
                                visible = isTestingPing || isConnecting || isScanningBoost || isDisconnecting,
                                enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.7f),
                                exit = fadeOut(tween(180)) + scaleOut(targetScale = 0.7f),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CyberSignalWaveform(
                                        phase = wavePhase,
                                        isPingMode = isTestingPing || isScanningBoost,
                                        modifier = Modifier.size(width = 22.dp, height = 22.dp),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                            }

                            PillTelemetryContent(
                                displayText = displayText,
                                status = status,
                                connectionUi = connectionUi,
                                effectivelyConnected = effectivelyConnected,
                                isTestingPing = isTestingPing,
                                isDarkTheme = isDarkTheme,
                                onCancelBoost = { onAction(MainAction.CancelBoostScan) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    // Rotating Electric-Blue Multi-Color RGB Orb Button
                    // Uniform across Normal & Boost; Boost distinguishes its idle icon with a lightning bolt.
                    val buttonDescription = stringResource(
                        if (isScanningBoost) R.string.acc_cancel_boost
                        else if (effectivelyConnected) R.string.acc_stop
                        else if (connectionUi.activeMode == ConnectionMode.BOOST) R.string.acc_connect_boost
                        else R.string.acc_start
                    )
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = knobInset)
                            .size(KNOB_SIZE)
                            .minimumInteractiveComponentSize()
                            .graphicsLayer {
                                scaleX = buttonScale
                                scaleY = buttonScale
                            }
                            .testTag("connect_button")
                            .semantics { contentDescription = buttonDescription }
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = buttonInteraction,
                                indication = null,
                                enabled = !isConnecting && !isDisconnecting,
                                role = Role.Button,
                                onClick = {
                                    if (isScanningBoost) {
                                        onAction(MainAction.CancelBoostScan)
                                    } else {
                                        onAction(MainAction.ToggleService)
                                    }
                                },
                            ),
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val outerR = size.minDimension / 2f

                            if (isConnecting || isScanningBoost || isTestingPing) {
                                val ringProgress = pulsePhase
                                drawCircle(
                                    color = (if (isTestingPing) cyberRgbEmerald else cyberCyanNeon)
                                        .copy(alpha = (1f - ringProgress) * 0.65f),
                                    radius = outerR * (0.65f + 0.34f * ringProgress),
                                    center = center,
                                    style = Stroke(width = 2.dp.toPx()),
                                )
                            }

                            val coreColors = when {
                                effectivelyConnected -> listOf(
                                    cyberCyanNeon,
                                    cyberBlueElectric,
                                    cyberBlueMid,
                                    cyberBlueDeep,
                                )
                                isConnecting || isScanningBoost -> listOf(
                                    Color.White.copy(alpha = 0.9f),
                                    cyberCyanNeon,
                                    cyberBlueMid,
                                    cyberBlueDeep,
                                )
                                else -> listOf(
                                    cyberBlueElectric,
                                    cyberBlueMid,
                                    cyberBlueDeep,
                                )
                            }
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = coreColors,
                                    center = Offset(center.x - outerR * 0.22f, center.y - outerR * 0.25f),
                                    radius = outerR * 1.15f,
                                ),
                                radius = outerR - 3.dp.toPx(),
                                center = center,
                            )

                            val ringSpeedMultiplier = when {
                                isConnecting || isScanningBoost -> 2.6f
                                isTestingPing -> 2.0f
                                effectivelyConnected -> 1.3f
                                else -> 0.7f
                            }
                            rotate(degrees = (rgbAngle * ringSpeedMultiplier) % 360f, pivot = center) {
                                drawCircle(
                                    brush = Brush.sweepGradient(
                                        colors = rgbStops,
                                        center = center,
                                    ),
                                    radius = outerR - 2.dp.toPx(),
                                    center = center,
                                    style = Stroke(
                                        width = if (activeGlow) 3.4.dp.toPx() else 2.6.dp.toPx(),
                                    ),
                                )
                            }

                            rotate(degrees = hudReverseAngle, pivot = center) {
                                val hudRadius = outerR - 6.5.dp.toPx()
                                val arcTopLeft = Offset(center.x - hudRadius, center.y - hudRadius)
                                val arcSize = Size(hudRadius * 2f, hudRadius * 2f)
                                val hudAlpha = if (activeGlow) 0.9f else 0.55f
                                drawArc(
                                    color = cyberCyanNeon.copy(alpha = hudAlpha),
                                    startAngle = 0f,
                                    sweepAngle = 62f,
                                    useCenter = false,
                                    topLeft = arcTopLeft,
                                    size = arcSize,
                                    style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round),
                                )
                                drawArc(
                                    color = cyberRgbMagenta.copy(alpha = hudAlpha),
                                    startAngle = 120f,
                                    sweepAngle = 55f,
                                    useCenter = false,
                                    topLeft = arcTopLeft,
                                    size = arcSize,
                                    style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round),
                                )
                                drawArc(
                                    color = cyberRgbEmerald.copy(alpha = hudAlpha),
                                    startAngle = 240f,
                                    sweepAngle = 60f,
                                    useCenter = false,
                                    topLeft = arcTopLeft,
                                    size = arcSize,
                                    style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round),
                                )
                            }

                            val specRadius = outerR - 5.dp.toPx()
                            drawArc(
                                color = Color.White.copy(alpha = if (effectivelyConnected || isConnecting) 0.42f else 0.25f),
                                startAngle = 215f,
                                sweepAngle = 110f,
                                useCenter = false,
                                topLeft = Offset(center.x - specRadius, center.y - specRadius),
                                size = Size(specRadius * 2f, specRadius * 2f),
                                style = Stroke(width = 1.3.dp.toPx(), cap = StrokeCap.Round),
                            )
                        }

                        AnimatedContent(
                            targetState = Pair(effectivelyConnected, connectionUi.activeMode),
                            transitionSpec = {
                                (scaleIn(
                                    initialScale = 0.55f,
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                ) + fadeIn(tween(180)))
                                    .togetherWith(scaleOut(targetScale = 0.55f) + fadeOut(tween(140)))
                            },
                            label = "connectIconTransition",
                        ) { (running, mode) ->
                            val iconRes = when {
                                running -> R.drawable.ic_stop_24dp
                                mode == ConnectionMode.BOOST -> R.drawable.ic_bolt_24dp
                                else -> R.drawable.ic_play_24dp
                            }
                            Icon(
                                painter = painterResource(iconRes),
                                contentDescription = buttonDescription,
                                tint = Color.White,
                                modifier = Modifier
                                    .size(24.dp)
                                    .graphicsLayer {
                                        rotationZ = if (!running) iconSpin.value else 0f
                                    },
                            )
                        }
                    }
                }
            }
        }

        // 3. Minimalist interactive Normal / Boost tabs anchored inside the unified bottom lip
        // (Zero inner background boxes — only crisp text + glowing sliding neon underline)
        AttachedModeTabsOverlay(
            activeMode = connectionUi.activeMode,
            enabled = !isTransitioning && !isDisconnecting,
            isDarkTheme = isDarkTheme,
            tabInset = TAB_SIDE_INSET,
            onSelectMode = { mode -> onAction(MainAction.SelectConnectionMode(mode)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = TRACK_HEIGHT)
                .height(ATTACHED_TAB_VISUAL_HEIGHT + ATTACHED_TAB_BOTTOM_EXTRA),
        )
    }
}

@Composable
private fun AttachedModeTabsOverlay(
    activeMode: ConnectionMode,
    enabled: Boolean,
    isDarkTheme: Boolean,
    tabInset: Dp,
    onSelectMode: (ConnectionMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val targetIndex = if (activeMode == ConnectionMode.NORMAL) 0f else 1f
    val animatedIndex by animateFloatAsState(
        targetValue = targetIndex,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "attachedTabIndicator",
    )
    val layoutDirection = LocalLayoutDirection.current

    BoxWithConstraints(modifier = modifier) {
        val safeInset = tabInset.coerceIn(42.dp, maxWidth * 0.30f)
        val dockWidth = (maxWidth - safeInset * 2).coerceAtLeast(120.dp)
        val tabWidth = dockWidth / 2f
        val visualSlot = if (layoutDirection == LayoutDirection.Rtl) 1f - animatedIndex else animatedIndex

        Box(
            modifier = Modifier
                .padding(horizontal = safeInset)
                .fillMaxSize(),
        ) {
            // Sleek glowing underline bar sliding cleanly underneath the active tab
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ATTACHED_TAB_VISUAL_HEIGHT)
                    .align(Alignment.TopCenter),
            ) {
                Box(
                    modifier = Modifier
                        .offset(x = tabWidth * visualSlot)
                        .width(tabWidth)
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 22.dp, vertical = 4.dp)
                        .height(2.5.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(cyberBlueMid, cyberCyanNeon, cyberBlueElectric)
                            )
                        ),
                )
            }

            // Full 48.dp-tall touch targets aligned cleanly at TopCenter without extra background boxes
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ATTACHED_TAB_VISUAL_HEIGHT + ATTACHED_TAB_BOTTOM_EXTRA)
                    .align(Alignment.TopCenter)
                    .selectableGroup(),
                verticalAlignment = Alignment.Top,
            ) {
                AttachedTabLabelItem(
                    label = stringResource(R.string.connection_mode_normal),
                    accessibleLabel = stringResource(R.string.acc_connect_normal),
                    isSelected = activeMode == ConnectionMode.NORMAL,
                    enabled = enabled,
                    isDarkTheme = isDarkTheme,
                    onClick = { onSelectMode(ConnectionMode.NORMAL) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("normal_mode_tab"),
                )

                AttachedTabLabelItem(
                    label = stringResource(R.string.connection_mode_boost),
                    accessibleLabel = stringResource(R.string.acc_connect_boost),
                    isSelected = activeMode == ConnectionMode.BOOST,
                    enabled = enabled,
                    isDarkTheme = isDarkTheme,
                    onClick = { onSelectMode(ConnectionMode.BOOST) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("boost_mode_tab"),
                )
            }
        }
    }
}

@Composable
private fun AttachedTabLabelItem(
    label: String,
    accessibleLabel: String,
    isSelected: Boolean,
    enabled: Boolean,
    isDarkTheme: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val textColor by animateColorAsState(
        targetValue = when {
            isSelected && isDarkTheme -> cyberCyanNeon
            isSelected -> cyberBlueMid
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(200),
        label = "attachedTabTextColor",
    )

    Box(
        modifier = modifier
            .height(ATTACHED_TAB_VISUAL_HEIGHT + ATTACHED_TAB_BOTTOM_EXTRA)
            .semantics(mergeDescendants = true) { contentDescription = accessibleLabel }
            .selectable(
                selected = isSelected,
                enabled = enabled,
                role = Role.Tab,
                onClick = onClick,
            ),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ATTACHED_TAB_VISUAL_HEIGHT),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 12.5.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                ),
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PillTelemetryContent(
    displayText: String,
    status: MainStatus,
    connectionUi: ConnectionUiState,
    effectivelyConnected: Boolean,
    isTestingPing: Boolean,
    isDarkTheme: Boolean,
    onCancelBoost: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val connStatus = connectionUi.connectionStatus
    val isScanningBoost = connStatus == ConnectionStatus.SCANNING_BOOST

    val headline = when (connStatus) {
        ConnectionStatus.SCANNING_BOOST -> {
            val progress = connectionUi.scanProgress
            if (progress != null && progress.totalCount > 0) {
                stringResource(
                    R.string.connection_boost_scanning_progress,
                    progress.scannedCount,
                    progress.totalCount,
                )
            } else {
                stringResource(R.string.connection_boost_scanning)
            }
        }
        ConnectionStatus.CONNECTING -> {
            val cfgName = connectionUi.activeConfigSummary?.name
            if (!cfgName.isNullOrBlank()) {
                "${stringResource(R.string.connection_connecting_label)} · $cfgName"
            } else {
                stringResource(R.string.connection_connecting_label)
            }
        }
        ConnectionStatus.DISCONNECTING -> stringResource(R.string.connection_disconnecting_label)
        ConnectionStatus.CONNECTED -> {
            if (isTestingPing || status is MainStatus.ConnectionTest) {
                displayText.lineSequence().firstOrNull().orEmpty()
            } else {
                connectionUi.activeConfigSummary?.let { "${it.name} · ${it.subscriptionName}" }
                    ?: displayText
            }
        }
        ConnectionStatus.ERROR -> connectionUi.statusMessage ?: displayText
        ConnectionStatus.DISCONNECTED -> {
            connectionUi.activeConfigSummary?.let { "${it.name} · $displayText" }
                ?: displayText
        }
    }

    val downloadText = formatSpeedBytesPerSec(
        if (effectivelyConnected) connectionUi.downloadSpeedBytesPerSec else 0L
    )
    val uploadText = formatSpeedBytesPerSec(
        if (effectivelyConnected) connectionUi.uploadSpeedBytesPerSec else 0L
    )
    val downloadAcc = stringResource(R.string.acc_download_speed, downloadText)
    val uploadAcc = stringResource(R.string.acc_upload_speed, uploadText)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        // Line 1: Server / Scan status + Ping pill (or Cancel during Boost scan)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AnimatedContent(
                targetState = headline,
                transitionSpec = {
                    (fadeIn(tween(180)) + scaleIn(initialScale = 0.96f))
                        .togetherWith(fadeOut(tween(130)))
                },
                label = "pillHeadline",
                modifier = Modifier.weight(1f),
            ) { textValue ->
                Text(
                    text = textValue,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        fontWeight = if (effectivelyConnected || isTestingPing || isScanningBoost) {
                            FontWeight.SemiBold
                        } else {
                            FontWeight.Medium
                        },
                    ),
                    color = when {
                        isTestingPing || isScanningBoost -> if (isDarkTheme) cyberCyanNeon else cyberBlueMid
                        status is MainStatus.ConnectionTest && status.result.delayMillis >= 0 ->
                            if (isDarkTheme) cyberRgbEmerald else MaterialTheme.colorScheme.onSurface
                        (status is MainStatus.ConnectionTest && status.result.delayMillis < 0) ||
                            connStatus == ConnectionStatus.ERROR -> cyberAlertRed
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (isScanningBoost) {
                val cancelDesc = stringResource(R.string.acc_cancel_boost)
                Surface(
                    shape = CircleShape,
                    color = cyberAlertRed.copy(alpha = 0.16f),
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onCancelBoost)
                        .testTag("cancel_boost_button")
                        .semantics { contentDescription = cancelDesc },
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = cancelDesc,
                        tint = cyberAlertRed,
                        modifier = Modifier
                            .padding(4.dp)
                            .size(16.dp),
                    )
                }
            } else {
                val pingMs = when {
                    status is MainStatus.ConnectionTest && status.result.delayMillis > 0L -> status.result.delayMillis
                    effectivelyConnected -> connectionUi.lastPingMs
                    else -> null
                }
                if (pingMs != null && pingMs > 0L) {
                    val isErr = status is MainStatus.ConnectionTest && status.result.delayMillis < 0L
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isErr) {
                                if (isDarkTheme) cyberPingBgErrorDark else cyberPingBgErrorLight
                            } else {
                                if (isDarkTheme) cyberPingBgPositiveDark else cyberPingBgPositiveLight
                            },
                        ) {
                            Text(
                                text = "${pingMs}ms",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFeatureSettings = "tnum",
                                ),
                                color = if (isErr) cyberAlertRed else cyberRgbEmerald,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        }

        // Line 2: Live Download (↓) & Upload (↑) Telemetry in crisp LTR tabular numerals
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "↓ $downloadText",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFeatureSettings = "tnum",
                    ),
                    color = if (effectivelyConnected) {
                        if (isDarkTheme) cyberCyanNeon else cyberBlueMid
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    modifier = Modifier
                        .testTag("download_speed_text")
                        .semantics { contentDescription = downloadAcc },
                )
                Text(
                    text = "↑ $uploadText",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFeatureSettings = "tnum",
                    ),
                    color = if (effectivelyConnected) {
                        if (isDarkTheme) cyberRgbEmerald else MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    modifier = Modifier
                        .testTag("upload_speed_text")
                        .semantics { contentDescription = uploadAcc },
                )
            }
        }
    }
}

@Composable
private fun CyberSignalWaveform(
    phase: Float,
    isPingMode: Boolean,
    modifier: Modifier = Modifier,
) {
    val barColors = if (isPingMode) {
        listOf(cyberCyanNeon, cyberBlueElectric, cyberRgbEmerald, cyberRgbViolet, cyberRgbMagenta)
    } else {
        listOf(cyberBlueElectric, cyberCyanNeon, cyberBlueMid, cyberRgbViolet, cyberCyanNeon)
    }
    Canvas(modifier = modifier) {
        val barCount = barColors.size
        val barWidth = 2.4.dp.toPx()
        val gap = (size.width - barCount * barWidth) / (barCount - 1).coerceAtLeast(1)
        val maxH = size.height
        val minH = maxH * 0.24f

        for (i in 0 until barCount) {
            val wave = (sin(phase + i * 0.85f) + 1f) * 0.5f
            val h = minH + (maxH - minH) * wave
            val x = i * (barWidth + gap)
            val y = (maxH - h) / 2f
            drawRoundRect(
                color = barColors[i],
                topLeft = Offset(x, y),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
            )
        }
    }
}

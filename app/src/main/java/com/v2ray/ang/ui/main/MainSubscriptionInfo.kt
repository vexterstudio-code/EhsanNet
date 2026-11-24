package com.v2ray.ang.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.SubscriptionUsageInfo
import com.v2ray.ang.extension.toTrafficString
import com.v2ray.ang.handler.SubscriptionInfoParser
import com.v2ray.ang.ui.compose.LocalDarkTheme
import com.v2ray.ang.ui.compose.cyberBlueElectric
import com.v2ray.ang.ui.compose.cyberCardBorderDark
import com.v2ray.ang.ui.compose.cyberCardBorderLight
import com.v2ray.ang.ui.compose.cyberCardDark
import com.v2ray.ang.ui.compose.cyberCardLight
import com.v2ray.ang.util.Utils

private const val MILLIS_PER_SECOND = 1000L
private const val EXPIRY_DATE_PATTERN = "yyyy-MM-dd"
private const val NO_EXPIRY_SYMBOL = "\u221E"
private const val CARD_ALPHA = 0.96f

/** Locale-neutral values the subscription information box renders. */
internal data class SubscriptionInfoUiModel(
    val title: String?,
    val usedBytes: Long,
    /** Traffic quota in bytes; 0 means unlimited. */
    val totalBytes: Long,
    /** Used share of the quota in 0..1; null when the quota is unlimited. */
    val trafficFraction: Float?,
    /** Expiry in epoch milliseconds; null when the subscription never expires. */
    val expireMillis: Long?,
    val expired: Boolean,
    val supportUrl: String?,
    val webPageUrl: String?,
) {
    val trafficLow: Boolean get() = trafficFraction != null && trafficFraction >= TRAFFIC_WARNING_FRACTION

    fun visibleSupportUrl(): String? =
        supportUrl?.takeUnless(SubscriptionInfoParser::isTelegramLink)

    fun visibleWebPageUrl(): String? =
        webPageUrl?.takeUnless(SubscriptionInfoParser::isTelegramLink)

    companion object {
        const val TRAFFIC_WARNING_FRACTION = 0.9f
    }
}

internal fun buildSubscriptionInfoUiModel(
    info: SubscriptionUsageInfo,
    nowMillis: Long,
): SubscriptionInfoUiModel {
    val unlimited = info.total <= 0L
    val expireMillis = info.expire.takeIf { it > 0L }?.let { it * MILLIS_PER_SECOND }
    return SubscriptionInfoUiModel(
        title = info.title,
        usedBytes = info.used,
        totalBytes = if (unlimited) 0L else info.total,
        trafficFraction = if (unlimited) null else (info.used.toDouble() / info.total).toFloat().coerceIn(0f, 1f),
        expireMillis = expireMillis,
        expired = expireMillis != null && expireMillis <= nowMillis,
        supportUrl = info.supportUrl,
        webPageUrl = info.webPageUrl,
    )
}

/**
 * Fixed box on top of a subscription group showing the account information published by the
 * subscription panel: traffic, expiry date and non-Telegram shortcuts (support page, refresh).
 */
@Composable
internal fun SubscriptionInfoCard(
    info: SubscriptionUsageInfo,
    fallbackTitle: String,
    onOpenLink: (String) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val model = remember(info) { buildSubscriptionInfoUiModel(info, System.currentTimeMillis()) }
    val nonTelegramSupportUrl = remember(model) { model.visibleSupportUrl() }
    val nonTelegramWebPageUrl = remember(model) { model.visibleWebPageUrl() }
    val isDark = LocalDarkTheme.current
    val cardColor = (if (isDark) cyberCardDark else cyberCardLight).copy(alpha = CARD_ALPHA)
    val borderColor = if (isDark) cyberBlueElectric.copy(alpha = 0.3f) else cyberCardBorderLight

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = cardColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = model.title ?: fallbackTitle,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                nonTelegramSupportUrl?.let { url ->
                    CardIconButton(
                        iconRes = R.drawable.ic_feedback_24dp,
                        contentDescription = stringResource(R.string.sub_info_open_support),
                        onClick = { onOpenLink(url) },
                    )
                }
                nonTelegramWebPageUrl?.let { url ->
                    CardIconButton(
                        iconRes = R.drawable.ic_about_24dp,
                        contentDescription = stringResource(R.string.sub_info_open_page),
                        onClick = { onOpenLink(url) },
                    )
                }
                CardIconButton(
                    iconRes = R.drawable.ic_refresh_24dp,
                    contentDescription = stringResource(R.string.title_sub_update),
                    onClick = onRefresh,
                )
            }

            // One semantics node for the read-only figures so TalkBack reads them in one go.
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(end = 8.dp)
                    .semantics(mergeDescendants = true) {}
            ) {
                StatsRow(model)
                model.trafficFraction?.let { fraction ->
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .clearAndSetSemantics {},
                        color = if (model.trafficLow) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CardIconButton(iconRes: Int, contentDescription: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .size(36.dp)
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(19.dp)
        )
    }
}

@Composable
private fun StatsRow(model: SubscriptionInfoUiModel) {
    val totalText = if (model.totalBytes > 0L) {
        model.totalBytes.toTrafficString()
    } else {
        stringResource(R.string.sub_info_unlimited)
    }
    // Figures and the slash between them always read left to right, also in right-to-left languages;
    // otherwise the bidi algorithm swaps "used / total" around the slash.
    val figuresStyle = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            text = stringResource(R.string.sub_info_traffic, model.usedBytes.toTrafficString(), totalText),
            modifier = Modifier.weight(1f, fill = false),
            style = figuresStyle,
            color = if (model.trafficLow) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.size(8.dp))
        Text(
            // No expiry date means the subscription never expires.
            text = model.expireMillis?.let { Utils.formatTimestamp(it, EXPIRY_DATE_PATTERN) } ?: NO_EXPIRY_SYMBOL,
            style = figuresStyle,
            color = if (model.expired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

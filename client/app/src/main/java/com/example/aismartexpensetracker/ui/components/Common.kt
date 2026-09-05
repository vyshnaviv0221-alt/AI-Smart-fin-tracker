package com.example.aismartexpensetracker.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.example.aismartexpensetracker.ui.theme.*

/**
 * Press feedback.
 *
 * The scale is driven by the *pressed* state, which fires on pointer-down, not
 * on click (which fires on release). Feedback that waits for release reads as
 * lag. The spring means the release is interruptible: press again mid-return
 * and it redirects from wherever it currently is rather than snapping.
 */
@Composable
fun rememberPressScale(interactionSource: MutableInteractionSource): Float {
    val pressed by interactionSource.collectIsPressedAsState()
    val reduced = LocalReducedMotion.current
    val scale by animateFloatAsState(
        targetValue = if (pressed) Motion.PRESS_SCALE else 1f,
        animationSpec = if (reduced) snap() else Motion.quick(),
        label = "pressScale"
    )
    return scale
}

/** A resting card. Soft lift, generous radius, white on the lilac canvas. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = Radius.card,
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = Elevation.card),
        border = BorderStroke(1.dp, Hairline),
        content = content
    )
}

/**
 * A card that responds to touch: shrinks on press-down, springs back on
 * release, and keeps the platform ripple so it still feels like Android.
 */
@Composable
fun PressableCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interactionSource)

    Card(
        modifier = modifier
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
            ),
        shape = Radius.card,
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = Elevation.card),
        border = BorderStroke(1.dp, Hairline),
        content = content
    )
}

/** Section header. Sits directly on the canvas, above a group of cards. */
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Space.xs, end = Space.xs, bottom = Space.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, style = SectionStyle, color = Ink)
        trailing?.invoke()
    }
}

/** Screen title. */
@Composable
fun ScreenTitle(
    text: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text, style = DisplayStyle, color = Ink)
            trailing?.invoke()
        }
        if (subtitle != null) {
            Spacer(Modifier.height(Space.xs))
            Text(subtitle, style = CaptionStyle, color = InkMuted)
        }
    }
}

/**
 * A progress bar whose fill springs to its target.
 *
 * Animating the fill rather than snapping it means a budget that moves from
 * 40% to 95% reads as a change, not as a different screen. Colour animates
 * too, so crossing into "over budget" is a transition rather than a cut.
 */
@Composable
fun AnimatedBar(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    track: Color = TrackGrey
) {
    val reduced = LocalReducedMotion.current
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = if (reduced) snap() else Motion.gentle(),
        label = "barFill"
    )
    val animatedColor by animateColorAsState(
        targetValue = color,
        animationSpec = if (reduced) snap() else Motion.standard(),
        label = "barColor"
    )

    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(Radius.bar)
            .background(track)
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated)
                .fillMaxHeight()
                .clip(Radius.bar)
                .background(animatedColor)
        )
    }
}

/** Small stat card: emoji, value, label. */
@Composable
fun StatTile(
    icon: String,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Ink
) {
    AppCard(modifier) {
        Column(Modifier.padding(Space.lg)) {
            Text(icon, style = BodyStyle.copy(fontSize = 20.sp))
            Spacer(Modifier.height(Space.sm))
            Text(value, style = StatStyle, color = valueColor)
            Spacer(Modifier.height(Space.xxs))
            Text(label, style = CaptionStyle, color = InkMuted)
        }
    }
}

/**
 * Empty state.
 *
 * Every list in the app can legitimately be empty -- nothing ships with
 * sample data -- so an empty state is a real state, not an edge case. It says
 * what is missing and exactly how to fix it.
 */
@Composable
fun EmptyState(
    icon: String,
    title: String,
    message: String,
    modifier: Modifier = Modifier
) {
    AppCard(modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(Space.xl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Indigo50),
                contentAlignment = Alignment.Center
            ) {
                Text(icon, style = StatStyle)
            }
            Spacer(Modifier.height(Space.md))
            Text(title, style = RowTitleStyle, color = Ink)
            Spacer(Modifier.height(Space.xs))
            Text(
                message,
                style = CaptionStyle,
                color = InkMuted,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Category pill used on transaction rows. */
@Composable
fun CategoryChip(category: String, modifier: Modifier = Modifier) {
    val tint = categoryColor(category)
    Box(
        modifier
            .clip(Radius.chip)
            .background(tint.copy(alpha = 0.10f))
            .padding(horizontal = Space.sm, vertical = 3.dp)
    ) {
        Text(category, style = LabelStyle, color = tint)
    }
}

/** Circular emoji badge used everywhere a category appears. */
@Composable
fun CategoryAvatar(
    category: String,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp
) {
    val tint = categoryColor(category)
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Text(categoryEmoji(category), style = BodyStyle)
    }
}

/** A hairline used between rows inside one card. */
@Composable
fun RowDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Hairline)
    )
}

// ---- category identity, in one place ----

private val CATEGORY_EMOJI = mapOf(
    "Food" to "🍔",
    "Groceries" to "🛒",
    "Travel" to "🚕",
    "Shopping" to "🛍️",
    "Bills" to "🧾",
    "Healthcare" to "🏥",
    "Entertainment" to "🎬",
    "Investment" to "📈",
    "Rent" to "🏠",
    "Transfer" to "💸",
    "Uncategorized" to "❓"
)

private val CATEGORY_COLOR = mapOf(
    "Food" to CatFood,
    "Groceries" to CatGroceries,
    "Travel" to CatTravel,
    "Shopping" to CatShopping,
    "Bills" to CatBills,
    "Healthcare" to CatHealthcare,
    "Entertainment" to CatEntertainment,
    "Investment" to CatInvestment,
    "Rent" to CatRent,
    "Transfer" to CatTransfer,
    "Uncategorized" to CatUnknown
)

fun categoryEmoji(category: String): String = CATEGORY_EMOJI[category] ?: "❓"

fun categoryColor(category: String): Color = CATEGORY_COLOR[category] ?: CatUnknown

/** Consistent money formatting: no decimals, grouped thousands, rupee prefix. */
fun rupees(amount: Double): String = "₹${"%,.0f".format(amount)}"

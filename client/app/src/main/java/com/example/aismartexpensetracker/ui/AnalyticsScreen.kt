package com.example.aismartexpensetracker.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas as DrawCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aismartexpensetracker.ExpenseViewModel
import com.example.aismartexpensetracker.ui.components.*
import com.example.aismartexpensetracker.ui.theme.*

private data class Slice(val category: String, val amount: Double, val color: Color)

@Composable
fun AnalyticsScreen(viewModel: ExpenseViewModel = viewModel()) {
    val totals by viewModel.categoryTotals.collectAsState()
    val slices = totals.map { Slice(it.category, it.amount, categoryColor(it.category)) }
    val total = slices.sumOf { it.amount }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Canvas)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.lg)
    ) {
        Spacer(Modifier.height(Space.sm))
        ScreenTitle(text = "Analytics", subtitle = "This month, by category")
        Spacer(Modifier.height(Space.xl))

        // Every ratio below divides by total, so an empty database would
        // produce NaN sweep angles and an invisible chart.
        if (slices.isEmpty() || total <= 0.0) {
            EmptyState(
                icon = "📈",
                title = "No spending data yet",
                message = "Capture or add a few transactions and this chart fills in."
            )
            Spacer(Modifier.height(Space.xxxl))
            return@Column
        }

        AppCard(Modifier.fillMaxWidth()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = Space.xxl),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(contentAlignment = Alignment.Center) {
                    DonutChart(slices, total)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(rupees(total), style = StatStyle, color = Indigo900)
                        Text("total spent", style = CaptionStyle, color = InkMuted)
                    }
                }
            }
        }

        Spacer(Modifier.height(Space.xxl))
        SectionHeader("Breakdown")

        AppCard(Modifier.fillMaxWidth()) {
            slices.forEachIndexed { index, slice ->
                BreakdownRow(slice, total)
                if (index != slices.lastIndex) {
                    RowDivider(Modifier.padding(start = Space.lg, end = Space.lg))
                }
            }
        }

        Spacer(Modifier.height(Space.xxxl))
    }
}

/**
 * The donut sweeps open on first composition rather than appearing complete.
 *
 * A single 0..1 progress value drives every arc, so the chart draws itself in
 * one continuous motion instead of each slice animating independently. Gaps
 * between slices keep adjacent categories separable without relying on colour
 * alone.
 */
@Composable
private fun DonutChart(slices: List<Slice>, total: Double) {
    val reduced = LocalReducedMotion.current
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }

    val sweep by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = if (reduced) snap() else Motion.gentle(),
        label = "donutSweep"
    )

    val strokeWidth = 26.dp
    val gapDegrees = if (slices.size > 1) 2.5f else 0f

    DrawCanvas(Modifier.size(184.dp)) {
        val stroke = strokeWidth.toPx()
        val diameter = size.minDimension - stroke
        val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
        val arcSize = Size(diameter, diameter)

        var startAngle = -90f
        slices.forEach { slice ->
            val full = (slice.amount / total * 360.0).toFloat()
            val drawn = (full - gapDegrees).coerceAtLeast(0.5f) * sweep
            drawArc(
                color = slice.color,
                startAngle = startAngle,
                sweepAngle = drawn,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            startAngle += full
        }
    }
}

@Composable
private fun BreakdownRow(slice: Slice, total: Double) {
    val share = (slice.amount / total).coerceIn(0.0, 1.0).toFloat()

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.lg, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(slice.color)
        )
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "${categoryEmoji(slice.category)}  ${slice.category}",
                    style = RowTitleStyle,
                    color = Ink
                )
                Text(
                    "${rupees(slice.amount)}  ·  ${(share * 100).toInt()}%",
                    style = CaptionStyle,
                    color = InkMuted
                )
            }
            Spacer(Modifier.height(Space.sm))
            AnimatedBar(progress = share, color = slice.color, height = 6.dp)
        }
    }
}

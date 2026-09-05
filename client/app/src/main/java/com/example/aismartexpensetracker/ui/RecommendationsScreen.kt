package com.example.aismartexpensetracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aismartexpensetracker.ExpenseViewModel
import com.example.aismartexpensetracker.Insight
import com.example.aismartexpensetracker.InsightLevel

private val RecPurpleDark = Color(0xFF3C3489)
private val RecPurpleMid = Color(0xFF534AB7)
private val RecBgGray = Color(0xFFF7F6FA)
private val RecTextSecondary = Color(0xFF757575)
private val RecWarningBg = Color(0xFFFDF1DC)
private val RecWarningText = Color(0xFFB05B00)
private val RecDangerBg = Color(0xFFFBE9E9)
private val RecDangerText = Color(0xFFD32F2F)
private val RecGoodBg = Color(0xFFEAF3DE)
private val RecGoodText = Color(0xFF3B6D11)
private val RecNeutralBg = Color(0xFFEEEDFE)

/**
 * Every card here is generated from the user's own transactions and budgets by
 * ExpenseViewModel.buildInsights -- there are no canned tips. When there is
 * nothing genuine to say the screen says so rather than inventing advice.
 */
@Composable
fun RecommendationsScreen(viewModel: ExpenseViewModel = viewModel()) {
    val insights by viewModel.insights.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RecBgGray)
            .padding(16.dp)
    ) {
        Text(
            "Recommendations",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = RecPurpleDark
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (insights.isEmpty()) "Nothing to flag right now"
            else "Based on this month's spending and your budgets",
            fontSize = 13.sp,
            color = RecTextSecondary
        )
        Spacer(Modifier.height(16.dp))

        if (insights.isEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text("No recommendations yet", fontWeight = FontWeight.Bold, color = RecPurpleDark)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Capture a few transactions and set some budget limits. " +
                            "Advice appears here only when your own data supports it.",
                        fontSize = 13.sp,
                        color = RecTextSecondary
                    )
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(insights) { InsightCard(it) }
            }
        }
    }
}

@Composable
private fun InsightCard(insight: Insight) {
    val (background, foreground) = when (insight.level) {
        InsightLevel.GOOD -> RecGoodBg to RecGoodText
        InsightLevel.WARNING -> RecWarningBg to RecWarningText
        InsightLevel.DANGER -> RecDangerBg to RecDangerText
        InsightLevel.NEUTRAL -> RecNeutralBg to RecPurpleMid
    }

    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp)) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(background),
                contentAlignment = Alignment.Center
            ) { Text(insight.icon, fontSize = 18.sp) }

            Spacer(Modifier.width(12.dp))

            Column {
                Text(
                    insight.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = foreground
                )
                Spacer(Modifier.height(4.dp))
                Text(insight.message, fontSize = 13.sp, color = RecTextSecondary)
            }
        }
    }
}

package com.example.aismartexpensetracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ============================================================
// Local theme colors (self-contained, prefixed to avoid any
// clash with colors declared elsewhere — same pattern as
// LoginScreen.kt / PredictionsScreen.kt).
// ============================================================
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

private enum class Tone { GOOD, WARNING, DANGER, NEUTRAL }

data class Recommendation(
    val icon: String,
    val title: String,
    val message: String,
    val tone: TonePublic
)

// Public-facing tone enum so callers outside this file (if ever needed)
// don't have to reference the private Tone type.
enum class TonePublic { GOOD, WARNING, DANGER, NEUTRAL }

@Composable
fun RecommendationsScreen() {
    // Sample rule-based tips — replace with real logic once category
    // spend data is available from Room (e.g. "if category X > 90% of
    // its budget limit, generate a WARNING tip").
    val tips = listOf(
        Recommendation(
            "🛍️", "Shopping is over budget",
            "You've spent ₹540 more than your Shopping limit this month. Consider pausing non-essential purchases.",
            TonePublic.DANGER
        ),
        Recommendation(
            "🧾", "Bills nearing the limit",
            "You're at 98% of your Bills budget. One more payment could push you over.",
            TonePublic.WARNING
        ),
        Recommendation(
            "🍔", "Food spending is up 12%",
            "Your Food expenses this month are higher than your 3-month average. A few home-cooked days could help.",
            TonePublic.WARNING
        ),
        Recommendation(
            "🚕", "Travel is under control",
            "You're comfortably within your Travel budget — ₹900 left with 5 days remaining in the month.",
            TonePublic.GOOD
        ),
        Recommendation(
            "💡", "Try the 50/30/20 rule",
            "Based on your income pattern, allocating 50% to needs, 30% to wants, and 20% to savings could improve your monthly balance.",
            TonePublic.NEUTRAL
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RecBgGray)
            .verticalScroll(rememberScrollState())
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
            "Insights based on your spending patterns",
            fontSize = 14.sp,
            color = RecTextSecondary
        )
        Spacer(Modifier.height(20.dp))

        tips.forEach { tip ->
            RecommendationCard(tip)
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun RecommendationCard(item: Recommendation) {
    val (bg, textColor) = when (item.tone) {
        TonePublic.GOOD -> RecGoodBg to RecGoodText
        TonePublic.WARNING -> RecWarningBg to RecWarningText
        TonePublic.DANGER -> RecDangerBg to RecDangerText
        TonePublic.NEUTRAL -> Color(0xFFEEEDFE) to RecPurpleMid
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(bg),
                contentAlignment = Alignment.Center
            ) {
                Text(item.icon, fontSize = 16.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = RecPurpleDark
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    item.message,
                    fontSize = 13.sp,
                    color = RecTextSecondary,
                    lineHeight = 18.sp
                )
            }
        }
    }
}


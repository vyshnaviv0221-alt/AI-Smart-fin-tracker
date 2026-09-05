package com.example.aismartexpensetracker.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * Type scale.
 *
 * Two rules drive every value here, and both are size-specific -- a single
 * letterSpacing or line-height applied to every size is wrong somewhere:
 *
 *  - Tracking tightens as text grows. Letters read too far apart at display
 *    sizes, so large text takes negative tracking; body sits near zero; small
 *    all-caps labels take positive tracking to stay legible.
 *  - Leading is inverse to size. Tight on headings, generous on body.
 *
 * Hierarchy is built from weight + size + leading together, not size alone,
 * so emphasis costs presence rather than space.
 *
 * Tracking is expressed in sp, not em. Compose's TextStyle.lerp -- which
 * OutlinedTextField uses to float its label between two styles -- throws
 * "Cannot perform operation for Em and Sp" if one style's letterSpacing is in
 * em and the other (a Material default) is in sp. Each value below is the em
 * figure multiplied by that style's font size, so the optical result is the
 * same.
 *
 * FontFamily.Default is deliberate: the platform font already ships optical
 * sizing and legibility tuning, and it honours the user's Dynamic Type
 * setting. All sizes are in sp so they scale with that setting.
 */

private val Leading = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None
)

/** Screen titles. Large, tight, confident. */
val DisplayStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Bold,
    fontSize = 28.sp,
    lineHeight = 33.sp,
    letterSpacing = (-0.7).sp,
    lineHeightStyle = Leading
)

/** Money headline -- the number people actually came to see. */
val AmountStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Bold,
    fontSize = 36.sp,
    lineHeight = 40.sp,
    letterSpacing = (-1.1).sp,
    lineHeightStyle = Leading
)

/** Section headers above a group of cards. */
val SectionStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.SemiBold,
    fontSize = 17.sp,
    lineHeight = 22.sp,
    letterSpacing = (-0.2).sp,
    lineHeightStyle = Leading
)

/** Primary row text: a merchant, a category name. */
val RowTitleStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.SemiBold,
    fontSize = 15.sp,
    lineHeight = 20.sp,
    letterSpacing = (-0.1).sp,
    lineHeightStyle = Leading
)

/** Body copy and empty-state explanations -- the loosest leading in the app. */
val BodyStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    lineHeight = 21.sp,
    letterSpacing = 0.sp,
    lineHeightStyle = Leading
)

/** Supporting detail under a row title. */
val CaptionStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = 12.5.sp,
    lineHeight = 17.sp,
    letterSpacing = 0.1.sp,
    lineHeightStyle = Leading
)

/** Small labels and chips -- positive tracking so they stay readable. */
val LabelStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Medium,
    fontSize = 11.5.sp,
    lineHeight = 15.sp,
    letterSpacing = 0.25.sp,
    lineHeightStyle = Leading
)

/** Stat tile values: large enough to scan, tight enough to sit in a small card. */
val StatStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Bold,
    fontSize = 22.sp,
    lineHeight = 26.sp,
    letterSpacing = (-0.45).sp,
    lineHeightStyle = Leading
)

val AppTypography = Typography(
    displaySmall = DisplayStyle,
    headlineMedium = AmountStyle,
    titleLarge = SectionStyle,
    titleMedium = RowTitleStyle,
    bodyLarge = BodyStyle,
    bodyMedium = CaptionStyle,
    labelSmall = LabelStyle
)

// Kept so Theme.kt's existing reference keeps resolving.
val Typography = AppTypography

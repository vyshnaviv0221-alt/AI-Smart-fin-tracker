package com.example.aismartexpensetracker.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The single source of colour for the app.
 *
 * Previously nine screen files each declared their own private copies of the
 * same hexes (AnaPurpleDark, BudgetPurpleDark, TxnPurpleDark ... all
 * 0xFF3C3489), which meant no change could be made in one place.
 */

// Brand
val Indigo900 = Color(0xFF2E2769)
val Indigo700 = Color(0xFF3C3489)
val Indigo500 = Color(0xFF5B51C9)
val Indigo300 = Color(0xFF9A93E8)
val Indigo100 = Color(0xFFE7E4FB)
val Indigo50 = Color(0xFFF2F0FE)

// Neutrals -- a faint lilac cast so the greys sit with the brand rather than
// fighting it.
val Ink = Color(0xFF1B1830)
val InkMuted = Color(0xFF6A6580)
val InkFaint = Color(0xFF9A94AE)
val Canvas = Color(0xFFF6F5FB)
val SurfaceWhite = Color(0xFFFFFFFF)
val Hairline = Color(0xFFE8E5F2)
val TrackGrey = Color(0xFFE9E6F3)

// Semantic
val Success = Color(0xFF10875E)
val SuccessSoft = Color(0xFFE3F4EC)
val Warning = Color(0xFFB4700B)
val WarningSoft = Color(0xFFFCF0DC)
val Danger = Color(0xFFC5303A)
val DangerSoft = Color(0xFFFBE9EA)

// Category accents. Distinct in hue and in lightness, so the donut and the
// legend stay separable for colour-vision deficiency and in greyscale print
// (the report will be printed).
val CatFood = Color(0xFF5B51C9)
val CatGroceries = Color(0xFF10875E)
val CatTravel = Color(0xFF2F7DD1)
val CatShopping = Color(0xFFC2417A)
val CatBills = Color(0xFFCC5B22)
val CatHealthcare = Color(0xFF12857C)
val CatEntertainment = Color(0xFF8348C4)
val CatInvestment = Color(0xFF2F7A34)
val CatRent = Color(0xFF7A5343)
val CatTransfer = Color(0xFF4E6478)
val CatUnknown = Color(0xFF8E8AA0)

// Retained for the Material colour scheme in Theme.kt
val Purple80 = Indigo300
val PurpleGrey80 = Color(0xFFCCC7DC)
val Pink80 = Color(0xFFE9B7D2)
val Purple40 = Indigo500
val PurpleGrey40 = Color(0xFF61597D)
val Pink40 = Color(0xFF7D5260)

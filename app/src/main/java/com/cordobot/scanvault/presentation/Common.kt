package com.cordobot.scanvault.presentation

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val colors = darkColorScheme(
    primary = Color(0xFF818CF8),
    onPrimary = Color(0xFF0B0B1A),
    background = Color(0xFF0B0D17),
    onBackground = Color.White,
    surface = Color(0xFF151827),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF20243A),
    onSurfaceVariant = Color(0xFFA1A7C2),
    surfaceContainer = Color(0xFF11131F)
)

@Composable
fun ScanVaultTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = colors,
        typography = Typography(
            headlineLarge = TextStyle(fontWeight = FontWeight.Black, fontSize = 30.sp),
            titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        ),
        content = content
    )
}

private val dateFormat = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.forLanguageTag("es"))

fun formatDate(instant: Instant): String = dateFormat.format(instant.atZone(ZoneId.systemDefault()))

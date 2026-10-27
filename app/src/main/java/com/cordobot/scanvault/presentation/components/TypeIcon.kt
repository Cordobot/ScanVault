package com.cordobot.scanvault.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.ShortText
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cordobot.scanvault.domain.CodeType

val CodeType.icon: ImageVector
    get() = when (this) {
        CodeType.URL -> Icons.Default.Language
        CodeType.WIFI -> Icons.Default.Wifi
        CodeType.CONTACT -> Icons.Default.Person
        CodeType.EMAIL -> Icons.Default.Email
        CodeType.PHONE -> Icons.Default.Phone
        CodeType.SMS -> Icons.AutoMirrored.Filled.Message
        CodeType.GEO -> Icons.Default.Place
        CodeType.PRODUCT -> Icons.Default.QrCode2
        CodeType.TEXT -> Icons.AutoMirrored.Filled.ShortText
    }

val CodeType.color: Color
    get() = when (this) {
        CodeType.URL -> Color(0xFF6366F1)
        CodeType.WIFI -> Color(0xFF0EA5E9)
        CodeType.CONTACT -> Color(0xFF22C55E)
        CodeType.EMAIL -> Color(0xFFF59E0B)
        CodeType.PHONE, CodeType.SMS -> Color(0xFF14B8A6)
        CodeType.GEO -> Color(0xFFEF4444)
        CodeType.PRODUCT -> Color(0xFFA855F7)
        CodeType.TEXT -> Color(0xFF94A3B8)
    }

@Composable
fun TypeIcon(type: CodeType, size: Dp = 44.dp) {
    Box(
        modifier = Modifier.size(size).background(type.color.copy(alpha = 0.18f), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(type.icon, contentDescription = type.label, tint = type.color)
    }
}

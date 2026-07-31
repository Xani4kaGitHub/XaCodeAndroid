package com.xanichka.xacode.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xanichka.xacode.R
import com.xanichka.xacode.model.ProviderType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

@Composable
fun BrandLogo(size: Dp = 44.dp) {
    Image(
        painter = painterResource(R.drawable.xacode_logo),
        contentDescription = "XaCode",
        modifier = Modifier.size(size)
    )
}

@Composable
fun CircleIconButton(icon: ImageVector, description: String, onClick: () -> Unit, size: Dp = 44.dp) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(size)) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = description, modifier = Modifier.size(size * .48f))
        }
    }
}

@Composable
fun ProviderBadge(provider: ProviderType, size: Dp = 42.dp, selected: Boolean = false) {
    val (label, color) = when (provider) {
        ProviderType.DEEPSEEK -> "DS" to Color(0xFF4D8DFF)
        ProviderType.OPENAI -> "◎" to Color(0xFF10A37F)
        ProviderType.ANTHROPIC -> "AI" to Color(0xFFD97757)
        ProviderType.GOOGLE -> "✦" to Color(0xFF4285F4)
        ProviderType.OPENROUTER -> "OR" to Color(0xFF7C6CFF)
        ProviderType.AGENTROUTER -> "AR" to Color(0xFF3BAF91)
        ProviderType.OLLAMA -> "OL" to Color(0xFF8B8FA3)
        ProviderType.CUSTOM -> "<>" to Color(0xFFCBA6F7)
    }
    Surface(modifier = Modifier.size(size), shape = CircleShape, color = color.copy(alpha = if (selected) .24f else .14f)) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, color = color, fontSize = (size.value * .31f).sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

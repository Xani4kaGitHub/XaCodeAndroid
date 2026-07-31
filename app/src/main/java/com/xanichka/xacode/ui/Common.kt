package com.xanichka.xacode.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xanichka.xacode.R

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

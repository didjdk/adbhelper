package com.rel.adbhelper.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * The app's mark: a center dot with two radiating rings, evoking a device being pinged/found
 * on the network — this app's actual job — rather than a literal mascot illustration.
 */
@Composable
fun SignalMark(modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 96.dp) {
    val accent = MaterialTheme.colorScheme.primary
    val faint = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    Canvas(modifier = modifier.size(size)) {
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val maxRadius = this.size.minDimension / 2f
        drawCircle(color = faint, radius = maxRadius, center = center, style = Stroke(width = maxRadius * 0.06f))
        drawCircle(
            color = faint,
            radius = maxRadius * 0.62f,
            center = center,
            style = Stroke(width = maxRadius * 0.06f),
        )
        drawCircle(color = accent, radius = maxRadius * 0.16f, center = center)
    }
}

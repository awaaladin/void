package com.vaultkeep.app.ui.splash

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.vaultkeep.app.R
import kotlinx.coroutines.delay

/**
 * In-app branded splash: the full eclipse photo (slightly scaled down from
 * full-bleed so it reads as a contained image, not an edge-to-edge crop),
 * with dark watery ripples expanding from its center as the loading beat,
 * then auto-advances. Shown right after the system cold-start splash
 * (MainActivity's installSplashScreen()) hands off.
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(1800)
        onFinished()
    }

    val infiniteTransition = rememberInfiniteTransition(label = "void-ripples")
    val ripplePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ripplePhase",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(id = R.drawable.splash_void),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .scale(0.86f),
        )
        RippleOverlay(phase = ripplePhase, modifier = Modifier.fillMaxSize())
        Text(
            "Void",
            style = MaterialTheme.typography.displayLarge,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp)
                .alpha(0.95f),
        )
    }
}

/**
 * Three concentric rings, each offset a third of a cycle apart, expanding
 * from the screen's center and fading out — a slow, dark "watery" pulse
 * rather than a mechanical spinner.
 */
@Composable
private fun RippleOverlay(phase: Float, modifier: Modifier = Modifier) {
    val ringColor = Color(0xFF9333EA)
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = size.minDimension * 0.42f
        val ringCount = 3
        for (i in 0 until ringCount) {
            val ringPhase = (phase + i.toFloat() / ringCount) % 1f
            val radius = 24.dp.toPx() + ringPhase * (maxRadius - 24.dp.toPx())
            val alpha = (1f - ringPhase).coerceIn(0f, 1f) * 0.5f
            drawCircle(
                color = ringColor.copy(alpha = alpha),
                radius = radius,
                center = center,
                style = Stroke(width = 2.dp.toPx()),
            )
        }
    }
}

package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Special Cinematic Animated Splash Screen:
 * - Scene 1 (~5s): Blue sky with drifting clouds & sunlight, White Pigeon 1 flying with
 *   a tiny string & hanging name tag displaying "happy".
 * - Scene 2 (~3.8s): Second White Pigeon enters with hanging name tag "hitu". Both pigeons
 *   fly together across the sunlit sky while displaying "Makwana_Hitu.07", then transitions
 *   smoothly to the main dashboard.
 */
@Composable
fun CinematicSplashScreen(
    onFinished: () -> Unit
) {
    // Scene 1: 0..5000ms, Scene 2: 5000..8800ms
    var currentScene by remember { mutableIntStateOf(1) }

    val happyProgress = remember { Animatable(0f) }
    val hituProgress = remember { Animatable(-0.25f) }
    val titleAlpha = remember { Animatable(0f) }
    val screenFade = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        // Scene 1 (~5 seconds): Happy pigeon soars smoothly across sunlit sky
        launch {
            happyProgress.animateTo(
                targetValue = 0.56f,
                animationSpec = tween(durationMillis = 5000, easing = FastOutSlowInEasing)
            )
        }
        launch {
            delay(1000L)
            titleAlpha.animateTo(1f, animationSpec = tween(1200))
        }

        delay(5000L)
        currentScene = 2

        // Scene 2: Second pigeon ("hitu") enters & both fly together across the sky
        val job1 = launch {
            happyProgress.animateTo(
                targetValue = 1.15f,
                animationSpec = tween(durationMillis = 3600, easing = LinearEasing)
            )
        }
        val job2 = launch {
            hituProgress.animateTo(
                targetValue = 1.05f,
                animationSpec = tween(durationMillis = 3600, easing = FastOutSlowInEasing)
            )
        }
        job1.join()
        job2.join()

        screenFade.animateTo(0f, animationSpec = tween(450))
        onFinished()
    }

    val infinite = rememberInfiniteTransition(label = "pigeon_flight")
    val wingPhase1 by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Restart),
        label = "wing1"
    )
    val wingPhase2 by infinite.animateFloat(
        initialValue = 1.4f,
        targetValue = (1.4f + 2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(460, easing = LinearEasing), RepeatMode.Restart),
        label = "wing2"
    )
    val cloudDrift by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Restart),
        label = "clouds"
    )
    val sunPulse by infinite.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "sun"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(screenFade.value)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0A3D8F),
                        Color(0xFF1D6CE0),
                        Color(0xFF56A8F5),
                        Color(0xFF9DD4FF)
                    )
                )
            )
    ) {
        // Sky, Sunlight, Clouds & Flying White Pigeons with Hanging Name Tags
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Glowing golden sunlight in upper-right sky
            val sunCenter = Offset(w * 0.82f, h * 0.16f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFFF7CC).copy(alpha = 0.95f),
                        Color(0xFFFFE082).copy(alpha = 0.48f),
                        Color(0xFF93C5FD).copy(alpha = 0f)
                    ),
                    center = sunCenter,
                    radius = w * 0.52f * sunPulse
                ),
                radius = w * 0.52f * sunPulse,
                center = sunCenter
            )
            drawCircle(
                color = Color(0xFFFFFBE6),
                radius = w * 0.085f,
                center = sunCenter
            )

            // Volumetric drifting clouds
            drawFluffyCloud(
                center = Offset(((w * 0.25f) + cloudDrift * w * 0.35f) % (w * 1.3f) - w * 0.15f, h * 0.20f),
                scale = w * 0.0022f,
                alpha = 0.82f
            )
            drawFluffyCloud(
                center = Offset(((w * 0.72f) + cloudDrift * w * 0.22f) % (w * 1.3f) - w * 0.15f, h * 0.33f),
                scale = w * 0.0028f,
                alpha = 0.72f
            )
            drawFluffyCloud(
                center = Offset(((w * 0.45f) + cloudDrift * w * 0.18f) % (w * 1.3f) - w * 0.15f, h * 0.66f),
                scale = w * 0.0032f,
                alpha = 0.60f
            )

            // Pigeon 1 ("happy")
            val p1x = w * (0.12f + happyProgress.value * 0.78f)
            val p1y = h * 0.36f + sin(wingPhase1.toDouble()).toFloat() * 14f - happyProgress.value * (h * 0.06f)
            val p1Size = w * 0.22f
            drawWhitePigeonWithTag(
                center = Offset(p1x, p1y),
                pigeonSize = p1Size,
                wingAngle = sin(wingPhase1.toDouble()).toFloat(),
                swingAngle = sin(wingPhase1.toDouble() * 0.5).toFloat() * 6f
            )

            // Pigeon 2 ("hitu") in Scene 2
            if (currentScene == 2 || hituProgress.value > -0.2f) {
                val p2x = w * (0.04f + hituProgress.value * 0.76f)
                val p2y = h * 0.48f + sin(wingPhase2.toDouble()).toFloat() * 14f - hituProgress.value * (h * 0.05f)
                val p2Size = w * 0.21f
                drawWhitePigeonWithTag(
                    center = Offset(p2x, p2y),
                    pigeonSize = p2Size,
                    wingAngle = sin(wingPhase2.toDouble()).toFloat(),
                    swingAngle = sin(wingPhase2.toDouble() * 0.5).toFloat() * 6f
                )
            }
        }

        // Crisp Compose name tag labels synchronized to the pigeons' feet strings
        Box(modifier = Modifier.fillMaxSize()) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.foundation.layout.BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val widthPx = with(density) { maxWidth.toPx() }
                val heightPx = with(density) { maxHeight.toPx() }

                val p1x = widthPx * (0.12f + happyProgress.value * 0.78f)
                val p1y = heightPx * 0.36f + sin(wingPhase1.toDouble()).toFloat() * 14f - happyProgress.value * (heightPx * 0.06f)
                val p1Size = widthPx * 0.22f

                HangingNameTag(
                    label = "happy",
                    modifier = Modifier.offset {
                        IntOffset(
                            (p1x - p1Size * 0.22f).roundToInt(),
                            (p1y + p1Size * 0.56f).roundToInt()
                        )
                    }
                )

                if (currentScene == 2 || hituProgress.value > -0.2f) {
                    val p2x = widthPx * (0.04f + hituProgress.value * 0.76f)
                    val p2y = heightPx * 0.48f + sin(wingPhase2.toDouble()).toFloat() * 14f - hituProgress.value * (heightPx * 0.05f)
                    val p2Size = widthPx * 0.21f

                    HangingNameTag(
                        label = "hitu",
                        modifier = Modifier.offset {
                            IntOffset(
                                (p2x - p2Size * 0.22f).roundToInt(),
                                (p2y + p2Size * 0.56f).roundToInt()
                            )
                        }
                    )
                }
            }
        }

        // Skip button in safe top-right corner
        TextButton(
            onClick = onFinished,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .safeDrawingPadding()
                .padding(12.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.25f))
                .testTag("splash_skip_button")
        ) {
            Text(
                text = "Skip Intro",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge
            )
        }

        // Bottom Cinematic Brand Banner
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding()
                .padding(bottom = 44.dp, start = 24.dp, end = 24.dp)
                .alpha(titleAlpha.value),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnimatedVisibility(
                visible = currentScene == 2,
                enter = fadeIn(tween(600)),
                exit = fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF090E21).copy(alpha = 0.65f))
                        .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(50))
                        .padding(horizontal = 14.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "happy  &  hitu  •  Flying Together",
                        color = Color(0xFFE0F2FE),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFF050711).copy(alpha = 0.72f),
                                Color(0xFF1E1B4B).copy(alpha = 0.78f),
                                Color(0xFF050711).copy(alpha = 0.72f)
                            )
                        )
                    )
                    .border(
                        1.dp,
                        Brush.horizontalGradient(listOf(Color(0xFF38BDF8), Color(0xFFA855F7))),
                        RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 28.dp, vertical = 16.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Makwana_Hitu.07",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "50GB Video Vault • Direct RTMP Broadcast Studio",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFBAE6FD)
                    )
                }
            }
        }
    }
}

@Composable
private fun HangingNameTag(
    label: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFFFF8E7))
            .border(1.2.dp, Color(0xFF92400E), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            color = Color(0xFF1E1B4B),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        )
    }
}

private fun DrawScope.drawFluffyCloud(
    center: Offset,
    scale: Float,
    alpha: Float
) {
    val c = Color.White.copy(alpha = alpha)
    drawCircle(c, radius = 38f * scale, center = center)
    drawCircle(c, radius = 30f * scale, center = center + Offset(-34f * scale, 10f * scale))
    drawCircle(c, radius = 32f * scale, center = center + Offset(36f * scale, 8f * scale))
    drawCircle(c, radius = 24f * scale, center = center + Offset(64f * scale, 16f * scale))
    drawRoundRect(
        color = c,
        topLeft = center + Offset(-55f * scale, 6f * scale),
        size = Size(135f * scale, 32f * scale),
        cornerRadius = CornerRadius(18f * scale, 18f * scale)
    )
}

private fun DrawScope.drawWhitePigeonWithTag(
    center: Offset,
    pigeonSize: Float,
    wingAngle: Float,
    swingAngle: Float
) {
    // Soft sun halo behind white pigeon
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = 0.35f), Color.Transparent),
            center = center,
            radius = pigeonSize * 0.95f
        ),
        radius = pigeonSize * 0.95f,
        center = center
    )

    // Back wing (flapping)
    val wingY = -pigeonSize * 0.36f * wingAngle
    val backWing = Path().apply {
        moveTo(center.x - pigeonSize * 0.05f, center.y - pigeonSize * 0.05f)
        quadraticTo(
            center.x - pigeonSize * 0.35f,
            center.y - pigeonSize * 0.45f + wingY,
            center.x - pigeonSize * 0.55f,
            center.y - pigeonSize * 0.25f + wingY * 1.1f
        )
        quadraticTo(
            center.x - pigeonSize * 0.25f,
            center.y + pigeonSize * 0.02f,
            center.x + pigeonSize * 0.05f,
            center.y + pigeonSize * 0.02f
        )
        close()
    }
    drawPath(backWing, color = Color(0xFFE2E8F0))

    // Tail feathers
    val tail = Path().apply {
        moveTo(center.x - pigeonSize * 0.22f, center.y + pigeonSize * 0.06f)
        lineTo(center.x - pigeonSize * 0.54f, center.y + pigeonSize * 0.22f)
        lineTo(center.x - pigeonSize * 0.46f, center.y + pigeonSize * 0.02f)
        close()
    }
    drawPath(tail, color = Color(0xFFF1F5F9))

    // Body
    drawOval(
        color = Color.White,
        topLeft = Offset(center.x - pigeonSize * 0.30f, center.y - pigeonSize * 0.14f),
        size = Size(pigeonSize * 0.58f, pigeonSize * 0.32f)
    )

    // Head
    val headCenter = Offset(center.x + pigeonSize * 0.28f, center.y - pigeonSize * 0.12f)
    drawCircle(color = Color.White, radius = pigeonSize * 0.13f, center = headCenter)

    // Eye
    drawCircle(
        color = Color(0xFF0F172A),
        radius = pigeonSize * 0.024f,
        center = headCenter + Offset(pigeonSize * 0.04f, -pigeonSize * 0.02f)
    )

    // Beak
    val beak = Path().apply {
        moveTo(headCenter.x + pigeonSize * 0.11f, headCenter.y - pigeonSize * 0.02f)
        lineTo(headCenter.x + pigeonSize * 0.23f, headCenter.y + pigeonSize * 0.02f)
        lineTo(headCenter.x + pigeonSize * 0.10f, headCenter.y + pigeonSize * 0.05f)
        close()
    }
    drawPath(beak, color = Color(0xFFF59E0B))

    // Front flapping wing
    val frontWing = Path().apply {
        moveTo(center.x + pigeonSize * 0.05f, center.y - pigeonSize * 0.02f)
        quadraticTo(
            center.x - pigeonSize * 0.12f,
            center.y - pigeonSize * 0.52f + wingY * 1.25f,
            center.x - pigeonSize * 0.48f,
            center.y - pigeonSize * 0.36f + wingY * 1.35f
        )
        quadraticTo(
            center.x - pigeonSize * 0.20f,
            center.y + pigeonSize * 0.06f,
            center.x + pigeonSize * 0.08f,
            center.y + pigeonSize * 0.05f
        )
        close()
    }
    drawPath(frontWing, color = Color.White)

    // Feet & tiny hanging string to the name tag
    val footBase = Offset(center.x - pigeonSize * 0.04f, center.y + pigeonSize * 0.16f)
    drawLine(
        color = Color(0xFFF59E0B),
        start = footBase,
        end = footBase + Offset(-pigeonSize * 0.04f, pigeonSize * 0.12f),
        strokeWidth = 3.5f,
        cap = StrokeCap.Round
    )
    rotate(swingAngle, pivot = footBase + Offset(-pigeonSize * 0.04f, pigeonSize * 0.12f)) {
        drawLine(
            color = Color(0xFFFEF3C7),
            start = footBase + Offset(-pigeonSize * 0.04f, pigeonSize * 0.12f),
            end = Offset(center.x - pigeonSize * 0.04f, center.y + pigeonSize * 0.58f),
            strokeWidth = 2.4f,
            cap = StrokeCap.Round
        )
    }
}

package com.vaniflow.app.ui.avatar

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaniflow.app.ui.avatar.model.AvatarEmotion
import com.vaniflow.app.ui.avatar.viseme.Viseme
import com.vaniflow.app.ui.components.getCharacterAvatarRes

/**
 * High-level discrete mouth shape for visual lip articulation.
 */
enum class MouthShape {
    REST,
    CLOSED,
    SMALL_OPEN,
    MEDIUM_OPEN,
    WIDE_OPEN,
    ROUND_O
}

/**
 * Facial anchor coordinates for each character calibrated directly from their 1024x1024 PNG source.
 */
data class CharacterFacialProfile(
    val characterId: String,
    val mouthCenterX: Float,
    val mouthCenterY: Float,
    val mouthWidthRatio: Float,
    val mouthHeightRatio: Float,
    val lipColor: Color = Color(0xFFC7656A)
)

object CharacterFacialRegistry {
    val rayaProfile = CharacterFacialProfile(
        characterId = "raya",
        mouthCenterX = 0.500f,
        mouthCenterY = 0.576f, // 590px / 1024px
        mouthWidthRatio = 0.155f,
        mouthHeightRatio = 0.058f
    )

    val rudraProfile = CharacterFacialProfile(
        characterId = "rudra",
        mouthCenterX = 0.500f,
        mouthCenterY = 0.581f, // 595px / 1024px
        mouthWidthRatio = 0.160f,
        mouthHeightRatio = 0.060f
    )

    val adwaitaProfile = CharacterFacialProfile(
        characterId = "adwaita",
        mouthCenterX = 0.500f,
        mouthCenterY = 0.556f, // 570px / 1024px
        mouthWidthRatio = 0.150f,
        mouthHeightRatio = 0.056f
    )

    val shubProfile = CharacterFacialProfile(
        characterId = "shub",
        mouthCenterX = 0.500f,
        mouthCenterY = 0.600f, // 615px / 1024px
        mouthWidthRatio = 0.155f,
        mouthHeightRatio = 0.058f
    )

    fun getProfile(characterId: String): CharacterFacialProfile {
        return when (characterId.lowercase().trim()) {
            "raya" -> rayaProfile
            "rudra" -> rudraProfile
            "adwaita" -> adwaitaProfile
            "shub" -> shubProfile
            else -> rayaProfile
        }
    }
}

fun mapToMouthShape(openness: Float, viseme: Viseme = Viseme.REST): MouthShape {
    return when {
        openness < 0.06f || viseme == Viseme.REST -> MouthShape.REST
        viseme == Viseme.M_B_P -> MouthShape.CLOSED
        viseme == Viseme.O || viseme == Viseme.U -> MouthShape.ROUND_O
        openness < 0.28f -> MouthShape.SMALL_OPEN
        openness < 0.62f -> MouthShape.MEDIUM_OPEN
        else -> MouthShape.WIDE_OPEN
    }
}

/**
 * Production Talking Avatar Renderer for VaniFlow.
 *
 * Uses native hardware-accelerated 2D Bitmap Mesh deformation (`drawBitmapMesh`) to animate
 * the ACTUAL lips and jaw of the photorealistic character portrait in real time.
 *
 * NO artificial vector paths, fake teeth, or secondary mouth overlays are drawn over the portrait.
 * The original facial textures, lips, and shading remain 100% continuous and realistic.
 */
@Composable
fun TalkingAvatarRenderer(
    characterId: String,
    avatarState: AvatarState,
    mouthOpenness: Float,
    currentViseme: Viseme = Viseme.REST,
    emotion: AvatarEmotion = AvatarEmotion.NEUTRAL,
    showDebugOverlay: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val profile = CharacterFacialRegistry.getProfile(characterId)
    val resId = getCharacterAvatarRes(characterId)

    // Cached decoded bitmap and reusable mesh transformer
    val bitmap = remember(resId) {
        BitmapFactory.decodeResource(context.resources, resId)
    }
    val faceTransform = remember { AvatarFaceTransform(meshWidth = 20, meshHeight = 20) }
    val paint = remember {
        Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
            isDither = true
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "talkingAvatar")

    // 1. Natural Sinusoidal Breathing (3.6s cycle)
    val breathingTranslationY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 4.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathingTranslationY"
    )
    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.012f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathingScale"
    )

    // 2. State-driven Head Tilt
    val headRotation = when (avatarState) {
        AvatarState.THINKING -> -1.5f
        AvatarState.CURIOUS -> 1.2f
        AvatarState.LISTENING -> 0.5f
        else -> 0.0f
    }

    val isSpeaking = avatarState == AvatarState.SPEAKING ||
            avatarState == AvatarState.HAPPY ||
            avatarState == AvatarState.CURIOUS ||
            avatarState == AvatarState.ENCOURAGING ||
            avatarState == AvatarState.SURPRISED

    val effectiveOpenness = if (isSpeaking) mouthOpenness.coerceIn(0f, 1f) else 0f
    val activeMouthShape = if (isSpeaking) mapToMouthShape(effectiveOpenness, currentViseme) else MouthShape.REST

    // 3. State Presence Aura
    val auraColor = when (avatarState) {
        AvatarState.LISTENING -> Color(0xFF06B6D4).copy(alpha = 0.22f)
        AvatarState.THINKING -> Color(0xFFF59E0B).copy(alpha = 0.18f)
        AvatarState.SPEAKING, AvatarState.HAPPY -> Color(0xFF10B981).copy(alpha = 0.20f)
        AvatarState.ENCOURAGING -> Color(0xFF8B5CF6).copy(alpha = 0.20f)
        AvatarState.INTERRUPTED -> Color(0xFFEF4444).copy(alpha = 0.22f)
        else -> Color.Transparent
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.70f),
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
    ) {
        // Glowing Presence Aura
        if (auraColor != Color.Transparent) {
            Box(
                modifier = Modifier
                    .size(300.dp)
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(auraColor, Color.Transparent)
                        )
                    )
            )
        }

        // Layer 1: Native 2D Bitmap Mesh Deforming the ACTUAL Portrait Pixels
        if (bitmap != null) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(y = breathingTranslationY.dp)
                    .scale(breathingScale)
                    .rotate(headRotation)
            ) {
                drawIntoCanvas { canvas ->
                    val nativeCanvas = canvas.nativeCanvas
                    val canvasW = size.width
                    val canvasH = size.height

                    val verts = faceTransform.computeDeformedMesh(
                        canvasW = canvasW,
                        canvasH = canvasH,
                        mouthCenterX = profile.mouthCenterX,
                        mouthCenterY = profile.mouthCenterY,
                        openness = effectiveOpenness,
                        sourceW = bitmap.width.toFloat(),
                        sourceH = bitmap.height.toFloat()
                    )

                    nativeCanvas.drawBitmapMesh(
                        bitmap,
                        faceTransform.meshWidth,
                        faceTransform.meshHeight,
                        verts,
                        0,
                        null,
                        0,
                        paint
                    )
                }
            }
        }

        // Layer 2: Subtle Bottom Gradient Fade for Video Call Viewport
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.background.copy(alpha = 0.75f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        )

        // Layer 3: Debug Overlay (Optional)
        if (showDebugOverlay) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.70f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSpeaking) Color(0xFF22C55E) else Color(0xFF38BDF8)
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Mode: Native Bitmap Mesh | State: ${avatarState.name}",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "Energy: ${"%.2f".format(effectiveOpenness)} | Shape: ${activeMouthShape.name}",
                        color = Color(0xFFE2E8F0),
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}
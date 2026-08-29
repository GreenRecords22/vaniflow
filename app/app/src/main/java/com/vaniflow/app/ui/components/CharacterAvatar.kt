package com.vaniflow.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vaniflow.app.R
import com.vaniflow.app.ui.avatar.AvatarState
import com.vaniflow.app.ui.avatar.TalkingAvatarRenderer
import com.vaniflow.app.ui.avatar.model.AvatarEmotion
import com.vaniflow.app.ui.avatar.viseme.Viseme

/**
 * Returns the drawable resource for a given character ID.
 */
fun getCharacterAvatarRes(characterId: String): Int {
    return when (characterId.lowercase().trim()) {
        "raya" -> R.drawable.avatar_raya
        "adwaita" -> R.drawable.avatar_adwaita
        "shub" -> R.drawable.avatar_shub
        "rudra" -> R.drawable.avatar_rudra
        else -> R.drawable.avatar_shub
    }
}

/**
 * Compact circular character avatar for lists, dashboard cards, and navigation.
 */
@Composable
fun CharacterAvatar(
    characterId: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    shape: Shape = CircleShape,
    borderWidth: Dp = 0.dp,
    borderColor: Color = Color.Transparent,
    avatarState: AvatarState = AvatarState.IDLE
) {
    val ringColor = when (avatarState) {
        AvatarState.IDLE -> Color.Transparent
        AvatarState.LISTENING -> Color(0xFF42A5F5)
        AvatarState.THINKING -> Color(0xFFFFB300)
        AvatarState.SPEAKING, AvatarState.HAPPY, AvatarState.CURIOUS, AvatarState.ENCOURAGING -> Color(0xFF66BB6A)
        AvatarState.INTERRUPTED -> Color(0xFFEF4444)
        AvatarState.ERROR -> Color(0xFFEF4444)
        else -> Color.Transparent
    }

    val pulse by if (avatarState == AvatarState.SPEAKING || avatarState == AvatarState.LISTENING || avatarState == AvatarState.HAPPY) {
        val transition = rememberInfiniteTransition(label = "avatarPulse")
        transition.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.06f,
            animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing)),
            label = "avatarPulse"
        )
    } else {
        val transition = rememberInfiniteTransition(label = "avatarStatic")
        transition.animateFloat(1.0f, 1.0f, infiniteRepeatable(tween(1)), label = "avatarStatic")
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .scale(pulse)
            .clip(shape)
            .background(Color.Transparent)
            .then(
                if (borderWidth > 0.dp || ringColor != Color.Transparent) {
                    Modifier.border(
                        width = if (ringColor != Color.Transparent) 3.dp else borderWidth,
                        color = if (ringColor != Color.Transparent) ringColor else borderColor,
                        shape
                    )
                } else Modifier
            )
    ) {
        Image(
            painter = painterResource(id = getCharacterAvatarRes(characterId)),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Realistic talking avatar presentation for ConversationScreen.
 * Powered by [TalkingAvatarRenderer] with real-time 2D mouth articulation,
 * eye blinks, breathing movement, and state postures.
 */
@Composable
fun ImmersiveCharacterAvatar(
    characterId: String,
    avatarState: AvatarState,
    mouthOpenness: Float = 0.0f,
    currentViseme: Viseme = Viseme.REST,
    emotion: AvatarEmotion = AvatarEmotion.NEUTRAL,
    showDebugOverlay: Boolean = true,
    modifier: Modifier = Modifier
) {
    TalkingAvatarRenderer(
        characterId = characterId,
        avatarState = avatarState,
        mouthOpenness = mouthOpenness,
        currentViseme = currentViseme,
        emotion = emotion,
        showDebugOverlay = showDebugOverlay,
        modifier = modifier
    )
}
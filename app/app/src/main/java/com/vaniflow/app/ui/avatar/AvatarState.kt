package com.vaniflow.app.ui.avatar

import com.vaniflow.app.domain.model.ConversationState
import com.vaniflow.app.ui.avatar.model.AvatarEmotion

/**
 * Visual conversation states for the avatar, mirroring the engine's
 * [ConversationState] and active emotional expression.
 */
enum class AvatarState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    HAPPY,
    CURIOUS,
    SURPRISED,
    ENCOURAGING,
    INTERRUPTED,
    ERROR
}

/** Maps the engine conversation state to the avatar visual state. */
fun ConversationState.toAvatarState(emotion: AvatarEmotion = AvatarEmotion.NEUTRAL): AvatarState = when (this) {
    ConversationState.IDLE -> AvatarState.IDLE
    ConversationState.LISTENING -> AvatarState.LISTENING
    ConversationState.USER_SPEAKING -> AvatarState.LISTENING
    ConversationState.TRANSCRIBING -> AvatarState.LISTENING
    ConversationState.THINKING -> AvatarState.THINKING
    ConversationState.AI_SPEAKING -> when (emotion) {
        AvatarEmotion.HAPPY, AvatarEmotion.EXCITED -> AvatarState.HAPPY
        AvatarEmotion.CURIOUS -> AvatarState.CURIOUS
        AvatarEmotion.SURPRISED -> AvatarState.SURPRISED
        AvatarEmotion.ENCOURAGING -> AvatarState.ENCOURAGING
        else -> AvatarState.SPEAKING
    }
    ConversationState.INTERRUPTED -> AvatarState.INTERRUPTED
    ConversationState.ERROR -> AvatarState.ERROR
    ConversationState.SESSION_COMPLETE -> AvatarState.IDLE
}
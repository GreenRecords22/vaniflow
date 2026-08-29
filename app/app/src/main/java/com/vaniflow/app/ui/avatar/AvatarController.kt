package com.vaniflow.app.ui.avatar

import com.vaniflow.app.domain.model.ConversationState
import com.vaniflow.app.ui.avatar.model.AvatarEmotion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Drives the avatar visual and emotional state from the conversation engine.
 */
@Singleton
class AvatarController @Inject constructor() {

    private val _avatarState = MutableStateFlow(AvatarState.IDLE)
    val avatarState: StateFlow<AvatarState> = _avatarState.asStateFlow()

    private val _currentEmotion = MutableStateFlow(AvatarEmotion.NEUTRAL)
    val currentEmotion: StateFlow<AvatarEmotion> = _currentEmotion.asStateFlow()

    fun update(conversationState: ConversationState, emotion: AvatarEmotion = _currentEmotion.value) {
        _currentEmotion.value = emotion
        _avatarState.value = conversationState.toAvatarState(emotion)
    }

    fun setEmotion(emotion: AvatarEmotion) {
        _currentEmotion.value = emotion
    }

    fun reset() {
        _currentEmotion.value = AvatarEmotion.NEUTRAL
        _avatarState.value = AvatarState.IDLE
    }
}
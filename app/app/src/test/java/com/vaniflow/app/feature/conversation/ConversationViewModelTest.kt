package com.vaniflow.app.feature.conversation

import com.vaniflow.app.domain.model.Character
import com.vaniflow.app.domain.model.ConversationState
import com.vaniflow.app.domain.model.Scenario
import com.vaniflow.app.domain.model.SessionScore
import com.vaniflow.app.domain.model.SkillLevel
import com.vaniflow.app.engine.ai.AIEngine
import com.vaniflow.app.engine.audio.AudioRecorder
import com.vaniflow.app.engine.audio.VADEngine
import com.vaniflow.app.engine.character.CharacterRegistry
import com.vaniflow.app.engine.conversation.ConversationEngine
import com.vaniflow.app.engine.scenario.ScenarioRegistry
import com.vaniflow.app.engine.stt.STTEngine
import com.vaniflow.app.engine.stt.STTResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import androidx.lifecycle.SavedStateHandle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationViewModelTest {

    private lateinit var engine: ConversationEngine
    private lateinit var stt: STTEngine
    private lateinit var dispatcher: TestDispatcher

    private val character = Character(
        id = "raya", name = "Raya", personality = "friendly", level = SkillLevel.INTERMEDIATE,
        voiceId = "en_IN_raya_female", speakingRate = 1.0f, correctionStyle = "gentle",
        avatarUrl = null, systemPrompt = "sys"
    )
    private val scenario = Scenario(
        id = "s", title = "Title", description = "desc", category = "cat",
        difficulty = SkillLevel.INTERMEDIATE, estimatedMinutes = 5,
        initialMessage = "Hi Alex, I'm Raya.", systemPrompt = "sys", targetVocabulary = emptyList()
    )

    private fun buildViewModel(): ConversationViewModel {
        val engineState = MutableStateFlow(ConversationState.IDLE)
        val engineTurns = MutableStateFlow<List<com.vaniflow.app.domain.model.ConversationTurn>>(emptyList())
        val engineError = MutableStateFlow<String?>(null)

        every { engine.state } returns engineState
        every { engine.turns } returns engineTurns
        every { engine.errorMessage } returns engineError
        coEvery { engine.playInitialGreeting() } returns Unit
        coEvery { engine.startSession(any(), any()) } returns Unit
        coEvery { engine.interrupt() } returns Unit
        coEvery { engine.cancelAll() } returns Unit

        val charReg = mockk<CharacterRegistry>()
        val scenReg = mockk<ScenarioRegistry>()
        every { charReg.getCharacter(any()) } returns character
        every { scenReg.getScenario(any()) } returns scenario

        val audio = mockk<AudioRecorder>(relaxed = true)
        val vad = mockk<VADEngine>(relaxed = true)

        val savedState = SavedStateHandle(mapOf("characterId" to "raya", "scenarioId" to "s"))
        return ConversationViewModel(engine, audio, vad, stt, charReg, scenReg, savedState)
    }

    @Before
    fun setup() {
        dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        engine = mockk(relaxed = true)
        stt = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `onPermissionDenied shows rationale and friendly message (no raw text)`() = runTest {
        val vm = buildViewModel()
        dispatcher.scheduler.advanceUntilIdle()
        vm.onPermissionDenied()
        val state = vm.uiState.value
        assertTrue(state.requiresPermissionRationale)
        assertEquals("Microphone access is needed for speaking practice.", state.errorMessage)
        assertFalse(state.errorMessage!!.contains("RECORD_AUDIO", ignoreCase = true))
    }

    @Test
    fun `onPermissionPermanentlyDenied routes to settings`() = runTest {
        val vm = buildViewModel()
        dispatcher.scheduler.advanceUntilIdle()
        vm.onPermissionPermanentlyDenied()
        assertTrue(vm.uiState.value.requiresPermissionSettings)
    }

    @Test
    fun `STT error is sanitized to friendly message`() = runTest {
        var callCount = 0
        coEvery { stt.startListening() } answers {
            if (callCount++ == 0) flow { emit(STTResult.Error("java.net.SocketException: broken pipe")) }
            else flowOf()
        }
        val vm = buildViewModel()
        dispatcher.scheduler.advanceUntilIdle()
        val observed = mutableListOf<String?>()
        val collectJob = launch { vm.uiState.collect { observed.add(it.errorMessage) } }
        vm.onMicTapped() // enters listening, launches STT
        dispatcher.scheduler.advanceUntilIdle()
        collectJob.cancel()
        assertTrue(
            "Friendly STT message must be shown",
            observed.any { it == "I didn't catch that. Tap the mic and try again." }
        )
        assertTrue(
            "Raw exception must not leak to UI",
            observed.none { it?.contains("SocketException", ignoreCase = true) ?: false }
        )
    }

    @Test
    fun `mic tap during AI_SPEAKING triggers engine interruption`() = runTest {
        val engineState = MutableStateFlow(ConversationState.AI_SPEAKING)
        every { engine.state } returns engineState
        coEvery { engine.errorMessage } returns MutableStateFlow<String?>(null)
        coEvery { engine.turns } returns MutableStateFlow(emptyList())
        coEvery { engine.playInitialGreeting() } returns Unit
        coEvery { engine.startSession(any(), any()) } returns Unit
        coEvery { engine.interrupt() } returns Unit
        coEvery { stt.startListening() } returns flowOf()

        val charReg = mockk<CharacterRegistry>()
        val scenReg = mockk<ScenarioRegistry>()
        every { charReg.getCharacter(any()) } returns character
        every { scenReg.getScenario(any()) } returns scenario
        val audio = mockk<AudioRecorder>(relaxed = true)
        val vad = mockk<VADEngine>(relaxed = true)
        val vm = ConversationViewModel(engine, audio, vad, stt, charReg, scenReg, SavedStateHandle(mapOf("characterId" to "raya", "scenarioId" to "s")))
        dispatcher.scheduler.advanceUntilIdle()

        vm.onMicTapped()
        dispatcher.scheduler.advanceUntilIdle()
        coVerify { engine.interrupt() }
    }

    @Test
    fun `clearErrorMessage resets error banner`() = runTest {
        val vm = buildViewModel()
        dispatcher.scheduler.advanceUntilIdle()
        vm.onPermissionDenied()
        assertTrue(vm.uiState.value.errorMessage != null)
        vm.clearErrorMessage()
        assertEquals(null, vm.uiState.value.errorMessage)
    }

    @Test
    fun `onEndSession passes the engine's real session id to the summary`() = runTest {
        every { engine.getCurrentSessionId() } returns "real_session_id_xyz"
        every { engine.endSession() } returns SessionScore(
            speakingTimeMinutes = 2,
            fluencyScore = 80,
            grammarScore = 85,
            pronunciationScore = 92,
            vocabularyScore = 80,
            strongestArea = "Pronunciation",
            focusNext = "Past Tense",
            focusNextExplanation = "Practice past tense."
        )
        val vm = buildViewModel()
        dispatcher.scheduler.advanceUntilIdle()
        var captured = ""
        vm.onEndSession { captured = it }
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("real_session_id_xyz", captured)
    }
}

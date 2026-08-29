package com.vaniflow.app.engine.ai

import com.vaniflow.app.engine.ai.analytics.DailyConversationUsageTracker
import com.vaniflow.app.engine.ai.cache.AIResponseCache
import com.vaniflow.app.engine.ai.cache.CacheCategory
import com.vaniflow.app.engine.ai.cache.CachedAIResponse
import com.vaniflow.app.engine.ai.memory.ConversationMemoryManager
import com.vaniflow.app.engine.ai.provider.*
import com.vaniflow.app.engine.ai.provider.adapter.GeminiProviderAdapter
import com.vaniflow.app.engine.ai.provider.adapter.OpenAICompatibleAdapter
import com.vaniflow.app.engine.ai.routing.ResponseDecisionType
import com.vaniflow.app.engine.ai.routing.SmartResponseDecisionEngine
import com.vaniflow.app.ui.avatar.AvatarFaceTransform
import com.vaniflow.app.ui.avatar.CharacterFacialRegistry
import io.mockk.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Full Real-World Verification and Audit Test Suite for VaniFlow.
 */
class Milestone28RealWorldAuditTest {

    private lateinit var memoryManager: ConversationMemoryManager
    private lateinit var mockCache: AIResponseCache
    private lateinit var usageTracker: DailyConversationUsageTracker
    private lateinit var quotaManager: ProviderQuotaManager
    private lateinit var healthManager: ProviderHealthManager
    private lateinit var configStore: ApiConfigStore
    private lateinit var mockOpenAIAdapter: OpenAICompatibleAdapter
    private lateinit var mockGeminiAdapter: GeminiProviderAdapter
    private lateinit var remotePrimary: RemoteAIProvider
    private lateinit var remoteSecondary: SecondaryRemoteAIProvider
    private lateinit var localProvider: LocalAIProvider
    private lateinit var fallbackProvider: FallbackAIProvider
    private lateinit var providerRegistry: ProviderRegistry
    private lateinit var router: SmartAIRouter
    private lateinit var decisionEngine: SmartResponseDecisionEngine
    private lateinit var faceTransform: AvatarFaceTransform

    @Before
    fun setup() {
        memoryManager = ConversationMemoryManager()
        mockCache = mockk(relaxed = true)
        usageTracker = DailyConversationUsageTracker()
        quotaManager = ProviderQuotaManager()
        healthManager = ProviderHealthManager(quotaManager)
        configStore = ApiConfigStore()
        mockOpenAIAdapter = mockk(relaxed = true)
        mockGeminiAdapter = mockk(relaxed = true)
        faceTransform = AvatarFaceTransform(20, 20)

        remotePrimary = RemoteAIProvider(healthManager, ConversationalDialogueEngine(), configStore, mockOpenAIAdapter, mockGeminiAdapter)
        remoteSecondary = SecondaryRemoteAIProvider(healthManager, ConversationalDialogueEngine(), configStore, mockOpenAIAdapter, mockGeminiAdapter)
        localProvider = mockk(relaxed = true) {
            every { providerId } returns "local_vaniflow"
            every { priority } returns 3
            every { isAvailable() } returns true
        }
        fallbackProvider = mockk(relaxed = true) {
            every { providerId } returns "context_fallback"
            every { priority } returns 4
            every { isAvailable() } returns true
        }

        providerRegistry = ProviderRegistry(listOf(remotePrimary, remoteSecondary, localProvider, fallbackProvider))
        decisionEngine = SmartResponseDecisionEngine(memoryManager, mockCache, usageTracker, providerRegistry)
        router = SmartAIRouter(providerRegistry, memoryManager, usageTracker, FallbackAIEngine(), mockCache, decisionEngine)
    }

    @Test
    fun testTwentyQuestionBenchmarkProducesTwentyDistinctContextualOutputs() = runBlocking {
        memoryManager.addTurn("user", "My name is Rahul and I love paneer tikka.")

        val questions = listOf(
            "What's your favorite food?",
            "Do you enjoy travelling?",
            "What do you usually do on weekends?",
            "Tell me something interesting about India.",
            "Why do you like music?",
            "What would you do on a rainy day?",
            "I'm feeling tired today.",
            "Tell me something surprising.",
            "What is your favorite movie?",
            "Do you like cricket?",
            "What makes a good friend?",
            "What place would you love to visit?",
            "Why is Jaipur interesting?",
            "Teach me an English phrase.",
            "What should I do this weekend?",
            "What makes people happy?",
            "Tell me a funny fact.",
            "What food would you recommend?",
            "Why do people travel?",
            "What are we talking about?"
        )

        val responses = mutableListOf<String>()

        for (q in questions) {
            val result = router.generateResponse("You are Raya", emptyList(), q)
            assertTrue("Query '$q' must succeed", result is AIResult.Success)
            val text = (result as AIResult.Success).text
            assertFalse("Response for '$q' must not be blank", text.isBlank())
            assertFalse("Must not expose internal error strings", text.contains("429") || text.contains("offline model") || text.contains("as an AI"))
            responses.add(text)
        }

        // Verify distinctness across all 20 responses
        val uniqueCount = responses.distinct().size
        assertEquals("All 20 benchmark questions must produce unique responses", 20, uniqueCount)
    }

    @Test
    fun testMultiPreferenceMemoryRecallAndDistinction() = runBlocking {
        memoryManager.addTurn("user", "My favorite food is paneer tikka.")
        memoryManager.addTurn("assistant", "I love paneer tikka too!")
        memoryManager.addTurn("user", "I also love dosa.")

        val prefResult = memoryManager.findMemoryAnswer("What food did I say I liked?")
        assertNotNull(prefResult)
        assertTrue(prefResult!!.contains("paneer", ignoreCase = true) || prefResult.contains("dosa", ignoreCase = true))

        // Ensure distinct questions do NOT collapse into memory answers
        assertNull("Capital question must not be intercepted by memory", memoryManager.findMemoryAnswer("What is the capital of France?"))
        assertNull("Travel question must not be intercepted by memory", memoryManager.findMemoryAnswer("Why do people enjoy travelling?"))
    }

    @Test
    fun testNinetyMinuteBoundaryCases() = runBlocking {
        // 89:59 -> Not exceeded
        usageTracker.addSpeakingDurationSeconds(89 * 60 + 59)
        assertFalse(usageTracker.isFairUseExceeded())
        var decision = decisionEngine.evaluateDecision("Tell me a story", "raya", "general")
        assertEquals(ResponseDecisionType.REMOTE_AI_REQUIRED, decision.type)

        // 90:01 -> Exceeded
        usageTracker.addSpeakingDurationSeconds(2)
        assertTrue(usageTracker.isFairUseExceeded())
        decision = decisionEngine.evaluateDecision("Tell me a story", "raya", "general")
        assertEquals(ResponseDecisionType.LOCAL_AI_REQUIRED, decision.type)
    }

    @Test
    fun testProviderFailoverCascadeWithCircuitBreakers() = runBlocking {
        // Normal -> Primary
        var decision = decisionEngine.evaluateDecision("Hello", "raya", "general")
        assertEquals("remote_primary", decision.selectedProvider?.providerId)

        // 429 on primary -> Trips breaker -> Routes to Secondary
        healthManager.recordFailure(remotePrimary, isRateLimit = true)
        assertFalse(healthManager.isAvailable(remotePrimary))
        decision = decisionEngine.evaluateDecision("Hello", "raya", "general")
        assertEquals("remote_secondary", decision.selectedProvider?.providerId)

        // Secondary fails -> Routes to Local SLM
        remoteSecondary.config = remoteSecondary.config.copy(isEnabled = false)
        decision = decisionEngine.evaluateDecision("Hello", "raya", "general")
        assertEquals("local_vaniflow", decision.selectedProvider?.providerId)
    }

    @Test
    fun testAvatarMeshDeformationAllCharacters() {
        val characters = listOf(
            CharacterFacialRegistry.rayaProfile,
            CharacterFacialRegistry.rudraProfile,
            CharacterFacialRegistry.adwaitaProfile,
            CharacterFacialRegistry.shubProfile
        )

        for (c in characters) {
            val rest = faceTransform.computeDeformedMesh(500f, 500f, c.mouthCenterX, c.mouthCenterY, 0.0f).clone()
            val open = faceTransform.computeDeformedMesh(500f, 500f, c.mouthCenterX, c.mouthCenterY, 0.7f)

            var movementCount = 0
            for (i in 0 until faceTransform.totalVertices) {
                val dy = kotlin.math.abs(open[i * 2 + 1] - rest[i * 2 + 1])
                if (dy > 0.01f) movementCount++
            }
            assertTrue("Character ${c.characterId} must exhibit localized mesh movement", movementCount > 0)
        }
    }
}
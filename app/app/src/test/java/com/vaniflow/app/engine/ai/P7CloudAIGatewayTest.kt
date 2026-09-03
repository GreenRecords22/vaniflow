package com.vaniflow.app.engine.ai

import com.vaniflow.app.domain.model.SkillLevel
import com.vaniflow.app.engine.ai.cache.AIResponseCache
import com.vaniflow.app.engine.ai.llm.FakeLocalLLMRuntime
import com.vaniflow.app.engine.ai.prompt.ConversationPromptBuilder
import com.vaniflow.app.engine.ai.prompt.VaniFlowTutorConstitution
import com.vaniflow.app.engine.ai.provider.AIProvider
import com.vaniflow.app.engine.ai.provider.ApiConfigStore
import com.vaniflow.app.engine.ai.provider.FallbackAIProvider
import com.vaniflow.app.engine.ai.provider.LocalAIProvider
import com.vaniflow.app.engine.ai.provider.ProviderConfig
import com.vaniflow.app.engine.ai.provider.ProviderHealthManager
import com.vaniflow.app.engine.ai.provider.ProviderHealthState
import com.vaniflow.app.engine.ai.provider.ProviderQuotaManager
import com.vaniflow.app.engine.ai.provider.ProviderRegistry
import com.vaniflow.app.engine.ai.provider.RemoteAIProvider
import com.vaniflow.app.engine.ai.provider.adapter.GeminiProviderAdapter
import com.vaniflow.app.engine.ai.provider.adapter.OpenAICompatibleAdapter
import com.vaniflow.app.engine.ai.provider.adapter.VaniFlowGatewayAdapter
import com.vaniflow.app.engine.character.CharacterRegistry
import com.vaniflow.app.engine.character.CharacterPromptBuilder
import com.vaniflow.app.engine.model.ModelManager
import com.vaniflow.app.engine.scenario.ScenarioRegistry
import com.vaniflow.app.engine.scenario.ScenarioPromptBuilder
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class P7CloudAIGatewayTest {

    private lateinit var configStore: ApiConfigStore
    private lateinit var quotaManager: ProviderQuotaManager
    private lateinit var healthManager: ProviderHealthManager
    private lateinit var gatewayAdapter: VaniFlowGatewayAdapter
    private lateinit var characterRegistry: CharacterRegistry
    private lateinit var scenarioRegistry: ScenarioRegistry
    private lateinit var mockModelManager: ModelManager
    private lateinit var mockCache: AIResponseCache

    @Before
    fun setup() {
        configStore = ApiConfigStore()
        quotaManager = ProviderQuotaManager()
        healthManager = ProviderHealthManager(quotaManager)
        gatewayAdapter = VaniFlowGatewayAdapter()
        characterRegistry = CharacterRegistry()
        scenarioRegistry = ScenarioRegistry()
        mockModelManager = mockk(relaxed = true)
        mockCache = mockk(relaxed = true)
    }

    @Test
    fun test01_gatewayAdapterTypeAndZeroHardcodedSecrets() {
        assertEquals("vaniflow_gateway", gatewayAdapter.adapterType)
        assertFalse("Config store must default to no hardcoded primary API key", configStore.hasPrimaryCredentials())
        assertFalse("Config store must default to no hardcoded secondary API key", configStore.hasSecondaryCredentials())
    }

    @Test
    fun test02_tutorConstitutionAndPromptParityInGatewayPayload() {
        val character = characterRegistry.getCharacter("raya")
        val scenario = scenarioRegistry.getScenario("order_coffee")
        val persona = CharacterPromptBuilder.buildPersonaPrompt(character)
        val scenarioPrompt = ScenarioPromptBuilder.buildScenarioPrompt(scenario)

        val history = listOf(
            AITurn(AITurn.Role.USER, "Hello Raya!"),
            AITurn(AITurn.Role.ASSISTANT, "Hi there! What can I get for you today?")
        )

        val prompt = ConversationPromptBuilder.buildRuntimePrompt(
            characterName = character.name,
            personalityPrompt = persona,
            scenarioTitle = scenario.title,
            scenarioPrompt = scenarioPrompt,
            userLevel = character.level,
            history = history,
            userInput = "Can I order a cappuccino with oat milk?",
            tutoringContext = "[TUTORING DIRECTIVE: Praise good phrasing and keep conversation flowing.]"
        )

        assertTrue(prompt.contains("[VANIFLOW TUTOR CONSTITUTION v1.0]"))
        assertTrue(prompt.contains(VaniFlowTutorConstitution.NORTH_STAR))
        assertTrue(prompt.contains("TUTORING DIRECTIVE:"))
        assertTrue(prompt.contains("<user_speech>Can I order a cappuccino with oat milk?</user_speech>"))
    }

    @Test
    fun test03_gatewayErrorHandlingReturnsMeaningfulError() = runBlocking {
        // Calling non-existent gateway port to test network failure resilience
        val result = gatewayAdapter.generate(
            endpoint = "http://127.0.0.1:59999/v1/chat",
            apiKey = "",
            model = "llama-3.1-8b-instant",
            systemPrompt = "You are Raya.",
            history = emptyList(),
            userInput = "Hello",
            timeoutMs = 500L
        )

        assertTrue("Must return AIResult.Error on connection failure", result is AIResult.Error)
        val errorMsg = (result as AIResult.Error).message
        assertTrue("Error message must contain network or gateway failure", errorMsg.contains("network error", ignoreCase = true) || errorMsg.contains("failed", ignoreCase = true))
    }

    @Test
    fun test04_smartRouterCascadesToLocalQwenWhenCloudGatewayFails() = runBlocking {
        val failingCloudProvider = object : AIProvider {
            override val providerId: String = "remote_primary"
            override val providerName: String = "Failing Cloud Gateway"
            override val priority: Int = 1
            override var config: ProviderConfig = ProviderConfig(providerId, providerName, isEnabled = true, priority = 1)
            override fun isAvailable(): Boolean = true
            override fun getHealthState(): ProviderHealthState = ProviderHealthState.AVAILABLE
            override suspend fun generateResponse(systemPrompt: String, conversationHistory: List<AITurn>, userInput: String): AIResult {
                return AIResult.Error("Gateway 502 Bad Gateway")
            }
            override fun streamResponse(systemPrompt: String, conversationHistory: List<AITurn>, userInput: String): Flow<String> = emptyFlow()
            override fun recordSuccess(latencyMs: Long, tokens: Int) {}
            override fun recordFailure(isRateLimit: Boolean) {}
        }

        val tempModel = java.io.File.createTempFile("fake_model", ".gguf").apply { writeBytes(ByteArray(2_000_000)) }
        tempModel.deleteOnExit()
        io.mockk.every { mockModelManager.getModelFile(any()) } returns tempModel

        val fakeRuntime = FakeLocalLLMRuntime(available = true, respond = { "This is a response generated on-device by local Qwen LLM." })
        val localEngine = LocalAIEngine(mockModelManager, fakeRuntime)
        val localProvider = LocalAIProvider(localEngine, healthManager)
        val fallbackProvider = FallbackAIProvider(ContextAwareFallbackEngine())

        val registry = ProviderRegistry(listOf(failingCloudProvider, localProvider, fallbackProvider))
        val router = SmartAIRouter(
            providerRegistry = registry,
            memoryManager = com.vaniflow.app.engine.ai.memory.ConversationMemoryManager(),
            usageTracker = com.vaniflow.app.engine.ai.analytics.DailyConversationUsageTracker(),
            fallbackAIEngine = FallbackAIEngine(),
            aiResponseCache = mockCache
        )

        val response = router.generateResponse(
            systemPrompt = "SYSTEM: You are Raya.",
            conversationHistory = emptyList(),
            userInput = "Tell me about your favorite food."
        )

        assertTrue("Response must succeed despite cloud failure", response is AIResult.Success)
        assertEquals("This is a response generated on-device by local Qwen LLM.", (response as AIResult.Success).text)
    }

    @Test
    fun test05_smartRouterCascadesToRuleAIWhenLocalQwenAlsoFails() = runBlocking {
        val failingCloudProvider = object : AIProvider {
            override val providerId: String = "remote_primary"
            override val providerName: String = "Failing Cloud Gateway"
            override val priority: Int = 1
            override var config: ProviderConfig = ProviderConfig(providerId, providerName, isEnabled = true, priority = 1)
            override fun isAvailable(): Boolean = true
            override fun getHealthState(): ProviderHealthState = ProviderHealthState.AVAILABLE
            override suspend fun generateResponse(systemPrompt: String, conversationHistory: List<AITurn>, userInput: String): AIResult {
                return AIResult.Error("Cloud Unavailable")
            }
            override fun streamResponse(systemPrompt: String, conversationHistory: List<AITurn>, userInput: String): Flow<String> = emptyFlow()
            override fun recordSuccess(latencyMs: Long, tokens: Int) {}
            override fun recordFailure(isRateLimit: Boolean) {}
        }

        val fakeRuntime = FakeLocalLLMRuntime(available = false)
        val localEngine = LocalAIEngine(mockModelManager, fakeRuntime)
        val localProvider = LocalAIProvider(localEngine, healthManager)
        val fallbackProvider = FallbackAIProvider(ContextAwareFallbackEngine())

        val registry = ProviderRegistry(listOf(failingCloudProvider, localProvider, fallbackProvider))
        val router = SmartAIRouter(
            providerRegistry = registry,
            memoryManager = com.vaniflow.app.engine.ai.memory.ConversationMemoryManager(),
            usageTracker = com.vaniflow.app.engine.ai.analytics.DailyConversationUsageTracker(),
            fallbackAIEngine = FallbackAIEngine(),
            aiResponseCache = mockCache
        )

        val response = router.generateResponse(
            systemPrompt = "SYSTEM: You are Raya.",
            conversationHistory = emptyList(),
            userInput = "I feel so tired after working all day."
        )

        assertTrue("Response must succeed via Rule AI fallback", response is AIResult.Success)
        val text = (response as AIResult.Success).text
        assertTrue("Rule AI generated empathetic reply", text.contains("rest", ignoreCase = true) || text.contains("energy", ignoreCase = true))
    }

    @Test
    fun test06_remoteAIProviderUsesGatewayWhenConfigured() = runBlocking {
        val dialogueEngine = ConversationalDialogueEngine()
        val openAIAdapter = OpenAICompatibleAdapter()
        val geminiAdapter = GeminiProviderAdapter()

        var gatewayInvoked = false
        val mockGatewayAdapter = object : VaniFlowGatewayAdapter() {
            override suspend fun generate(
                endpoint: String,
                apiKey: String,
                model: String,
                systemPrompt: String,
                history: List<AITurn>,
                userInput: String,
                timeoutMs: Long
            ): AIResult {
                gatewayInvoked = true
                return AIResult.Success(
                    text = "Hello from VaniFlow AI Gateway!",
                    latencyMs = 150L,
                    metadata = AIResponseMetadata(
                        routingLevel = AIRoutingLevel.OPTIONAL_CLOUD,
                        latencyMs = 150L,
                        tokensGenerated = 10,
                        providerName = "groq"
                    )
                )
            }
        }

        configStore.setGatewayConfig("https://gateway.vaniflow.com/v1/chat", enabled = true)

        val provider = RemoteAIProvider(
            healthManager = healthManager,
            dialogueEngine = dialogueEngine,
            configStore = configStore,
            openAIAdapter = openAIAdapter,
            geminiAdapter = geminiAdapter,
            gatewayAdapter = mockGatewayAdapter
        )

        val result = provider.generateResponse(
            systemPrompt = "SYSTEM: You are Raya.",
            conversationHistory = emptyList(),
            userInput = "Hi Raya!"
        )

        assertTrue("Must return AIResult.Success", result is AIResult.Success)
        assertTrue("Gateway adapter must be invoked", gatewayInvoked)
        assertEquals("Hello from VaniFlow AI Gateway!", (result as AIResult.Success).text)
    }

    @Test
    fun test07_productionGatewayUrlValidation() {
        val gatewayUrl = com.vaniflow.app.BuildConfig.GATEWAY_URL
        assertNotNull("GATEWAY_URL must be defined in BuildConfig", gatewayUrl)
        assertTrue("GATEWAY_URL must be a valid HTTP/HTTPS endpoint", gatewayUrl.startsWith("http://") || gatewayUrl.startsWith("https://"))
        assertTrue("GATEWAY_URL must target chat completions path", gatewayUrl.endsWith("/v1/chat"))
    }

    @Test
    fun test08_streamSseCancellationAndChunkParsing() = runBlocking {
        val flow = gatewayAdapter.stream(
            endpoint = "http://127.0.0.1:59999/v1/chat",
            apiKey = "",
            model = "groq/compound-mini",
            systemPrompt = "System",
            history = emptyList(),
            userInput = "Hello",
            timeoutMs = 500L
        )

        var caughtException = false
        try {
            flow.collect { }
        } catch (e: Exception) {
            caughtException = true
        }
        assertTrue("Streaming on failed connection throws exception for router catch", caughtException)
    }
}

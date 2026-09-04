package com.vaniflow.app.engine.ai.provider

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Secure runtime store for Cloud AI provider credentials and endpoints.
 * Automatically loads build-time keys, environment variables, and persisted user settings.
 */
@Singleton
class ApiConfigStore @Inject constructor(
    @ApplicationContext private val context: Context?
) {

    constructor() : this(null)

    private val prefs by lazy {
        context?.getSharedPreferences("vaniflow_api_config", Context.MODE_PRIVATE)
    }

    private var primaryApiKey: String = ""
    private var primaryEndpoint: String = "https://api.groq.com/openai/v1/chat/completions"
    private var primaryModel: String = "llama-3.3-70b-versatile"
    private var primaryAdapterType: String = "openai_compatible"

    private var secondaryApiKey: String = ""
    private var secondaryEndpoint: String = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent"
    private var secondaryModel: String = "gemini-1.5-flash"
    private var secondaryAdapterType: String = "gemini"

    private var gatewayEnabled: Boolean = false

    init {
        loadConfig()
    }

    private fun loadConfig() {
        if (context == null) {
            return
        }
        // 1. Check SharedPreferences first (user customization overrides build config)
        val savedGroqKey = prefs?.getString("primary_api_key", "") ?: ""
        val savedGroqEndpoint = prefs?.getString("primary_endpoint", "") ?: ""
        val savedGroqModel = prefs?.getString("primary_model", "") ?: ""
        val savedGroqAdapter = prefs?.getString("primary_adapter", "") ?: ""

        val savedGeminiKey = prefs?.getString("secondary_api_key", "") ?: ""
        val savedGeminiEndpoint = prefs?.getString("secondary_endpoint", "") ?: ""
        val savedGeminiModel = prefs?.getString("secondary_model", "") ?: ""
        val savedGeminiAdapter = prefs?.getString("secondary_adapter", "") ?: ""

        // 2. Baseline from BuildConfig and Environment Variables
        val buildConfigGroq = runCatching { com.vaniflow.app.BuildConfig.GROQ_API_KEY }.getOrDefault("")
        val envGroq = System.getenv("GROQ_API_KEY") ?: System.getProperty("GROQ_API_KEY") ?: ""
        val initialGroq = savedGroqKey.ifBlank { buildConfigGroq.ifBlank { envGroq } }

        val buildConfigGemini = runCatching { com.vaniflow.app.BuildConfig.GEMINI_API_KEY }.getOrDefault("")
        val envGemini = System.getenv("GEMINI_API_KEY") ?: System.getProperty("GEMINI_API_KEY") ?: ""
        val initialGemini = savedGeminiKey.ifBlank { buildConfigGemini.ifBlank { envGemini } }

        if (initialGroq.isNotBlank()) {
            primaryApiKey = initialGroq.trim()
            primaryEndpoint = savedGroqEndpoint.ifBlank { "https://api.groq.com/openai/v1/chat/completions" }
            primaryModel = savedGroqModel.ifBlank { "llama-3.3-70b-versatile" }
            primaryAdapterType = savedGroqAdapter.ifBlank { "openai_compatible" }
        }

        if (initialGemini.isNotBlank()) {
            secondaryApiKey = initialGemini.trim()
            secondaryEndpoint = savedGeminiEndpoint.ifBlank { "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent" }
            secondaryModel = savedGeminiModel.ifBlank { "gemini-1.5-flash" }
            secondaryAdapterType = savedGeminiAdapter.ifBlank { "gemini" }
        }
    }

    fun setGatewayConfig(endpoint: String, appToken: String = "", enabled: Boolean = true) {
        primaryEndpoint = endpoint.trim()
        primaryApiKey = appToken.trim()
        primaryAdapterType = "vaniflow_gateway"
        gatewayEnabled = enabled
    }

    fun setPrimaryConfig(apiKey: String, endpoint: String, model: String, adapterType: String = "openai_compatible") {
        primaryApiKey = apiKey.trim()
        primaryEndpoint = endpoint.trim()
        primaryModel = model.trim()
        primaryAdapterType = adapterType.trim()
        gatewayEnabled = (adapterType == "vaniflow_gateway")

        prefs?.edit()
            ?.putString("primary_api_key", primaryApiKey)
            ?.putString("primary_endpoint", primaryEndpoint)
            ?.putString("primary_model", primaryModel)
            ?.putString("primary_adapter", primaryAdapterType)
            ?.apply()
    }

    fun setSecondaryConfig(apiKey: String, endpoint: String, model: String, adapterType: String = "gemini") {
        secondaryApiKey = apiKey.trim()
        secondaryEndpoint = endpoint.trim()
        secondaryModel = model.trim()
        secondaryAdapterType = adapterType.trim()

        prefs?.edit()
            ?.putString("secondary_api_key", secondaryApiKey)
            ?.putString("secondary_endpoint", secondaryEndpoint)
            ?.putString("secondary_model", secondaryModel)
            ?.putString("secondary_adapter", secondaryAdapterType)
            ?.apply()
    }

    fun getPrimaryApiKey(): String = primaryApiKey
    fun getPrimaryEndpoint(): String = primaryEndpoint
    fun getPrimaryModel(): String = primaryModel
    fun getPrimaryAdapterType(): String = primaryAdapterType
    fun hasPrimaryCredentials(): Boolean = primaryApiKey.isNotBlank()
    fun isGatewayConfigured(): Boolean = gatewayEnabled

    fun getSecondaryApiKey(): String = secondaryApiKey
    fun getSecondaryEndpoint(): String = secondaryEndpoint
    fun getSecondaryModel(): String = secondaryModel
    fun getSecondaryAdapterType(): String = secondaryAdapterType
    fun hasSecondaryCredentials(): Boolean = secondaryApiKey.isNotBlank()

    fun clear() {
        primaryApiKey = ""
        secondaryApiKey = ""
        gatewayEnabled = false
        prefs?.edit()?.clear()?.apply()
    }
}
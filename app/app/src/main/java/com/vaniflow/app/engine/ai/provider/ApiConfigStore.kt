package com.vaniflow.app.engine.ai.provider

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Secure runtime store for Cloud AI provider credentials and endpoints.
 * Never bundles hardcoded API keys in source code or compiled APK.
 */
@Singleton
class ApiConfigStore @Inject constructor() {

    private var primaryApiKey: String = ""
    private var primaryEndpoint: String = "https://api.groq.com/openai/v1/chat/completions"
    private var primaryModel: String = "llama-3.1-8b-instant"
    private var primaryAdapterType: String = "openai_compatible"

    private var secondaryApiKey: String = ""
    private var secondaryEndpoint: String = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent"
    private var secondaryModel: String = "gemini-1.5-flash"
    private var secondaryAdapterType: String = "gemini"

    fun setPrimaryConfig(apiKey: String, endpoint: String, model: String, adapterType: String = "openai_compatible") {
        primaryApiKey = apiKey.trim()
        primaryEndpoint = endpoint.trim()
        primaryModel = model.trim()
        primaryAdapterType = adapterType.trim()
    }

    fun setSecondaryConfig(apiKey: String, endpoint: String, model: String, adapterType: String = "gemini") {
        secondaryApiKey = apiKey.trim()
        secondaryEndpoint = endpoint.trim()
        secondaryModel = model.trim()
        secondaryAdapterType = adapterType.trim()
    }

    fun getPrimaryApiKey(): String = primaryApiKey
    fun getPrimaryEndpoint(): String = primaryEndpoint
    fun getPrimaryModel(): String = primaryModel
    fun getPrimaryAdapterType(): String = primaryAdapterType
    fun hasPrimaryCredentials(): Boolean = primaryApiKey.isNotBlank()

    fun getSecondaryApiKey(): String = secondaryApiKey
    fun getSecondaryEndpoint(): String = secondaryEndpoint
    fun getSecondaryModel(): String = secondaryModel
    fun getSecondaryAdapterType(): String = secondaryAdapterType
    fun hasSecondaryCredentials(): Boolean = secondaryApiKey.isNotBlank()

    fun clear() {
        primaryApiKey = ""
        secondaryApiKey = ""
    }
}
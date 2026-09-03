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
    private var primaryEndpoint: String = "http://10.0.2.2:8080/v1/chat"
    private var primaryModel: String = "llama-3.1-8b-instant"
    private var primaryAdapterType: String = "vaniflow_gateway"

    private var secondaryApiKey: String = ""
    private var secondaryEndpoint: String = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent"
    private var secondaryModel: String = "gemini-1.5-flash"
    private var secondaryAdapterType: String = "gemini"

    private var gatewayEnabled: Boolean = false

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
    }
}
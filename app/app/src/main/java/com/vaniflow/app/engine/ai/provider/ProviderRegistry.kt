package com.vaniflow.app.engine.ai.provider

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registry of all available AI providers ordered by priority.
 */
@Singleton
class ProviderRegistry(
    private val providersList: List<AIProvider>
) {
    @Inject
    constructor(
        remotePrimary: RemoteAIProvider,
        remoteSecondary: SecondaryRemoteAIProvider,
        localProvider: LocalAIProvider,
        fallbackProvider: FallbackAIProvider
    ) : this(listOf(remotePrimary, remoteSecondary, localProvider, fallbackProvider))

    val allProviders: List<AIProvider>
        get() = providersList.sortedBy { it.priority }

    fun getHealthyProviders(): List<AIProvider> {
        return allProviders.filter { it.isAvailable() }
    }

    fun getPrimaryActiveProvider(): AIProvider {
        return getHealthyProviders().firstOrNull() ?: allProviders.last()
    }
}
package com.vaniflow.app.di

import com.vaniflow.app.engine.ai.AIEngine
import com.vaniflow.app.engine.ai.MockAIEngine
import com.vaniflow.app.engine.ai.llm.LocalLLMRuntime
import com.vaniflow.app.engine.ai.llm.LlamaCppRuntime
import com.vaniflow.app.engine.audio.AndroidAudioRecordManager
import com.vaniflow.app.engine.audio.AudioRecorder
import com.vaniflow.app.engine.audio.EnergyVADEngine
import com.vaniflow.app.engine.audio.VADEngine
import com.vaniflow.app.engine.learning.FeedbackEngine
import com.vaniflow.app.engine.learning.MockFeedbackEngine
import com.vaniflow.app.engine.model.DefaultModelManager
import com.vaniflow.app.engine.model.ModelManager
import com.vaniflow.app.engine.stt.RealOfflineSTTEngine
import com.vaniflow.app.engine.stt.STTEngine
import com.vaniflow.app.engine.tts.RealOfflineTTSEngine
import com.vaniflow.app.engine.tts.TTSEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt Dependency Injection Module for VaniFlow Engines.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindModelManager(modelManager: DefaultModelManager): ModelManager

    @Binds
    @Singleton
    abstract fun bindLocalLLMRuntime(llamaCppRuntime: LlamaCppRuntime): LocalLLMRuntime

    @Binds
    @Singleton
    abstract fun bindAudioRecorder(audioRecordManager: AndroidAudioRecordManager): AudioRecorder

    @Binds
    @Singleton
    abstract fun bindVADEngine(energyVADEngine: EnergyVADEngine): VADEngine

    @Binds
    @Singleton
    abstract fun bindSTTEngine(realOfflineSTTEngine: RealOfflineSTTEngine): STTEngine

    @Binds
    @Singleton
    abstract fun bindTTSEngine(realOfflineTTSEngine: RealOfflineTTSEngine): TTSEngine

    @Binds
    @Singleton
    abstract fun bindAIEngine(smartAIRouter: com.vaniflow.app.engine.ai.SmartAIRouter): AIEngine

    @Binds
    @Singleton
    abstract fun bindFeedbackEngine(defaultFeedbackEngine: com.vaniflow.app.engine.learning.DefaultFeedbackEngine): FeedbackEngine

    @Binds
    @Singleton
    abstract fun bindAIResponseCache(defaultAIResponseCache: com.vaniflow.app.engine.ai.cache.DefaultAIResponseCache): com.vaniflow.app.engine.ai.cache.AIResponseCache

    @Binds
    @Singleton
    abstract fun bindLipSyncController(visemeLipSyncController: com.vaniflow.app.ui.avatar.VisemeLipSyncController): com.vaniflow.app.ui.avatar.LipSyncController
}

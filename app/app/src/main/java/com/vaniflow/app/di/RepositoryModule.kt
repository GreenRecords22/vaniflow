package com.vaniflow.app.di

import com.vaniflow.app.data.repository.DefaultProgressRepository
import com.vaniflow.app.data.repository.DefaultSessionRepository
import com.vaniflow.app.data.repository.DefaultVocabularyRepository
import com.vaniflow.app.domain.repository.ProgressRepository
import com.vaniflow.app.domain.repository.SessionRepository
import com.vaniflow.app.domain.repository.VocabularyRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindSessionRepository(defaultSessionRepository: DefaultSessionRepository): SessionRepository

    @Binds
    @Singleton
    abstract fun bindProgressRepository(defaultProgressRepository: DefaultProgressRepository): ProgressRepository

    @Binds
    @Singleton
    abstract fun bindVocabularyRepository(defaultVocabularyRepository: DefaultVocabularyRepository): VocabularyRepository

    @Binds
    @Singleton
    abstract fun bindUserPreferencesRepository(defaultUserPreferencesRepository: com.vaniflow.app.data.repository.DefaultUserPreferencesRepository): com.vaniflow.app.domain.repository.UserPreferencesRepository

    @Binds
    @Singleton
    abstract fun bindLearnerProfileRepository(defaultLearnerProfileRepository: com.vaniflow.app.data.repository.DefaultLearnerProfileRepository): com.vaniflow.app.domain.repository.LearnerProfileRepository

    @Binds
    @Singleton
    abstract fun bindDailyUsageRepository(defaultDailyUsageRepository: com.vaniflow.app.data.repository.DefaultDailyUsageRepository): com.vaniflow.app.domain.repository.DailyUsageRepository

    @Binds
    @Singleton
    abstract fun bindLearningEventRepository(defaultLearningEventRepository: com.vaniflow.app.data.repository.DefaultLearningEventRepository): com.vaniflow.app.domain.repository.LearningEventRepository

    @Binds
    @Singleton
    abstract fun bindConceptMasteryRepository(defaultConceptMasteryRepository: com.vaniflow.app.data.repository.DefaultConceptMasteryRepository): com.vaniflow.app.domain.repository.ConceptMasteryRepository

    @Binds
    @Singleton
    abstract fun bindVocabularyMemoryRepository(defaultVocabularyMemoryRepository: com.vaniflow.app.data.repository.DefaultVocabularyMemoryRepository): com.vaniflow.app.domain.repository.VocabularyMemoryRepository
}

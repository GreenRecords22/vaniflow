package com.vaniflow.app.data

import com.vaniflow.app.data.local.db.dao.LearnerProfileDao
import com.vaniflow.app.data.local.db.entity.LearnerProfileEntity
import com.vaniflow.app.data.repository.DefaultLearnerProfileRepository
import com.vaniflow.app.engine.learning.tutor.CorrectionSeverity
import com.vaniflow.app.engine.learning.tutor.EnglishError
import com.vaniflow.app.engine.learning.tutor.EnglishErrorCategory
import com.vaniflow.app.engine.learning.tutor.EstimatedLevel
import com.vaniflow.app.engine.learning.tutor.LearnerProfile
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LearnerProfilePersistenceTest {

    private lateinit var learnerProfileDao: LearnerProfileDao
    private lateinit var repository: DefaultLearnerProfileRepository
    private lateinit var dispatcher: TestDispatcher

    @Before
    fun setup() {
        dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        learnerProfileDao = mockk(relaxed = true)
        repository = DefaultLearnerProfileRepository(learnerProfileDao, dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `saving learner profile persists mistakes and mastery correctly`() = runTest {
        val slot = slot<LearnerProfileEntity>()
        coEvery { learnerProfileDao.saveProfile(capture(slot)) } returns Unit

        val profile = LearnerProfile(
            estimatedLevel = EstimatedLevel.B1,
            speakingConfidenceScore = 78.5f,
            totalUtterances = 24,
            correctionsDelivered = 5,
            successfulRetries = 4
        )
        profile.recordMistake("past_buyed", EnglishErrorCategory.TENSE)
        profile.recordMistake("sva_he_dont", EnglishErrorCategory.SUBJECT_VERB_AGREEMENT)
        profile.recordSuccessfulRetry("past_buyed", EnglishErrorCategory.TENSE)
        profile.recentCorrections.add(
            EnglishError(
                originalText = "buyed",
                suggestedText = "bought",
                category = EnglishErrorCategory.TENSE,
                severity = CorrectionSeverity.IMPORTANT,
                explanation = "Use 'bought' instead of 'buyed'.",
                ruleIdentifier = "past_buyed"
            )
        )

        repository.saveLearnerProfile(profile)

        coVerify { learnerProfileDao.saveProfile(any()) }
        val captured = slot.captured
        assertEquals("B1", captured.estimatedLevel)
        assertEquals(78.5f, captured.speakingConfidenceScore, 0.01f)
        assertTrue(captured.commonMistakesJson.contains("past_buyed"))
        assertTrue(captured.commonMistakesJson.contains("sva_he_dont"))
        assertTrue(captured.masteredConceptsJson.contains("past_buyed"))
        assertTrue(captured.recentCorrectionsJson.contains("bought"))
    }

    @Test
    fun `loading persisted learner profile reconstructs all memory fields faithfully`() = runTest {
        val entity = LearnerProfileEntity(
            id = "default_learner_profile",
            estimatedLevel = "B2",
            speakingConfidenceScore = 82.0f,
            totalUtterances = 50,
            correctionsDelivered = 8,
            successfulRetries = 7,
            commonMistakesJson = "{\"past_buyed\":3,\"prep_good_in\":2}",
            masteredConceptsJson = "[\"past_buyed\",\"sva_he_dont\"]",
            conceptsNeedingPracticeJson = "[\"tense\",\"prepositions\"]",
            recentCorrectionsJson = "[{\"originalText\":\"good in\",\"suggestedText\":\"good at\",\"category\":\"PREPOSITIONS\",\"severity\":\"MINOR\",\"explanation\":\"Say good at\",\"ruleIdentifier\":\"prep_good_in\"}]",
            updatedAt = System.currentTimeMillis()
        )

        coEvery { learnerProfileDao.getProfile() } returns entity

        // Create new repository instance simulating app restart
        val freshRepo = DefaultLearnerProfileRepository(learnerProfileDao, dispatcher)
        val loadedProfile = freshRepo.getLearnerProfile()

        assertEquals(EstimatedLevel.B2, loadedProfile.estimatedLevel)
        assertEquals(82.0f, loadedProfile.speakingConfidenceScore, 0.01f)
        assertEquals(50, loadedProfile.totalUtterances)
        assertEquals(8, loadedProfile.correctionsDelivered)
        assertEquals(7, loadedProfile.successfulRetries)
        assertEquals(3, loadedProfile.commonMistakes["past_buyed"])
        assertEquals(2, loadedProfile.commonMistakes["prep_good_in"])
        assertTrue(loadedProfile.masteredConcepts.contains("past_buyed"))
        assertTrue(loadedProfile.masteredConcepts.contains("sva_he_dont"))
        assertTrue(loadedProfile.conceptsNeedingPractice.contains("tense"))
        assertTrue(loadedProfile.conceptsNeedingPractice.contains("prepositions"))
        assertEquals(1, loadedProfile.recentCorrections.size)
        assertEquals("good at", loadedProfile.recentCorrections.first().suggestedText)
    }
}

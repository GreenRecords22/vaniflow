package com.vaniflow.app.engine.learning.tutor

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * 30-Utterance Benchmark Suite for VaniFlow Real English Speaking Tutor Engine.
 */
class Milestone27EnglishTutorBenchmarkTest {

    private lateinit var tutorEngine: EnglishCorrectionEngine
    private lateinit var learningMemory: LearningMemoryManager

    @Before
    fun setup() {
        kotlinx.coroutines.runBlocking {
            tutorEngine = EnglishCorrectionEngine()
            learningMemory = LearningMemoryManager()
            learningMemory.ensureLoaded()
        }
    }

    @Test
    fun testThirtyUtteranceBenchmarkSuite() {
        data class BenchmarkCase(
            val input: String,
            val expectError: Boolean,
            val expectedCategory: EnglishErrorCategory?,
            val expectedSeverity: CorrectionSeverity?,
            val expectedSuggestedSnippet: String?,
            val expectRetryRequired: Boolean
        )

        val benchmarkCases = listOf(
            // 1. Correct English
            BenchmarkCase("I love drinking coffee in the morning.", false, null, null, null, false),
            BenchmarkCase("She went to the market and bought fresh vegetables.", false, null, null, null, false),
            BenchmarkCase("We have lived in this city for five years.", false, null, null, null, false),

            // 2. Irregular Past Tense
            BenchmarkCase("Yesterday I buyed vegetables.", true, EnglishErrorCategory.TENSE, CorrectionSeverity.IMPORTANT, "bought", true),
            BenchmarkCase("He teached English for two years.", true, EnglishErrorCategory.TENSE, CorrectionSeverity.IMPORTANT, "taught", true),
            BenchmarkCase("She bringed her laptop to work.", true, EnglishErrorCategory.TENSE, CorrectionSeverity.IMPORTANT, "brought", true),
            BenchmarkCase("The goalkeeper catched the ball quickly.", true, EnglishErrorCategory.TENSE, CorrectionSeverity.IMPORTANT, "caught", true),
            BenchmarkCase("The children eated all the cookies.", true, EnglishErrorCategory.TENSE, CorrectionSeverity.IMPORTANT, "ate", true),
            BenchmarkCase("I sleeped for eight hours last night.", true, EnglishErrorCategory.TENSE, CorrectionSeverity.IMPORTANT, "slept", true),

            // 3. Past Time Markers with Present Verb
            BenchmarkCase("Yesterday I go to the supermarket.", true, EnglishErrorCategory.TENSE, CorrectionSeverity.IMPORTANT, "went", true),
            BenchmarkCase("Last night I see a brilliant movie.", true, EnglishErrorCategory.TENSE, CorrectionSeverity.IMPORTANT, "saw", true),
            BenchmarkCase("I go Jaipur yesterday.", true, EnglishErrorCategory.TENSE, CorrectionSeverity.IMPORTANT, "went", true),

            // 4. Subject-Verb Agreement
            BenchmarkCase("He don't like playing cricket.", true, EnglishErrorCategory.SUBJECT_VERB_AGREEMENT, CorrectionSeverity.IMPORTANT, "doesn't", true),
            BenchmarkCase("She don't know the answer to this question.", true, EnglishErrorCategory.SUBJECT_VERB_AGREEMENT, CorrectionSeverity.IMPORTANT, "doesn't", true),
            BenchmarkCase("He have two cars.", true, EnglishErrorCategory.SUBJECT_VERB_AGREEMENT, CorrectionSeverity.IMPORTANT, "has", true),
            BenchmarkCase("My friends likes travelling to the mountains.", true, EnglishErrorCategory.SUBJECT_VERB_AGREEMENT, CorrectionSeverity.IMPORTANT, "like", true),
            BenchmarkCase("Everyone are waiting in the lobby.", true, EnglishErrorCategory.SUBJECT_VERB_AGREEMENT, CorrectionSeverity.IMPORTANT, "is", true),

            // 5. Prepositions
            BenchmarkCase("I am very good in English speaking.", true, EnglishErrorCategory.PREPOSITIONS, CorrectionSeverity.MINOR, "good at", false),
            BenchmarkCase("She is married with a doctor.", true, EnglishErrorCategory.PREPOSITIONS, CorrectionSeverity.MINOR, "married to", false),
            BenchmarkCase("I have lived here since 3 years.", true, EnglishErrorCategory.PREPOSITIONS, CorrectionSeverity.IMPORTANT, "for 3 years", false),
            BenchmarkCase("Please listen me carefully.", true, EnglishErrorCategory.PREPOSITIONS, CorrectionSeverity.IMPORTANT, "listen to me", false),

            // 6. Articles
            BenchmarkCase("I want to eat a apple.", true, EnglishErrorCategory.ARTICLES, CorrectionSeverity.MINOR, "an apple", false),
            BenchmarkCase("She is an doctor at the local hospital.", true, EnglishErrorCategory.ARTICLES, CorrectionSeverity.MINOR, "a doctor", false),
            BenchmarkCase("Next month I want to buy car.", true, EnglishErrorCategory.ARTICLES, CorrectionSeverity.MINOR, "buy a car", false),

            // 7. Word Order & Question Inversion
            BenchmarkCase("Where you are going this evening?", true, EnglishErrorCategory.WORD_ORDER, CorrectionSeverity.IMPORTANT, "where are you going", true),
            BenchmarkCase("What you are doing right now?", true, EnglishErrorCategory.WORD_ORDER, CorrectionSeverity.IMPORTANT, "what are you doing", true),

            // 8. Uncountable Nouns & Plurals
            BenchmarkCase("The teacher gave me many information.", true, EnglishErrorCategory.SINGULAR_PLURAL, CorrectionSeverity.IMPORTANT, "a lot of information", false),
            BenchmarkCase("We ordered new furnitures for the living room.", true, EnglishErrorCategory.SINGULAR_PLURAL, CorrectionSeverity.IMPORTANT, "furniture", false),
            BenchmarkCase("Many peoples attended the festival.", true, EnglishErrorCategory.SINGULAR_PLURAL, CorrectionSeverity.IMPORTANT, "many people", false),

            // 9. Spoken Phrasing & Collocations
            BenchmarkCase("Please open the light before entering.", true, EnglishErrorCategory.NATURAL_PHRASING, CorrectionSeverity.STYLE, "turn on the light", false),
            BenchmarkCase("Yesterday I took a bath of sun.", true, EnglishErrorCategory.NATURAL_PHRASING, CorrectionSeverity.STYLE, "sunbathed", false),
            BenchmarkCase("He passed out from college last summer.", true, EnglishErrorCategory.WORD_CHOICE, CorrectionSeverity.STYLE, "graduated from college", false),
            BenchmarkCase("I very like Indian food.", true, EnglishErrorCategory.NATURAL_PHRASING, CorrectionSeverity.STYLE, "I really like", false),
            BenchmarkCase("She go to school every day.", true, EnglishErrorCategory.SUBJECT_VERB_AGREEMENT, CorrectionSeverity.IMPORTANT, "goes", true),
            BenchmarkCase("Yesterday I was go market.", true, EnglishErrorCategory.TENSE, CorrectionSeverity.IMPORTANT, "went", true)
        )

        assertEquals("Must contain at least 30 benchmark test cases", 35, benchmarkCases.size)

        for (caseItem in benchmarkCases) {
            val decision = tutorEngine.analyzeUtterance(caseItem.input)
            assertEquals("Error presence mismatch for: '${caseItem.input}'", caseItem.expectError, decision.hasError)

            if (caseItem.expectError) {
                val primaryErr = decision.detectedErrors.firstOrNull()
                assertNotNull("Must detect error for '${caseItem.input}'", primaryErr)
                assertEquals("Category mismatch for '${caseItem.input}'", caseItem.expectedCategory, primaryErr?.category)
                assertEquals("Severity mismatch for '${caseItem.input}'", caseItem.expectedSeverity, decision.primarySeverity)
                assertTrue("Corrected sentence must contain snippet '${caseItem.expectedSuggestedSnippet}' for '${caseItem.input}'",
                    decision.correctedSentence?.contains(caseItem.expectedSuggestedSnippet!!, ignoreCase = true) == true)
                assertEquals("Retry requirement mismatch for '${caseItem.input}'", caseItem.expectRetryRequired, decision.shouldRequestRetry)

                // Feed to learning memory
                learningMemory.onUtteranceAnalyzed(decision)
            }
        }

        // Verify learning memory updated
        val profile = learningMemory.profile
        assertTrue("Mistakes should be tracked in profile", profile.commonMistakes.isNotEmpty())
        assertTrue("Concepts needing practice should be populated", profile.conceptsNeedingPractice.isNotEmpty())
    }

    @Test
    fun testRetryCycleEvaluationSuccess() {
        val initialInput = "Yesterday I buyed vegetables."
        val decision = tutorEngine.analyzeUtterance(initialInput)
        assertTrue(decision.hasError)
        assertTrue(decision.shouldRequestRetry)

        val targetError = decision.detectedErrors.first { it.ruleIdentifier == "past_buyed" }

        // User retry attempt: corrected
        val retryInput = "Yesterday I bought vegetables."
        val evaluation = tutorEngine.evaluateRetry(targetError, initialInput, retryInput)

        assertTrue("Retry must be evaluated as fixed", evaluation.isFixed)
        assertFalse("Retry must not be partial", evaluation.isPartiallyFixed)
        assertTrue("Must provide encouraging praise", evaluation.praiseFeedback.contains("Perfect", ignoreCase = true) || evaluation.praiseFeedback.contains("Much smoother", ignoreCase = true))

        learningMemory.onRetryEvaluated(evaluation)
        assertTrue("Profile must record successful retry", learningMemory.profile.successfulRetries > 0)
        assertTrue("Past buyed must be in mastered concepts", learningMemory.profile.masteredConcepts.contains("past_buyed"))
    }

    @Test
    fun testConfidenceFirstNonIntrusiveCoaching() {
        // Minor article mistake should not interrupt with harsh feedback
        val input = "I want to eat a apple."
        val decision = tutorEngine.analyzeUtterance(input)
        assertTrue(decision.hasError)
        assertEquals(CorrectionSeverity.MINOR, decision.primarySeverity)
        assertEquals(CorrectionTiming.NO_CORRECTION, decision.timing)
        assertFalse("Minor article slip should not force a retry", decision.shouldRequestRetry)
    }
}
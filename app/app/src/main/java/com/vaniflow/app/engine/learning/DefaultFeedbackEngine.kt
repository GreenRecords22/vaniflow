package com.vaniflow.app.engine.learning

import com.vaniflow.app.domain.model.Correction
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production FeedbackEngine implementation powered by GrammarEngine.
 */
@Singleton
class DefaultFeedbackEngine @Inject constructor(
    private val grammarEngine: GrammarEngine
) : FeedbackEngine {

    override suspend fun analyzeUtterance(userInput: String): Correction? {
        return grammarEngine.analyze(userInput)
    }
}

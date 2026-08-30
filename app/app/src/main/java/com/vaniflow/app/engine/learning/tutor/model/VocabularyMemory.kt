package com.vaniflow.app.engine.learning.tutor.model

import java.util.UUID

data class VocabularyMemory(
    val id: String = UUID.randomUUID().toString(),
    val wordOrPhrase: String,
    val phonetic: String = "",
    val partOfSpeech: String = "expression",
    val meaning: String,
    val exampleSentence: String,
    val familiarityScore: Int = 20, // 0 to 100
    val usageCount: Int = 1,
    val lastUsedEpochMs: Long = System.currentTimeMillis(),
    val sourceScenarioId: String? = null
)

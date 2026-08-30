package com.vaniflow.app.data.repository

import com.vaniflow.app.data.local.db.dao.LearnerProfileDao
import com.vaniflow.app.data.local.db.entity.LearnerProfileEntity
import com.vaniflow.app.domain.repository.LearnerProfileRepository
import com.vaniflow.app.engine.learning.tutor.CorrectionSeverity
import com.vaniflow.app.engine.learning.tutor.EnglishError
import com.vaniflow.app.engine.learning.tutor.EnglishErrorCategory
import com.vaniflow.app.engine.learning.tutor.EstimatedLevel
import com.vaniflow.app.engine.learning.tutor.LearnerProfile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultLearnerProfileRepository @Inject constructor(
    private val learnerProfileDao: LearnerProfileDao
) : LearnerProfileRepository {

    private var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    constructor(learnerProfileDao: LearnerProfileDao, dispatcher: CoroutineDispatcher) : this(learnerProfileDao) {
        this.ioDispatcher = dispatcher
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun getLearnerProfile(): LearnerProfile = withContext(ioDispatcher) {
        val entity = learnerProfileDao.getProfile()
        if (entity != null) {
            mapEntityToProfile(entity)
        } else {
            val defaultProfile = LearnerProfile()
            saveLearnerProfile(defaultProfile)
            defaultProfile
        }
    }

    override suspend fun saveLearnerProfile(profile: LearnerProfile): Unit = withContext(ioDispatcher) {
        val entity = mapProfileToEntity(profile)
        learnerProfileDao.saveProfile(entity)
    }

    override fun observeLearnerProfile(): Flow<LearnerProfile?> {
        return learnerProfileDao.getProfileFlow().map { entity ->
            entity?.let { mapEntityToProfile(it) }
        }
    }

    override suspend fun clearLearnerProfile(): Unit = withContext(ioDispatcher) {
        learnerProfileDao.deleteProfile()
    }

    private fun mapProfileToEntity(profile: LearnerProfile): LearnerProfileEntity {
        val commonMistakesJson = buildJsonObject {
            profile.commonMistakes.forEach { (k, v) ->
                put(k, v)
            }
        }.toString()

        val masteredConceptsJson = buildJsonArray {
            profile.masteredConcepts.forEach { add(it) }
        }.toString()

        val conceptsNeedingPracticeJson = buildJsonArray {
            profile.conceptsNeedingPractice.forEach { add(it) }
        }.toString()

        val recentCorrectionsJson = buildJsonArray {
            profile.recentCorrections.takeLast(20).forEach { err ->
                add(buildJsonObject {
                    put("originalText", err.originalText)
                    put("suggestedText", err.suggestedText)
                    put("category", err.category.name)
                    put("severity", err.severity.name)
                    put("explanation", err.explanation)
                    put("ruleIdentifier", err.ruleIdentifier)
                })
            }
        }.toString()

        return LearnerProfileEntity(
            id = "default_learner_profile",
            estimatedLevel = profile.estimatedLevel.name,
            speakingConfidenceScore = profile.speakingConfidenceScore,
            totalUtterances = profile.totalUtterances,
            correctionsDelivered = profile.correctionsDelivered,
            successfulRetries = profile.successfulRetries,
            commonMistakesJson = commonMistakesJson,
            masteredConceptsJson = masteredConceptsJson,
            conceptsNeedingPracticeJson = conceptsNeedingPracticeJson,
            recentCorrectionsJson = recentCorrectionsJson,
            updatedAt = System.currentTimeMillis()
        )
    }

    private fun mapEntityToProfile(entity: LearnerProfileEntity): LearnerProfile {
        val level = try {
            EstimatedLevel.valueOf(entity.estimatedLevel)
        } catch (_: Exception) {
            EstimatedLevel.A2
        }

        val profile = LearnerProfile(
            estimatedLevel = level,
            speakingConfidenceScore = entity.speakingConfidenceScore,
            totalUtterances = entity.totalUtterances,
            correctionsDelivered = entity.correctionsDelivered,
            successfulRetries = entity.successfulRetries
        )

        // Parse common mistakes
        try {
            if (entity.commonMistakesJson.isNotBlank()) {
                val obj = json.parseToJsonElement(entity.commonMistakesJson).jsonObject
                obj.forEach { (k, v) ->
                    val count = v.jsonPrimitive.content.toIntOrNull() ?: 0
                    profile.commonMistakes[k] = count
                }
            }
        } catch (_: Exception) {}

        // Parse mastered concepts
        try {
            if (entity.masteredConceptsJson.isNotBlank()) {
                val arr = json.parseToJsonElement(entity.masteredConceptsJson).jsonArray
                arr.forEach {
                    profile.masteredConcepts.add(it.jsonPrimitive.content)
                }
            }
        } catch (_: Exception) {}

        // Parse concepts needing practice
        try {
            if (entity.conceptsNeedingPracticeJson.isNotBlank()) {
                val arr = json.parseToJsonElement(entity.conceptsNeedingPracticeJson).jsonArray
                arr.forEach {
                    profile.conceptsNeedingPractice.add(it.jsonPrimitive.content)
                }
            }
        } catch (_: Exception) {}

        // Parse recent corrections
        try {
            if (entity.recentCorrectionsJson.isNotBlank()) {
                val arr = json.parseToJsonElement(entity.recentCorrectionsJson).jsonArray
                arr.forEach { el ->
                    val obj = el.jsonObject
                    val orig = obj["originalText"]?.jsonPrimitive?.content ?: ""
                    val sug = obj["suggestedText"]?.jsonPrimitive?.content ?: ""
                    val catStr = obj["category"]?.jsonPrimitive?.content ?: EnglishErrorCategory.GRAMMAR.name
                    val sevStr = obj["severity"]?.jsonPrimitive?.content ?: CorrectionSeverity.IMPORTANT.name
                    val exp = obj["explanation"]?.jsonPrimitive?.content ?: ""
                    val ruleId = obj["ruleIdentifier"]?.jsonPrimitive?.content ?: "general_rule"

                    val cat = try { EnglishErrorCategory.valueOf(catStr) } catch (_: Exception) { EnglishErrorCategory.GRAMMAR }
                    val sev = try { CorrectionSeverity.valueOf(sevStr) } catch (_: Exception) { CorrectionSeverity.IMPORTANT }

                    profile.recentCorrections.add(
                        EnglishError(
                            originalText = orig,
                            suggestedText = sug,
                            category = cat,
                            severity = sev,
                            explanation = exp,
                            ruleIdentifier = ruleId
                        )
                    )
                }
            }
        } catch (_: Exception) {}

        return profile
    }
}

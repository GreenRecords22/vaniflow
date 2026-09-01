package com.vaniflow.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "speech_analysis")
data class SpeechAnalysisEntity(
    @PrimaryKey val id: String,
    val turnId: String,
    val sessionId: String,
    val audioDurationMs: Long,
    val voicedDurationMs: Long,
    val pauseCount: Int,
    val totalPauseDurationMs: Long,
    val wordsPerMinute: Float,
    val qualitativeFluency: String,
    val qualitativePronunciation: String,
    val hesitationType: String,
    val snrDb: Float,
    val hasPhonemeEvidence: Boolean,
    val practicedSound: String?,
    val timestampEpochMs: Long = System.currentTimeMillis()
)

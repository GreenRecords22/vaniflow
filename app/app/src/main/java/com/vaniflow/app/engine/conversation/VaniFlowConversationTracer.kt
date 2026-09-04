package com.vaniflow.app.engine.conversation

import android.util.Log

data class ConversationTurnTrace(
    val turnNumber: Int,
    val sessionId: String,
    val userUtterance: String,
    val sttTranscript: String,
    val tutorAction: String,
    val correctionCategory: String?,
    val correctionDetails: String?,
    val providerName: String,
    val routingLevel: String,
    val qualityStatus: String,
    val regenerationAttempts: Int,
    val finalSpokenResponse: String,
    val ttsStatus: String,
    val latencyMs: Long
) {
    fun formatFormattedLog(): String = buildString {
        appendLine("========== [VANIFLOW DEV RUNTIME TRACE] ==========")
        appendLine("TURN  (Session: )")
        appendLine("USER: ")
        appendLine("STT: ")
        appendLine("TUTOR ACTION: ")
        if (correctionCategory != null) {
            appendLine("CORRECTION:  - ")
        } else {
            appendLine("CORRECTION: NONE")
        }
        appendLine("PROVIDER:  ()")
        appendLine("QUALITY: ")
        appendLine("REGENERATION: ")
        appendLine("FINAL RESPONSE: ")
        appendLine("TTS: ")
        appendLine("LATENCY: ms")
        appendLine("==================================================")
    }
}

object VaniFlowConversationTracer {
    private val traces = mutableListOf<ConversationTurnTrace>()

    fun recordTurn(trace: ConversationTurnTrace) {
        synchronized(traces) {
            traces.add(trace)
            if (traces.size > 100) traces.removeAt(0)
        }
        try {
            Log.d("VaniFlowDevTrace", trace.formatFormattedLog())
        } catch (_: Throwable) {
            println(trace.formatFormattedLog())
        }
    }

    fun getRecentTraces(): List<ConversationTurnTrace> = synchronized(traces) { traces.toList() }

    fun clear() = synchronized(traces) { traces.clear() }
}

package com.vaniflow.app.engine.stt

/**
 * Benchmark evaluation corpus representing authentic conversational English
 * spoken by Indian professionals, job seekers, and students.
 *
 * Covers:
 * - Common syntax & phrasing patterns
 * - Technical & workplace discussions
 * - Daily conversational small-talk
 * - Fast & concise responses
 */
object IndianEnglishCorpus {

    data class Utterance(
        val id: String,
        val text: String,
        val domain: String,
        val accentStyle: String,
        val expectedWords: Int
    )

    val benchmarkSentences: List<Utterance> = listOf(
        Utterance(
            id = "in_daily_01",
            text = "Could you please give me one hot cappuccino with oat milk?",
            domain = "Daily Life",
            accentStyle = "Urban Indian English",
            expectedWords = 11
        ),
        Utterance(
            id = "in_daily_02",
            text = "I have been living in Bangalore for the past four years.",
            domain = "Daily Life",
            accentStyle = "Standard South Indian",
            expectedWords = 11
        ),
        Utterance(
            id = "in_work_01",
            text = "In yesterday's sprint sync we resolved the database migration blocker.",
            domain = "Work / Standup",
            accentStyle = "Tech Professional",
            expectedWords = 10
        ),
        Utterance(
            id = "in_work_02",
            text = "Let us discuss the trade-offs before finalizing the product roadmap.",
            domain = "Work / Strategy",
            accentStyle = "Executive Corporate",
            expectedWords = 10
        ),
        Utterance(
            id = "in_interview_01",
            text = "I spearheaded the frontend architectural modernization using Jetpack Compose.",
            domain = "Interview",
            accentStyle = "Engineering Interview",
            expectedWords = 9
        ),
        Utterance(
            id = "in_travel_01",
            text = "I need to check in two bags for my flight to Delhi.",
            domain = "Travel",
            accentStyle = "Airport Check-in",
            expectedWords = 12
        ),
        Utterance(
            id = "in_short_01",
            text = "Yes, absolutely.",
            domain = "General",
            accentStyle = "Short Affirmation",
            expectedWords = 2
        ),
        Utterance(
            id = "in_short_02",
            text = "Sounds good to me.",
            domain = "General",
            accentStyle = "Quick Response",
            expectedWords = 4
        )
    )

    /**
     * Calculates Word Error Rate (WER) between reference and hypothesis text.
     */
    fun calculateWER(reference: String, hypothesis: String): Float {
        val refWords = reference.lowercase().trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
        val hypWords = hypothesis.lowercase().trim().split("\\s+".toRegex()).filter { it.isNotBlank() }

        if (refWords.isEmpty()) return if (hypWords.isEmpty()) 0.0f else 1.0f

        val d = Array(refWords.size + 1) { IntArray(hypWords.size + 1) }

        for (i in 0..refWords.size) d[i][0] = i
        for (j in 0..hypWords.size) d[0][j] = j

        for (i in 1..refWords.size) {
            for (j in 1..hypWords.size) {
                if (refWords[i - 1] == hypWords[j - 1]) {
                    d[i][j] = d[i - 1][j - 1]
                } else {
                    val substitution = d[i - 1][j - 1] + 1
                    val insertion = d[i][j - 1] + 1
                    val deletion = d[i - 1][j] + 1
                    d[i][j] = minOf(substitution, insertion, deletion)
                }
            }
        }

        val distance = d[refWords.size][hypWords.size]
        return (distance.toFloat() / refWords.size).coerceIn(0.0f, 1.0f)
    }
}

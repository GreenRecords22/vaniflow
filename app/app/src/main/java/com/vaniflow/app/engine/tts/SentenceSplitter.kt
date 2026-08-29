package com.vaniflow.app.engine.tts

/**
 * Utility for splitting paragraph-length text into natural sentence-level chunks
 * for low-latency streaming TTS synthesis.
 *
 * Preserves common abbreviations, honorifics, and decimal numbers.
 */
object SentenceSplitter {

    private val ABBREVIATIONS = setOf(
        "mr.", "mrs.", "ms.", "dr.", "prof.", "sr.", "jr.",
        "e.g.", "i.e.", "etc.", "vs.", "approx.", "min.", "sec."
    )

    fun splitIntoSentences(text: String): List<String> {
        if (text.isBlank()) return emptyList()

        val normalized = text.trim()
        val sentences = mutableListOf<String>()
        val current = StringBuilder()

        var i = 0
        while (i < normalized.length) {
            val char = normalized[i]
            current.append(char)

            if (char == '.' || char == '!' || char == '?' || char == '\n') {
                val currentStr = current.toString().trim()

                // Check if the current token ends with an abbreviation
                val isAbbreviation = ABBREVIATIONS.any { abbr ->
                    currentStr.endsWith(abbr, ignoreCase = true)
                }

                // Check if dot is inside a number (e.g. 3.5, $10.50)
                val isDecimalNumber = char == '.' && i + 1 < normalized.length && normalized[i + 1].isDigit()

                if (!isAbbreviation && !isDecimalNumber) {
                    if (currentStr.isNotBlank()) {
                        sentences.add(currentStr)
                        current.clear()
                    }
                }
            }
            i++
        }

        val remaining = current.toString().trim()
        if (remaining.isNotBlank()) {
            sentences.add(remaining)
        }

        return sentences
    }
}

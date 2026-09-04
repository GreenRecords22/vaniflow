package com.vaniflow.app.engine.learning.tutor

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dedicated English Speaking Tutor Correction Engine.
 * Analyzes grammar, tense, agreement, prepositions, articles, and natural phrasing.
 * Implements a confidence-first tutoring strategy: encouraging tone, gentle non-intrusive
 * feedback, and seamless retry evaluation.
 */
@Singleton
class EnglishCorrectionEngine @Inject constructor() {

    data class Rule(
        val ruleId: String,
        val regex: Regex,
        val replacementTransform: (MatchResult) -> String,
        val explanation: String,
        val category: EnglishErrorCategory,
        val severity: CorrectionSeverity,
        val requiresRetry: Boolean = false
    )

    private val rules: List<Rule> = listOf(
        // 1. Irregular Past Tense
        Rule(
            ruleId = "past_buyed",
            regex = "\\bbuyed\\b".toRegex(RegexOption.IGNORE_CASE),
            replacementTransform = { "bought" },
            explanation = "The past tense of 'buy' is 'bought'.",
            category = EnglishErrorCategory.TENSE,
            severity = CorrectionSeverity.IMPORTANT,
            requiresRetry = true
        ),
        Rule(
            ruleId = "past_teached",
            regex = "\\bteached\\b".toRegex(RegexOption.IGNORE_CASE),
            replacementTransform = { "taught" },
            explanation = "The past tense of 'teach' is 'taught'.",
            category = EnglishErrorCategory.TENSE,
            severity = CorrectionSeverity.IMPORTANT,
            requiresRetry = true
        ),
        Rule(
            ruleId = "past_bringed",
            regex = "\\bbringed\\b".toRegex(RegexOption.IGNORE_CASE),
            replacementTransform = { "brought" },
            explanation = "The past tense of 'bring' is 'brought'.",
            category = EnglishErrorCategory.TENSE,
            severity = CorrectionSeverity.IMPORTANT,
            requiresRetry = true
        ),
        Rule(
            ruleId = "past_catched",
            regex = "\\bcatched\\b".toRegex(RegexOption.IGNORE_CASE),
            replacementTransform = { "caught" },
            explanation = "The past tense of 'catch' is 'caught'.",
            category = EnglishErrorCategory.TENSE,
            severity = CorrectionSeverity.IMPORTANT,
            requiresRetry = true
        ),
        Rule(
            ruleId = "past_eated",
            regex = "\\beated\\b".toRegex(RegexOption.IGNORE_CASE),
            replacementTransform = { "ate" },
            explanation = "The past tense of 'eat' is 'ate'.",
            category = EnglishErrorCategory.TENSE,
            severity = CorrectionSeverity.IMPORTANT,
            requiresRetry = true
        ),
        Rule(
            ruleId = "past_sleeped",
            regex = "\\bsleeped\\b".toRegex(RegexOption.IGNORE_CASE),
            replacementTransform = { "slept" },
            explanation = "The past tense of 'sleep' is 'slept'.",
            category = EnglishErrorCategory.TENSE,
            severity = CorrectionSeverity.IMPORTANT,
            requiresRetry = true
        ),

        // 2. Past Time Markers with Present Verb: "yesterday I go market" -> "yesterday I went to the market"
        Rule(
            ruleId = "tense_past_marker_go_market",
            regex = "\\b(yesterday|last\\s+(?:night|week|month|year))\\s+(i|we|they|he|she)\\s+go\\s+market\\b".toRegex(RegexOption.IGNORE_CASE),
            replacementTransform = { match -> "${match.groupValues[1]} ${match.groupValues[2]} went to the market" },
            explanation = "Say 'went to the market' for completed actions in the past (e.g. 'Yesterday I went to the market').",
            category = EnglishErrorCategory.TENSE,
            severity = CorrectionSeverity.IMPORTANT,
            requiresRetry = true
        ),
        Rule(
            ruleId = "tense_i_go_market",
            regex = "\\b(i|we|they|he|she)\\s+go\\s+market\\s*(yesterday|last\\s+(?:night|week|month|year))?\\b".toRegex(RegexOption.IGNORE_CASE),
            replacementTransform = { match ->
                val time = match.groupValues[2].trim()
                if (time.isNotBlank()) "${match.groupValues[1]} went to the market $time" else "${match.groupValues[1]} went to the market"
            },
            explanation = "Say 'went to the market' when referring to going to the market in the past.",
            category = EnglishErrorCategory.TENSE,
            severity = CorrectionSeverity.IMPORTANT,
            requiresRetry = true
        ),
        Rule(
            ruleId = "tense_past_marker_go",
            regex = "\\b(yesterday|last\\s+(?:night|week|month|year))\\s+(i|we|they|he|she)\\s+go\\b".toRegex(RegexOption.IGNORE_CASE),
            replacementTransform = { match -> "${match.groupValues[1]} ${match.groupValues[2]} went" },
            explanation = "When describing completed actions in the past (like 'yesterday'), say 'went' instead of 'go'.",
            category = EnglishErrorCategory.TENSE,
            severity = CorrectionSeverity.IMPORTANT,
            requiresRetry = true
        ),
        Rule(
            ruleId = "tense_i_go_yesterday",
            regex = "\\b(i|we|they|he|she)\\s+go\\s+(yesterday|last\\s+(?:night|week|month|year))\\b".toRegex(RegexOption.IGNORE_CASE),
            replacementTransform = { match -> "${match.groupValues[1]} went ${match.groupValues[2]}" },
            explanation = "Use past tense 'went' when referring to events in the past.",
            category = EnglishErrorCategory.TENSE,
            severity = CorrectionSeverity.IMPORTANT,
            requiresRetry = true
        ),
        Rule(
            ruleId = "tense_i_go_place_yesterday",
            regex = "\\b(i|we|they|he|she)\\s+go\\s+(?:to\\s+)?([a-zA-Z]+)\\s+(yesterday|last\\s+(?:night|week|month|year))\\b".toRegex(RegexOption.IGNORE_CASE),
            replacementTransform = { match ->
                val place = match.groupValues[2]
                val formattedPlace = if (place.equals("market", ignoreCase = true)) "to the market" else if (place.equals("home", ignoreCase = true) || place.equals("there", ignoreCase = true)) place else "to $place"
                "${match.groupValues[1]} went $formattedPlace ${match.groupValues[3]}"
            },
            explanation = "Use 'went to' when describing past travel to a place (e.g., 'went to Jaipur yesterday').",
            category = EnglishErrorCategory.TENSE,
            severity = CorrectionSeverity.IMPORTANT,
            requiresRetry = true
        ),
        Rule(
            ruleId = "tense_working_since_duration",
            regex = "\\b(i|we|they)\\s+am\\s+(living|staying|working)\\s+(?:here\\s+)?since\\s+(\\d+|two|three|four|five|six|seven|eight|nine|ten)\\s+(years?|months?|days?|hours?)\\b".toRegex(RegexOption.IGNORE_CASE),
            replacementTransform = { match -> "I've been ${match.groupValues[2].lowercase()} here for ${match.groupValues[3]} ${match.groupValues[4]}" },
            explanation = "Use present perfect continuous ('I have been living/working') with 'for' to express ongoing duration (e.g. 'I have lived here for 5 years').",
            category = EnglishErrorCategory.TENSE,
            severity = CorrectionSeverity.IMPORTANT,
            requiresRetry = true
        ),
        Rule(
            ruleId = "tense_past_meet",
            regex = "\\b(yesterday|last\\s+(?:night|week|month|year))\\s+(i|we|they|he|she)\\s+meet\\b".toRegex(RegexOption.IGNORE_CASE),
            replacementTransform = { match -> "${match.groupValues[1]} ${match.groupValues[2]} met" },
            explanation = "Use past tense 'met' for completed actions in the past.",
            category = EnglishErrorCategory.TENSE,
            severity = CorrectionSeverity.IMPORTANT,
            requiresRetry = true
        ),
        Rule(
            ruleId = "tense_past_see",
            regex = "\\b(yesterday|last\\s+(?:night|week|month|year))\\s+(i|we|they)\\s+see\\b".toRegex(RegexOption.IGNORE_CASE),
            replacementTransform = { match -> "${match.groupValues[1]} ${match.groupValues[2]} saw" },
            explanation = "Use 'saw' for past actions.",
            category = EnglishErrorCategory.TENSE,
            severity = CorrectionSeverity.IMPORTANT,
            requiresRetry = true
        ),
        Rule(
            ruleId = "tense_was_go",
            regex = "\\b(i|he|she|we|they)\\s+(?:was|were)\\s+go\\s+([a-zA-Z]+)\\b".toRegex(RegexOption.IGNORE_CASE),
            replacementTransform = { match ->
                val subj = match.groupValues[1]
                val place = match.groupValues[2]
                if (place.equals("market", ignoreCase = true)) {
                    "$subj went to the market"
                } else if (place.equals("home", ignoreCase = true)) {
                    "$subj went home"
                } else {
                    "$subj went to $place"
                }
            },
            explanation = "Say 'went to' when describing past travel (e.g. 'went to the market').",
            category = EnglishErrorCategory.TENSE,
            severity = CorrectionSeverity.IMPORTANT,
            requiresRetry = true
        ),

        // 3. Subject-Verb Agreement: "he don't" -> "he doesn't", "he have" -> "he has"
        Rule(
            ruleId = "sva_he_she_go",
            regex = "\\b(he|she)\\s+go\\s+(to\\s+[a-zA-Z]+|home|every\\s+day|daily|often|always)\\b".toRegex(RegexOption.IGNORE_CASE),
            replacementTransform = { match -> "${match.groupValues[1]} goes ${match.groupValues[2]}" },
            explanation = "Use 'goes' with third-person singular subjects (he/she).",
            category = EnglishErrorCategory.SUBJECT_VERB_AGREEMENT,
            severity = CorrectionSeverity.IMPORTANT,
            requiresRetry = true
        ),
        Rule(
            ruleId = "sva_he_dont",
            regex = "\\b(he|she|it|my\\s+friend)\\s+(?:don't|dont|don’t)\\b".toRegex(RegexOption.IGNORE_CASE),
            replacementTransform = { match -> "${match.groupValues[1]} doesn't" },
            explanation = "Use 'doesn't' with third-person singular subjects (he/she/it).",
            category = EnglishErrorCategory.SUBJECT_VERB_AGREEMENT,
            severity = CorrectionSeverity.IMPORTANT,
            requiresRetry = true
        ),
        Rule(
            ruleId = "sva_he_have",
            regex = "\\b(he|she|it)\\s+have\\b".toRegex(RegexOption.IGNORE_CASE),
            replacementTransform = { match -> "${match.groupValues[1]} has" },
            explanation = "With third-person singular subjects (he/she/it), use 'has' instead of 'have'.",
            category = EnglishErrorCategory.SUBJECT_VERB_AGREEMENT,
            severity = CorrectionSeverity.IMPORTANT,
            requiresRetry = true
        ),
        Rule(
            ruleId = "sva_plural_likes",
            regex = "\\b(my\\s+friends|they|we)\\s+likes\\b".toRegex(RegexOption.IGNORE_CASE),
            replacementTransform = { match -> "${match.groupValues[1]} like" },
            explanation = "With plural subjects (they/we/friends), use the base verb 'like'.",
            category = EnglishErrorCategory.SUBJECT_VERB_AGREEMENT,
            severity = CorrectionSeverity.IMPORTANT,
            requiresRetry = true
        ),
        Rule(
            ruleId = "sva_everyone_are",
            regex = "\\b(everyone|everybody|someone|somebody)\\s+are\\b".toRegex(RegexOption.IGNORE_CASE),
            replacementTransform = { match -> "${match.groupValues[1]} is" },
            explanation = "'Everyone' and 'everybody' take singular verbs: use 'is' instead of 'are'.",
            category = EnglishErrorCategory.SUBJECT_VERB_AGREEMENT,
            severity = CorrectionSeverity.IMPORTANT,
            requiresRetry = true
        ),

        // 4. Prepositions: "good in English" -> "good at English", "married with" -> "married to"
        Rule(
            ruleId = "prep_good_in",
            regex = "\\bgood\\s+in\\s+(english|math|sports|cricket|cooking)\\b".toRegex(RegexOption.IGNORE_CASE),
            replacementTransform = { match -> "good at ${match.groupValues[1]}" },
            explanation = "Use the preposition 'at' when describing skills (e.g. 'good at English').",
            category = EnglishErrorCategory.PREPOSITIONS,
            severity = CorrectionSeverity.MINOR
        ),
        Rule(
            ruleId = "prep_married_with",
            regex = "\\bmarried\\s+with\\b".toRegex(RegexOption.IGNORE_CASE),
            replacementTransform = { "married to" },
            explanation = "In English, we say someone is 'married to' their partner.",
            category = EnglishErrorCategory.PREPOSITIONS,
            severity = CorrectionSeverity.MINOR
        ),
        Rule(
            ruleId = "prep_since_duration",
            regex = "\\bsince\\s+(\\d+|two|three|four|five)\\s+(years?|months?|days?|hours?)\\b".toRegex(RegexOption.IGNORE_CASE),
            replacementTransform = { match -> "for ${match.groupValues[1]} ${match.groupValues[2]}" },
            explanation = "Use 'for' with time durations (e.g., 'for 3 years'), and 'since' for a specific starting date.",
            category = EnglishErrorCategory.PREPOSITIONS,
            severity = CorrectionSeverity.IMPORTANT
        ),
        Rule(
            ruleId = "prep_went_destination",
            regex = "\\b(i|he|she|we|they)\\s+went\\s+(jaipur|delhi|mumbai|bangalore|goa|london|paris|market|school|college|office|hospital|station|village|city)\\b".toRegex(RegexOption.IGNORE_CASE),
            replacementTransform = { match ->
                val subj = match.groupValues[1]
                val rawDest = match.groupValues[2]
                val dest = if (rawDest.first().isUpperCase()) rawDest else rawDest.replaceFirstChar { it.uppercase() }
                if (dest.equals("market", true)) {
                    "$subj went to the market"
                } else {
                    "$subj went to $dest"
                }
            },
            explanation = "Use the preposition 'to' with 'went' when naming a destination (e.g. 'went to Jaipur').",
            category = EnglishErrorCategory.PREPOSITIONS,
            severity = CorrectionSeverity.IMPORTANT,
            requiresRetry = false
        ),
        Rule(
            ruleId = "prep_listen_me",
            regex = "\\blisten\\s+me\\b".toRegex(RegexOption.IGNORE_CASE),
            replacementTransform = { "listen to me" },
            explanation = "'Listen' requires the preposition 'to' before an object: say 'listen to me'.",
            category = EnglishErrorCategory.PREPOSITIONS,
            severity = CorrectionSeverity.IMPORTANT
        ),
        Rule(
            ruleId = "prep_arrived_on",
            regex = "\\barrived\\s+on\\s+(the\\s+)?(airport|station|hotel|office|school|college|scene|destination)\\b".toRegex(RegexOption.IGNORE_CASE),
            replacementTransform = { match -> "arrived at ${match.groupValues[1]}${match.groupValues[2]}" },
            explanation = "Use 'arrived at' for specific locations like an airport or station.",
            category = EnglishErrorCategory.PREPOSITIONS,
            severity = CorrectionSeverity.IMPORTANT
        ),
        Rule(
            ruleId = "prep_at_morning",
            regex = "\\bat\\s+morning\\b".toRegex(RegexOption.IGNORE_CASE),
            replacementTransform = { "in the morning" },
            explanation = "Say 'in the morning' instead of 'at morning'.",
            category = EnglishErrorCategory.PREPOSITIONS,
            severity = CorrectionSeverity.IMPORTANT
        ),
        Rule(
            ruleId = "prep_on_the_morning",
            regex = "\\b(arrived|came|went|left)\\s+on\\s+the\\s+(morning|evening|afternoon)\\b".toRegex(RegexOption.IGNORE_CASE),
            replacementTransform = { match -> "${match.groupValues[1]} in the ${match.groupValues[2]}" },
            explanation = "Say 'in the morning' or 'in the evening' rather than 'on the morning'.",
            category = EnglishErrorCategory.PREPOSITIONS,
            severity = CorrectionSeverity.IMPORTANT
        ),
        Rule(
            ruleId = "prep_discussed_about",
            regex = "\\b(discuss|discussed|discussing)\\s+about\\b".toRegex(RegexOption.IGNORE_CASE),
            replacementTransform = { match -> match.groupValues[1] },
            explanation = "'Discuss' already means 'talk about', so do not use 'about' after it (e.g., 'discussed this problem').",
            category = EnglishErrorCategory.PREPOSITIONS,
            severity = CorrectionSeverity.IMPORTANT,
            requiresRetry = true
        ),

        // 5. Articles: "a apple" -> "an apple", "an doctor" -> "a doctor"
        Rule(
            ruleId = "art_a_vowel",
            regex = "\\ba\\s+(apple|orange|egg|ice\\s+cream|umbrella|hour)\\b".toRegex(RegexOption.IGNORE_CASE),
            replacementTransform = { match -> "an ${match.groupValues[1]}" },
            explanation = "Use 'an' before words starting with vowel sounds.",
            category = EnglishErrorCategory.ARTICLES,
            severity = CorrectionSeverity.MINOR
        ),
        Rule(
            ruleId = "art_an_consonant",
            regex = "\\ban\\s+(doctor|teacher|car|bike|book|phone|table)\\b".toRegex(RegexOption.IGNORE_CASE),
            replacementTransform = { match -> "a ${match.groupValues[1]}" },
            explanation = "Use 'a' before words starting with consonant sounds.",
            category = EnglishErrorCategory.ARTICLES,
            severity = CorrectionSeverity.MINOR
        ),
        Rule(
            ruleId = "art_missing_car",
            regex = "\\bwant\\s+to\\s+buy\\s+car\\b".toRegex(RegexOption.IGNORE_CASE),
            replacementTransform = { "want to buy a car" },
            explanation = "Countable singular nouns need an article: say 'buy a car'.",
            category = EnglishErrorCategory.ARTICLES,
            severity = CorrectionSeverity.MINOR
        ),

        // 6. Word Order & Question Formation
        Rule(
            ruleId = "word_order_where_you_are",
            regex = "\\bwhere\\s+you\\s+are\\s+going\\b".toRegex(RegexOption.IGNORE_CASE),
            replacementTransform = { match -> if (match.value.first().isUpperCase()) "Where are you going" else "where are you going" },
            explanation = "In direct questions, place the helping verb before the subject: 'Where are you going?'",
            category = EnglishErrorCategory.WORD_ORDER,
            severity = CorrectionSeverity.IMPORTANT,
            requiresRetry = true
        ),
        Rule(
            ruleId = "word_order_what_you_are",
            regex = "\\bwhat\\s+you\\s+are\\s+doing\\b".toRegex(RegexOption.IGNORE_CASE),
            replacementTransform = { match -> if (match.value.first().isUpperCase()) "What are you doing" else "what are you doing" },
            explanation = "In direct questions, invert the subject and verb: 'What are you doing?'",
            category = EnglishErrorCategory.WORD_ORDER,
            severity = CorrectionSeverity.IMPORTANT,
            requiresRetry = true
        ),
        Rule(
            ruleId = "word_order_i_very_like",
            regex = "\\bi\\s+very\\s+like\\b".toRegex(RegexOption.IGNORE_CASE),
            replacementTransform = { "I really like" },
            explanation = "Instead of 'I very like', say 'I really like' or 'I like it very much'.",
            category = EnglishErrorCategory.NATURAL_PHRASING,
            severity = CorrectionSeverity.STYLE
        ),

        // 7. Uncountable Nouns & Plurals
        Rule(
            ruleId = "noun_numeral_plural",
            regex = "\\b(two|three|four|five|six|seven|eight|nine|ten|many|several|a\\s+few)\\s+(brother|sister|friend|child|car|book|room|day|year|month|hour|dog|cat|student|teacher)\\b".toRegex(RegexOption.IGNORE_CASE),
            replacementTransform = { match ->
                val num = match.groupValues[1]
                val noun = match.groupValues[2].lowercase()
                val plural = when (noun) {
                    "child" -> "children"
                    else -> noun + "s"
                }
                "$num $plural"
            },
            explanation = "Use plural nouns after numbers greater than one (e.g., 'two brothers').",
            category = EnglishErrorCategory.SINGULAR_PLURAL,
            severity = CorrectionSeverity.IMPORTANT,
            requiresRetry = true
        ),
        Rule(
            ruleId = "noun_many_information",
            regex = "\\bmany\\s+informations?\\b".toRegex(RegexOption.IGNORE_CASE),
            replacementTransform = { "a lot of information" },
            explanation = "'Information' is an uncountable noun and does not take plural 's': say 'a lot of information'.",
            category = EnglishErrorCategory.SINGULAR_PLURAL,
            severity = CorrectionSeverity.IMPORTANT
        ),
        Rule(
            ruleId = "noun_furnitures",
            regex = "\\bfurnitures\\b".toRegex(RegexOption.IGNORE_CASE),
            replacementTransform = { "furniture" },
            explanation = "'Furniture' is uncountable in English.",
            category = EnglishErrorCategory.SINGULAR_PLURAL,
            severity = CorrectionSeverity.IMPORTANT
        ),
        Rule(
            ruleId = "noun_peoples",
            regex = "\\bmany\\s+peoples\\b".toRegex(RegexOption.IGNORE_CASE),
            replacementTransform = { "many people" },
            explanation = "'People' is already plural: say 'many people'.",
            category = EnglishErrorCategory.SINGULAR_PLURAL,
            severity = CorrectionSeverity.IMPORTANT
        ),
        Rule(
            ruleId = "noun_advices",
            regex = "\\badvices\\b".toRegex(RegexOption.IGNORE_CASE),
            replacementTransform = { "advice" },
            explanation = "'Advice' is uncountable: say 'advice' or 'pieces of advice'.",
            category = EnglishErrorCategory.SINGULAR_PLURAL,
            severity = CorrectionSeverity.IMPORTANT
        ),

        // 8. Spoken Phrasing & Idiomatic Collocations
        Rule(
            ruleId = "phrasing_open_light",
            regex = "\\b(open|close)\\s+the\\s+light\\b".toRegex(RegexOption.IGNORE_CASE),
            replacementTransform = { match ->
                if (match.groupValues[1].equals("open", true)) "turn on the light" else "turn off the light"
            },
            explanation = "For electrical devices, say 'turn on the light' or 'turn off the light'.",
            category = EnglishErrorCategory.NATURAL_PHRASING,
            severity = CorrectionSeverity.STYLE
        ),
        Rule(
            ruleId = "phrasing_bath_of_sun",
            regex = "\\b(took|take)\\s+a\\s+bath\\s+of\\s+sun\\b".toRegex(RegexOption.IGNORE_CASE),
            replacementTransform = { "sunbathed" },
            explanation = "In English, the natural expression is 'sunbathe' or 'soak up the sun'.",
            category = EnglishErrorCategory.NATURAL_PHRASING,
            severity = CorrectionSeverity.STYLE
        ),
        Rule(
            ruleId = "phrasing_passed_out",
            regex = "\\bpassed\\s+out\\s+from\\s+(college|university|school)\\b".toRegex(RegexOption.IGNORE_CASE),
            replacementTransform = { match -> "graduated from ${match.groupValues[1]}" },
            explanation = "Say 'graduated from college' ('passed out' means fainted).",
            category = EnglishErrorCategory.WORD_CHOICE,
            severity = CorrectionSeverity.STYLE
        ),
        Rule(
            ruleId = "phrasing_good_name",
            regex = "\\b(what\\s+is|what's)\\s+your\\s+good\\s+name\\b".toRegex(RegexOption.IGNORE_CASE),
            replacementTransform = { "What is your name" },
            explanation = "In standard English, simply ask 'What is your name?' rather than 'good name'.",
            category = EnglishErrorCategory.NATURAL_PHRASING,
            severity = CorrectionSeverity.STYLE
        )
    )

    /**
     * Analyzes a learner utterance for English accuracy, applies confidence-first filtering,
     * and decides whether and how to coach the learner.
     */
    fun analyzeUtterance(utterance: String): TutorCorrectionDecision {
        val trimmed = utterance.trim()
        if (trimmed.isBlank()) {
            return TutorCorrectionDecision(hasError = false)
        }

        val errors = mutableListOf<EnglishError>()
        var corrected = trimmed

        for (rule in rules) {
            val match = rule.regex.find(corrected)
            if (match != null) {
                val origSnippet = match.value
                val sugSnippet = rule.replacementTransform(match)
                corrected = corrected.replaceRange(match.range, sugSnippet)
                errors.add(
                    EnglishError(
                        originalText = origSnippet,
                        suggestedText = sugSnippet,
                        category = rule.category,
                        severity = rule.severity,
                        explanation = rule.explanation,
                        ruleIdentifier = rule.ruleId
                    )
                )
            }
        }

        if (errors.isEmpty()) {
            return TutorCorrectionDecision(hasError = false)
        }

        // Primary severity is the highest severity detected
        val primarySeverity = errors.maxByOrNull {
            when (it.severity) {
                CorrectionSeverity.CRITICAL -> 4
                CorrectionSeverity.IMPORTANT -> 3
                CorrectionSeverity.MINOR -> 2
                CorrectionSeverity.STYLE -> 1
            }
        }?.severity ?: CorrectionSeverity.MINOR

        // Confidence-first decision: do not interrupt on low-severity minor slips
        val shouldCorrect = primarySeverity == CorrectionSeverity.CRITICAL || primarySeverity == CorrectionSeverity.IMPORTANT
        val timing = if (shouldCorrect) CorrectionTiming.AFTER_UTTERANCE else CorrectionTiming.NO_CORRECTION
        val requiresRetry = errors.any { err -> rules.find { it.ruleId == err.ruleIdentifier }?.requiresRetry == true }

        val primaryError = errors.first()
        val gentleFeedback = if (shouldCorrect) {
            "I understood you perfectly 😊. A more natural way to say that is:\n\n\"$corrected\"\n\nTip: ${primaryError.explanation}"
        } else null

        val retryPrompt = if (requiresRetry) "Now try saying that sentence once more 🎤." else null

        return TutorCorrectionDecision(
            hasError = true,
            detectedErrors = errors,
            primarySeverity = primarySeverity,
            timing = timing,
            gentleFeedback = gentleFeedback,
            correctedSentence = corrected,
            tutorExplanation = primaryError.explanation,
            shouldRequestRetry = requiresRetry,
            retryPrompt = retryPrompt
        )
    }

    /**
     * Evaluates a retry utterance against the original mistake to verify learner improvement.
     */
    fun evaluateRetry(
        originalError: EnglishError?,
        originalUtterance: String,
        retryUtterance: String
    ): RetryEvaluation {
        val trimmedRetry = retryUtterance.trim().lowercase()
        if (originalError == null) {
            return RetryEvaluation(
                originalError = null,
                retryUtterance = retryUtterance,
                isFixed = true,
                isPartiallyFixed = false,
                praiseFeedback = "Great speaking! Keep going 👏.",
                masteryDelta = 1.0f
            )
        }

        val origMistakeWord = originalError.originalText.lowercase().trim()
        val expectedTargetWord = originalError.suggestedText.lowercase().trim()

        val mistakeStillPresent = trimmedRetry.contains(origMistakeWord)
        val targetPresent = trimmedRetry.contains(expectedTargetWord)

        val isFixed = !mistakeStillPresent && targetPresent
        val isPartiallyFixed = !mistakeStillPresent && !targetPresent

        val praise = when {
            isFixed -> "Perfect! 👏 Much smoother."
            isPartiallyFixed -> "Good effort! You're very close 😊."
            else -> "Good try! Remember: say '$expectedTargetWord' instead of '$origMistakeWord'. Let's keep going!"
        }

        val delta = if (isFixed) 2.0f else if (isPartiallyFixed) 1.0f else 0.0f

        return RetryEvaluation(
            originalError = originalError,
            retryUtterance = retryUtterance,
            isFixed = isFixed,
            isPartiallyFixed = isPartiallyFixed,
            praiseFeedback = praise,
            masteryDelta = delta
        )
    }
}
package com.vaniflow.app.engine.learning

import com.vaniflow.app.domain.model.Correction
import com.vaniflow.app.domain.model.CorrectionCategory
import com.vaniflow.app.domain.model.FeedbackImportance
import javax.inject.Inject
import javax.inject.Singleton

/**
 * High-precision, deterministic, rule-based Grammar, Tense, and Phrasing Engine.
 *
 * Specializes in common Indian English transfer patterns, tense consistency,
 * irregular past verbs, preposition usage, and natural spoken idioms.
 */
@Singleton
class GrammarEngine @Inject constructor() {

    data class GrammarRule(
        val regex: Regex,
        val replacementTransform: (MatchResult) -> String,
        val explanation: String,
        val category: CorrectionCategory = CorrectionCategory.GRAMMAR,
        val importance: FeedbackImportance = FeedbackImportance.MEDIUM
    )

    private val rules: List<GrammarRule> = listOf(
        // 1. Common irregular past tense errors: "buyed" -> "bought", "teached" -> "taught", "bringed" -> "brought", "catched" -> "caught"
        GrammarRule(
            regex = "\\bbuyed\\b".toRegex(RegexOption.IGNORE_CASE),
            replacementTransform = { "bought" },
            explanation = "The past tense of 'buy' is irregular: use 'bought' instead of 'buyed'.",
            category = CorrectionCategory.GRAMMAR,
            importance = FeedbackImportance.HIGH
        ),
        GrammarRule(
            regex = "\\bteached\\b".toRegex(RegexOption.IGNORE_CASE),
            replacementTransform = { "taught" },
            explanation = "The past tense of 'teach' is 'taught'.",
            category = CorrectionCategory.GRAMMAR,
            importance = FeedbackImportance.HIGH
        ),
        GrammarRule(
            regex = "\\bbringed\\b".toRegex(RegexOption.IGNORE_CASE),
            replacementTransform = { "brought" },
            explanation = "The past tense of 'bring' is 'brought'.",
            category = CorrectionCategory.GRAMMAR,
            importance = FeedbackImportance.HIGH
        ),
        GrammarRule(
            regex = "\\bcatched\\b".toRegex(RegexOption.IGNORE_CASE),
            replacementTransform = { "caught" },
            explanation = "The past tense of 'catch' is 'caught'.",
            category = CorrectionCategory.GRAMMAR,
            importance = FeedbackImportance.HIGH
        ),

        // 2. Past time marker with present tense: "yesterday I go" -> "yesterday I went"
        GrammarRule(
            regex = "\\b(yesterday|last\\s+(?:night|week|month|year))\\s+(i|we|they|he|she)\\s+go\\b".toRegex(RegexOption.IGNORE_CASE),
            replacementTransform = { match -> "${match.groupValues[1]} ${match.groupValues[2]} went" },
            explanation = "When describing completed actions in the past (like 'yesterday'), use the past tense verb 'went'.",
            category = CorrectionCategory.GRAMMAR,
            importance = FeedbackImportance.HIGH
        ),
        GrammarRule(
            regex = "\\b(i|we|they|he|she)\\s+go\\s+(yesterday|last\\s+(?:night|week|month|year))\\b".toRegex(RegexOption.IGNORE_CASE),
            replacementTransform = { match -> "${match.groupValues[1]} went ${match.groupValues[2]}" },
            explanation = "Use past tense 'went' when talking about past events.",
            category = CorrectionCategory.GRAMMAR,
            importance = FeedbackImportance.HIGH
        ),

        // 3. Duration prepositions: "since X years/months/days" -> "for X years/months/days"
        GrammarRule(
            regex = "\\bsince\\s+(\\d+|one|two|three|four|five|six|seven|eight|nine|ten|a\\s+few|several)\\s+(years?|months?|days?|weeks?|hours?)\\b".toRegex(RegexOption.IGNORE_CASE),
            replacementTransform = { match -> "for ${match.groupValues[1]} ${match.groupValues[2]}" },
            explanation = "Use 'for' when referring to a duration of time (e.g., 'for 3 years'), and 'since' for a specific starting point in time (e.g., 'since 2021').",
            category = CorrectionCategory.GRAMMAR,
            importance = FeedbackImportance.HIGH
        ),

        // 4. Stative continuous verbs: "am having / is having / are having" -> "have / has"
        GrammarRule(
            regex = "\\b(i|we|they|you)\\s+am\\s+having\\b".toRegex(RegexOption.IGNORE_CASE),
            replacementTransform = { match -> "${match.groupValues[1]} have" },
            explanation = "'Have' expresses possession as a stative verb and is typically used in the simple present ('I have') rather than continuous ('I am having').",
            category = CorrectionCategory.NATURAL_PHRASING,
            importance = FeedbackImportance.MEDIUM
        ),
        GrammarRule(
            regex = "\\b(he|she|it)\\s+is\\s+having\\b".toRegex(RegexOption.IGNORE_CASE),
            replacementTransform = { match -> "${match.groupValues[1]} has" },
            explanation = "'Have' expresses possession as a stative verb ('he has' / 'she has').",
            category = CorrectionCategory.NATURAL_PHRASING,
            importance = FeedbackImportance.MEDIUM
        ),

        // 5. Stative verb: "am knowing / is knowing" -> "know / knows"
        GrammarRule(
            regex = "\\b(i|we|they|you)\\s+(?:am|are)\\s+knowing\\b".toRegex(RegexOption.IGNORE_CASE),
            replacementTransform = { match -> "${match.groupValues[1]} know" },
            explanation = "'Know' is a state of mind, so use 'I know' instead of continuous 'I am knowing'.",
            category = CorrectionCategory.GRAMMAR,
            importance = FeedbackImportance.HIGH
        ),
        GrammarRule(
            regex = "\\b(he|she)\\s+is\\s+knowing\\b".toRegex(RegexOption.IGNORE_CASE),
            replacementTransform = { match -> "${match.groupValues[1]} knows" },
            explanation = "'Know' is a stative verb ('she knows' instead of 'she is knowing').",
            category = CorrectionCategory.GRAMMAR,
            importance = FeedbackImportance.HIGH
        ),

        // 6. Stative verb: "am understanding / is understanding" -> "understand / understands"
        GrammarRule(
            regex = "\\b(i|we|they|you)\\s+(?:am|are)\\s+understanding\\b".toRegex(RegexOption.IGNORE_CASE),
            replacementTransform = { match -> "${match.groupValues[1]} understand" },
            explanation = "Use 'I understand' instead of continuous 'I am understanding'.",
            category = CorrectionCategory.NATURAL_PHRASING,
            importance = FeedbackImportance.MEDIUM
        ),

        // 7. Redundant preposition: "discuss about" -> "discuss"
        GrammarRule(
            regex = "\\bdiscuss(?:ed|ing)?\\s+about\\b".toRegex(RegexOption.IGNORE_CASE),
            replacementTransform = { match ->
                when {
                    match.value.startsWith("discussed", ignoreCase = true) -> "discussed"
                    match.value.startsWith("discussing", ignoreCase = true) -> "discussing"
                    else -> "discuss"
                }
            },
            explanation = "'Discuss' is a transitive verb that takes a direct object without the preposition 'about'.",
            category = CorrectionCategory.NATURAL_PHRASING,
            importance = FeedbackImportance.MEDIUM
        ),

        // 8. Redundant preposition: "order for" -> "order"
        GrammarRule(
            regex = "\\border(?:ed|ing)?\\s+for\\s+(a|an|the|one|two|some|hot|cold|my)\\b".toRegex(RegexOption.IGNORE_CASE),
            replacementTransform = { match -> "order ${match.groupValues[1]}" },
            explanation = "'Order' takes a direct object (e.g., 'order a coffee' instead of 'order for a coffee').",
            category = CorrectionCategory.NATURAL_PHRASING,
            importance = FeedbackImportance.MEDIUM
        ),

        // 9. Redundant word: "revert back" -> "reply"
        GrammarRule(
            regex = "\\brevert\\s+back\\b".toRegex(RegexOption.IGNORE_CASE),
            replacementTransform = { "reply" },
            explanation = "'Revert' already implies returning; use 'reply' or 'get back to you' in conversational English.",
            category = CorrectionCategory.NATURAL_PHRASING,
            importance = FeedbackImportance.LOW
        ),

        // 10. Redundant word: "return back" -> "return"
        GrammarRule(
            regex = "\\breturn\\s+back\\b".toRegex(RegexOption.IGNORE_CASE),
            replacementTransform = { "return" },
            explanation = "'Return' already means to go or come back, so 'back' is redundant.",
            category = CorrectionCategory.NATURAL_PHRASING,
            importance = FeedbackImportance.LOW
        ),

        // 11. Past tense auxiliary mistake: "did not went / didn't went" -> "did not go"
        GrammarRule(
            regex = "\\b(did\\s+not|didn't)\\s+went\\b".toRegex(RegexOption.IGNORE_CASE),
            replacementTransform = { match -> "${match.groupValues[1]} go" },
            explanation = "After the auxiliary verb 'did/didn't', always use the base form of the verb ('go', not 'went').",
            category = CorrectionCategory.GRAMMAR,
            importance = FeedbackImportance.HIGH
        ),
        GrammarRule(
            regex = "\\b(did\\s+not|didn't)\\s+saw\\b".toRegex(RegexOption.IGNORE_CASE),
            replacementTransform = { match -> "${match.groupValues[1]} see" },
            explanation = "After 'did not', use the base verb 'see' rather than the past tense 'saw'.",
            category = CorrectionCategory.GRAMMAR,
            importance = FeedbackImportance.HIGH
        ),

        // 12. Subject-verb agreement: "one of my friend" -> "one of my friends"
        GrammarRule(
            regex = "\\bone\\s+of\\s+my\\s+([a-zA-Z]+)(?<!s)\\b".toRegex(RegexOption.IGNORE_CASE),
            replacementTransform = { match -> "one of my ${match.groupValues[1]}s" },
            explanation = "The phrase 'one of my...' is followed by a plural noun (e.g., 'one of my friends').",
            category = CorrectionCategory.GRAMMAR,
            importance = FeedbackImportance.MEDIUM
        ),

        // 13. Subject-verb agreement: "He don't / She don't" -> "He doesn't / She doesn't"
        GrammarRule(
            regex = "\\b(he|she|it|everyone)\\s+don't\\b".toRegex(RegexOption.IGNORE_CASE),
            replacementTransform = { match -> "${match.groupValues[1]} doesn't" },
            explanation = "Third-person singular subjects take 'doesn't' (e.g., 'He doesn't know').",
            category = CorrectionCategory.GRAMMAR,
            importance = FeedbackImportance.HIGH
        ),

        // 14. Preposition with transport: "in bus / in train" -> "on the bus / on the train"
        GrammarRule(
            regex = "\\b(in|inside)\\s+(?:the\\s+)?(bus|train|flight|plane)\\b".toRegex(RegexOption.IGNORE_CASE),
            replacementTransform = { match -> "on the ${match.groupValues[2]}" },
            explanation = "For public transport (bus, train, plane), use 'on the bus' rather than 'in bus'.",
            category = CorrectionCategory.NATURAL_PHRASING,
            importance = FeedbackImportance.MEDIUM
        )
    )

    /**
     * Analyzes utterance and returns the most prominent correction if applicable.
     */
    fun analyze(text: String): Correction? {
        if (text.isBlank()) return null

        for (rule in rules) {
            val match = rule.regex.find(text)
            if (match != null) {
                var replacement = rule.replacementTransform(match)
                if (match.value.firstOrNull()?.isUpperCase() == true) {
                    replacement = replacement.replaceFirstChar { it.uppercaseChar() }
                }
                val correctedText = text.replaceRange(match.range, replacement)

                return Correction(
                    originalText = text.trim(),
                    suggestedText = correctedText.trim(),
                    explanation = rule.explanation,
                    category = rule.category,
                    importance = rule.importance
                )
            }
        }

        return null
    }
}

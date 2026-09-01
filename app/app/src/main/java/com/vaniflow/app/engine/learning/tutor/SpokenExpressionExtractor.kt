package com.vaniflow.app.engine.learning.tutor

import com.vaniflow.app.engine.learning.tutor.model.VocabularyMemory
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpokenExpressionExtractor @Inject constructor() {

    data class ExpressionTemplate(
        val phrase: String,
        val partOfSpeech: String,
        val meaning: String,
        val example: String
    )

    private val commonExpressions = listOf(
        ExpressionTemplate("looking forward to", "idiom/expression", "Awaiting something with pleasure or excitement", "I am looking forward to our next meeting."),
        ExpressionTemplate("by the way", "discourse marker", "Used to introduce a new topic or additional information", "By the way, did you finish that report?"),
        ExpressionTemplate("on the other hand", "connector", "Used to present contrasting points or alternative perspectives", "It's expensive, but on the other hand, the quality is top-notch."),
        ExpressionTemplate("in my opinion", "opinion phrase", "Used to politely state one's personal view or thought", "In my opinion, teamwork leads to better results."),
        ExpressionTemplate("to be honest", "conversational phrase", "Used to speak candidly and genuinely", "To be honest, I really enjoyed our conversation."),
        ExpressionTemplate("as soon as possible", "adverbial phrase", "At the earliest possible moment", "Please let me know as soon as possible."),
        ExpressionTemplate("make sure to", "collocation", "Ensure or double check that an action is taken", "Make sure to get plenty of rest before tomorrow."),
        ExpressionTemplate("keep in mind", "idiom", "Remember or consider a fact when deciding something", "Keep in mind that practice makes progress."),
        ExpressionTemplate("get used to", "verb phrase", "Become accustomed to a new situation or routine", "It took some time to get used to the new schedule."),
        ExpressionTemplate("figure out", "phrasal verb", "Understand or solve a problem through thinking", "Let's figure out the best way to handle this."),
        ExpressionTemplate("come up with", "phrasal verb", "Produce, suggest, or discover an idea", "She came up with a great solution."),
        ExpressionTemplate("touch base", "business idiom", "Briefly contact someone to check in or share updates", "Let's touch base again tomorrow morning."),
        ExpressionTemplate("catch up with", "phrasal verb", "Talk to someone to learn what they have been doing", "It was wonderful to catch up with you today."),
        ExpressionTemplate("wrap up", "phrasal verb", "Conclude or finish an activity smoothly", "Let's wrap up our discussion for today.")
    )

    /**
     * Finds useful spoken expressions present in a conversation turn.
     */
    fun extractExpressions(text: String, scenarioId: String? = null): List<VocabularyMemory> {
        val lower = text.lowercase()
        val results = mutableListOf<VocabularyMemory>()

        for (tmpl in commonExpressions) {
            if (lower.contains(tmpl.phrase)) {
                results.add(
                    VocabularyMemory(
                        wordOrPhrase = tmpl.phrase,
                        phonetic = "",
                        partOfSpeech = tmpl.partOfSpeech,
                        meaning = tmpl.meaning,
                        exampleSentence = tmpl.example,
                        familiarityScore = 30,
                        usageCount = 1,
                        sourceScenarioId = scenarioId
                    )
                )
            }
        }
        return results
    }
}

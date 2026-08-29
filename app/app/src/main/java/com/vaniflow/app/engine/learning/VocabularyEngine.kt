package com.vaniflow.app.engine.learning

import com.vaniflow.app.data.local.db.dao.SavedVocabularyDao
import com.vaniflow.app.data.local.db.entity.SavedVocabularyEntity
import com.vaniflow.app.domain.model.SkillLevel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

data class VocabularyWord(
    val word: String,
    val phonetic: String,
    val partOfSpeech: String,
    val meaning: String,
    val example: String,
    val cefrLevel: String = "B2"
)

/**
 * Vocabulary Engine managing word discovery, CEFR level tagging, and saved items.
 */
@Singleton
class VocabularyEngine @Inject constructor(
    private val savedVocabularyDao: SavedVocabularyDao? = null
) {

    val curatedDictionary: Map<String, VocabularyWord> = mapOf(
        "confident" to VocabularyWord(
            word = "Confident",
            phonetic = "/ˈkɒnfɪdənt/",
            partOfSpeech = "Adjective",
            meaning = "Feeling or showing certainty about something or self-assurance in one's abilities.",
            example = "She felt confident during the interview and answered every technical question clearly.",
            cefrLevel = "B1"
        ),
        "spearhead" to VocabularyWord(
            word = "Spearhead",
            phonetic = "/ˈspɪəhɛd/",
            partOfSpeech = "Verb",
            meaning = "To lead an organized activity, campaign, or technical initiative.",
            example = "He spearheaded the transition to Kotlin Coroutines across all core services.",
            cefrLevel = "C1"
        ),
        "articulate" to VocabularyWord(
            word = "Articulate",
            phonetic = "/ɑːˈtɪkjʊlət/",
            partOfSpeech = "Adjective",
            meaning = "Having or showing the ability to speak fluently and coherently.",
            example = "An articulate explanation helps cross-functional teams align faster.",
            cefrLevel = "B2"
        ),
        "blocker" to VocabularyWord(
            word = "Blocker",
            phonetic = "/ˈblɒkə/",
            partOfSpeech = "Noun",
            meaning = "An obstacle or dependency that prevents a task or sprint item from progressing.",
            example = "We identified the database migration bottleneck as the primary blocker.",
            cefrLevel = "B2"
        ),
        "cappuccino" to VocabularyWord(
            word = "Cappuccino",
            phonetic = "/ˌkæpʊˈtʃiːnəʊ/",
            partOfSpeech = "Noun",
            meaning = "An Italian coffee drink prepared with equal parts espresso, steamed milk, and milk foam.",
            example = "Could you please make my cappuccino with oat milk and extra foam?",
            cefrLevel = "A2"
        )
    )

    fun getWordDetails(word: String): VocabularyWord? {
        return curatedDictionary[word.lowercase().trim()]
    }

    fun extractVocabulary(text: String): List<VocabularyWord> {
        val words = text.lowercase().split("\\W+".toRegex())
        return words.mapNotNull { curatedDictionary[it] }.distinctBy { it.word }
    }

    suspend fun saveWord(word: VocabularyWord) {
        savedVocabularyDao?.insertVocabulary(
            SavedVocabularyEntity(
                word = word.word,
                phonetic = word.phonetic,
                partOfSpeech = word.partOfSpeech,
                meaning = word.meaning,
                example = word.example
            )
        )
    }

    suspend fun deleteWord(word: String) {
        savedVocabularyDao?.deleteVocabulary(word)
    }

    fun getAllSaved(): Flow<List<SavedVocabularyEntity>>? {
        return savedVocabularyDao?.getAllVocabulary()
    }
}

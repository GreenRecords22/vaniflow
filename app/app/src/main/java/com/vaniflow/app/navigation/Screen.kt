package com.vaniflow.app.navigation

/**
 * All navigation routes in VaniFlow.
 */
sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object Home : Screen("home")
    data object CharacterSelection : Screen("character_selection")
    data object ScenarioSelection : Screen("scenario_selection/{characterId}") {
        fun createRoute(characterId: String) = "scenario_selection/$characterId"
    }
    data object Conversation : Screen("conversation/{characterId}/{scenarioId}") {
        fun createRoute(characterId: String, scenarioId: String) =
            "conversation/$characterId/$scenarioId"
    }
    data object SessionSummary : Screen("session_summary/{sessionId}") {
        fun createRoute(sessionId: String) = "session_summary/$sessionId"
    }
    data object Progress : Screen("progress")
    data object Profile : Screen("profile")
    data object VocabularyDetail : Screen("vocabulary_detail/{wordId}") {
        fun createRoute(wordId: String) = "vocabulary_detail/$wordId"
    }
}

package com.vaniflow.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.vaniflow.app.feature.character.CharacterSelectionScreen
import com.vaniflow.app.feature.conversation.ConversationScreen
import com.vaniflow.app.feature.home.HomeScreen
import com.vaniflow.app.feature.profile.ProfileScreen
import com.vaniflow.app.feature.progress.ProgressScreen
import com.vaniflow.app.feature.scenario.ScenarioSelectionScreen
import com.vaniflow.app.feature.summary.SessionSummaryScreen
import com.vaniflow.app.feature.vocabulary.VocabularyDetailScreen

@Composable
fun VaniFlowNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier,
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onStartSpeaking = { characterId ->
                    navController.navigate(
                        Screen.ScenarioSelection.createRoute(characterId)
                    )
                },
                onScenarioClick = { characterId, scenarioId ->
                    navController.navigate(
                        Screen.Conversation.createRoute(characterId, scenarioId)
                    )
                },
            )
        }

        composable(Screen.CharacterSelection.route) {
            CharacterSelectionScreen(
                onCharacterSelected = { characterId ->
                    navController.navigate(
                        Screen.ScenarioSelection.createRoute(characterId)
                    )
                },
            )
        }

        composable(
            route = Screen.ScenarioSelection.route,
            arguments = listOf(
                navArgument("characterId") { type = NavType.StringType }
            ),
        ) { backStackEntry ->
            val characterId = backStackEntry.arguments?.getString("characterId") ?: return@composable
            ScenarioSelectionScreen(
                characterId = characterId,
                onScenarioSelected = { scenarioId ->
                    navController.navigate(
                        Screen.Conversation.createRoute(characterId, scenarioId)
                    )
                },
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Screen.Conversation.route,
            arguments = listOf(
                navArgument("characterId") { type = NavType.StringType },
                navArgument("scenarioId") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val characterId = backStackEntry.arguments?.getString("characterId") ?: return@composable
            val scenarioId = backStackEntry.arguments?.getString("scenarioId") ?: return@composable
            ConversationScreen(
                characterId = characterId,
                scenarioId = scenarioId,
                onSessionComplete = { sessionId ->
                    navController.navigate(Screen.SessionSummary.createRoute(sessionId)) {
                        popUpTo(Screen.Home.route)
                    }
                },
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Screen.SessionSummary.route,
            arguments = listOf(
                navArgument("sessionId") { type = NavType.StringType }
            ),
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: return@composable
            SessionSummaryScreen(
                sessionId = sessionId,
                onPracticeAgain = { navController.popBackStack(Screen.Home.route, false) },
                onBackToHome = { navController.popBackStack(Screen.Home.route, false) },
            )
        }

        composable(Screen.Progress.route) {
            ProgressScreen()
        }

        composable(Screen.Profile.route) {
            ProfileScreen()
        }

        composable(
            route = Screen.VocabularyDetail.route,
            arguments = listOf(
                navArgument("wordId") { type = NavType.StringType }
            ),
        ) { backStackEntry ->
            val wordId = backStackEntry.arguments?.getString("wordId") ?: return@composable
            VocabularyDetailScreen(
                wordId = wordId,
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}

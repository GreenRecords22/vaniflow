package com.vaniflow.app.feature.scenario

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vaniflow.app.ui.theme.VaniFlowTheme

/**
 * Difficulty level for speaking practice scenarios.
 */
enum class Difficulty(val label: String) {
    BEGINNER("Beginner"),
    INTERMEDIATE("Intermediate"),
    ADVANCED("Advanced"),
}

/**
 * Data model for a scenario item.
 */
data class Scenario(
    val id: String,
    val title: String,
    val description: String,
    val durationMinutes: Int,
    val category: String,
    val difficulty: Difficulty,
    val icon: ImageVector,
)

/**
 * Mock scenario data matching Stitch practice_scenarios_final design.
 */
private val MockScenarios = listOf(
    Scenario(
        id = "free_conversation",
        title = "🌟 Free Flow / Open Talk",
        description = "Speak freely about any topic without any script — share thoughts, discuss hobbies, or ask questions.",
        durationMinutes = 5,
        category = "Free Flow",
        difficulty = Difficulty.BEGINNER,
        icon = Icons.Filled.RecordVoiceOver,
    ),
    Scenario(
        id = "order_coffee",
        title = "Order Coffee",
        description = "Practice ordering your favorite drink and making small talk with the barista.",
        durationMinutes = 3,
        category = "Daily Life",
        difficulty = Difficulty.BEGINNER,
        icon = Icons.Filled.LocalCafe,
    ),
    Scenario(
        id = "airport_checkin",
        title = "Airport Check-in",
        description = "Navigate the check-in counter, baggage drop, and common questions.",
        durationMinutes = 5,
        category = "Travel",
        difficulty = Difficulty.INTERMEDIATE,
        icon = Icons.Filled.FlightTakeoff,
    ),
    Scenario(
        id = "job_interview",
        title = "Job Interview",
        description = "Answer common interview questions and discuss your professional experience.",
        durationMinutes = 10,
        category = "Interview",
        difficulty = Difficulty.ADVANCED,
        icon = Icons.Filled.Work,
    ),
    Scenario(
        id = "project_standup",
        title = "Project Standup",
        description = "Give a clear update on your tasks and discuss blockers with the team.",
        durationMinutes = 5,
        category = "Work",
        difficulty = Difficulty.INTERMEDIATE,
        icon = Icons.Filled.Groups,
    ),
    Scenario(
        id = "meeting_someone",
        title = "Meeting Someone",
        description = "Introduce yourself, ask questions, and make a friendly first impression.",
        durationMinutes = 3,
        category = "Daily Life",
        difficulty = Difficulty.BEGINNER,
        icon = Icons.Filled.Person,
    ),
    Scenario(
        id = "workplace_discussion",
        title = "Workplace Discussion",
        description = "Express your opinions clearly and collaborate effectively with colleagues.",
        durationMinutes = 7,
        category = "Work",
        difficulty = Difficulty.INTERMEDIATE,
        icon = Icons.Filled.Business,
    ),
)

private val FilterCategories = listOf(
    "All",
    "Free Flow",
    "Daily Life",
    "Travel",
    "Interview",
    "Work",
)

/**
 * Scenario Selection Screen composable.
 *
 * Allows the learner to select a real-world scenario to practice their speaking skills.
 */
@Composable
fun ScenarioSelectionScreen(
    characterId: String,
    onScenarioSelected: (scenarioId: String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedCategory by rememberSaveable { mutableStateOf("All") }

    val filteredScenarios = remember(selectedCategory) {
        if (selectedCategory == "All") {
            MockScenarios
        } else {
            MockScenarios.filter { it.category.equals(selectedCategory, ignoreCase = true) }
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            // Top App Bar
            item {
                ScenarioTopAppBar(
                    onNavigateBack = onNavigateBack,
                    onSettingsClick = { /* Settings action */ },
                )
            }

            // Heading Section
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = "Select Scenario",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Choose a real-world situation to practice your speaking skills.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Filter Chips
            item {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(FilterCategories) { category ->
                        val isSelected = category == selectedCategory
                        FilterChipPill(
                            label = category,
                            isSelected = isSelected,
                            onClick = { selectedCategory = category },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Scenario Cards List
            items(
                items = filteredScenarios,
                key = { it.id },
            ) { scenario ->
                ScenarioCard(
                    scenario = scenario,
                    onClick = { onScenarioSelected(scenario.id) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/**
 * Top App Bar matching Stitch practice_scenarios_final design.
 */
@Composable
private fun ScenarioTopAppBar(
    onNavigateBack: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Navigate back",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }

            // VaniFlow Avatar & Brand
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.RecordVoiceOver,
                    contentDescription = "VaniFlow Avatar",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }

            Text(
                text = "VaniFlow",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = "Settings",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * Filter chip pill with smooth color transition.
 */
@Composable
private fun FilterChipPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        animationSpec = tween(durationMillis = 200),
        label = "chip_bg_color",
    )

    val textColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(durationMillis = 200),
        label = "chip_text_color",
    )

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(9999.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(9999.dp),
        color = backgroundColor,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            color = textColor,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
    }
}

/**
 * Scenario card component with 24dp rounded corners, icon container, difficulty badge, and metadata.
 */
@Composable
private fun ScenarioCard(
    scenario: Scenario,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp,
            pressedElevation = 3.dp,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            // Top Row: Icon + Difficulty Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                // Scenario icon in tinted rounded square
                val iconBgColor = when (scenario.difficulty) {
                    Difficulty.BEGINNER -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                    Difficulty.INTERMEDIATE -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)
                    Difficulty.ADVANCED -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                }

                val iconTintColor = when (scenario.difficulty) {
                    Difficulty.BEGINNER -> MaterialTheme.colorScheme.secondary
                    Difficulty.INTERMEDIATE -> MaterialTheme.colorScheme.tertiary
                    Difficulty.ADVANCED -> MaterialTheme.colorScheme.primary
                }

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(iconBgColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = scenario.icon,
                        contentDescription = "${scenario.title} icon",
                        tint = iconTintColor,
                        modifier = Modifier.size(26.dp),
                    )
                }

                // Difficulty Badge
                DifficultyBadge(difficulty = scenario.difficulty)
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Title
            Text(
                text = scenario.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Description
            Text(
                text = scenario.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Bottom Meta Info: Duration & Category
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Schedule,
                    contentDescription = "Duration",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${scenario.durationMinutes} min",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
                Text(
                    text = "  •  ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                Text(
                    text = scenario.category,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
            }
        }
    }
}

/**
 * Difficulty badge chip.
 * Beginner: primary/surface-variant chip
 * Intermediate: secondary chip
 * Advanced: tertiary / error container outline chip
 */
@Composable
private fun DifficultyBadge(
    difficulty: Difficulty,
    modifier: Modifier = Modifier,
) {
    val (badgeBgColor, badgeTextColor) = when (difficulty) {
        Difficulty.BEGINNER -> Pair(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Difficulty.INTERMEDIATE -> Pair(
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
            MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Difficulty.ADVANCED -> Pair(
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
            MaterialTheme.colorScheme.onErrorContainer,
        )
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = badgeBgColor,
    ) {
        Text(
            text = difficulty.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = badgeTextColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ScenarioSelectionScreenPreview() {
    VaniFlowTheme {
        ScenarioSelectionScreen(
            characterId = "elena",
            onScenarioSelected = {},
            onNavigateBack = {},
        )
    }
}

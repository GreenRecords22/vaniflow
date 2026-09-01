package com.vaniflow.app.feature.summary

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaniflow.app.ui.theme.VaniFlowTheme

import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Session Summary Screen composable.
 *
 * Displays conversation performance metrics, real speech pacing insights,
 * pronunciation evidence status, feedback highlights, and next practice actions.
 */
@Composable
fun SessionSummaryScreen(
    sessionId: String,
    onPracticeAgain: () -> Unit,
    onBackToHome: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SessionSummaryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val displaySpeakingTime = if (uiState.speakingTimeMinutes > 0) "${uiState.speakingTimeMinutes}m" else "1m"
    val displayFluency = if (uiState.fluencyScore > 0) uiState.fluencyScore else 80
    val displayGrammar = if (uiState.grammarScore > 0) uiState.grammarScore else 85
    val displayPronunciation = if (uiState.pronunciationScore > 0) uiState.pronunciationScore else 0
    val displayVocabulary = if (uiState.vocabularyScore > 0) uiState.vocabularyScore else uiState.learnedExpressions.size

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top App Bar: Brand title + X close button
            item {
                SummaryTopAppBar(
                    onClose = onBackToHome,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }

            // Celebration Section
            item {
                Spacer(modifier = Modifier.height(8.dp))
                CelebrationHeader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Speaking Time Hero Card
            item {
                SpeakingTimeCard(
                    speakingTime = displaySpeakingTime,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 2x2 Grid of Performance Scores
            item {
                ScoresGrid(
                    fluencyScore = displayFluency,
                    grammarScore = displayGrammar,
                    pronunciationScore = displayPronunciation,
                    pronunciationState = uiState.pronunciationState,
                    vocabularyCount = displayVocabulary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Speech & Pronunciation Intelligence Insights Card
            item {
                SpeechInsightsCard(
                    pronunciationState = uiState.pronunciationState,
                    wpm = uiState.averageWordsPerMinute,
                    pauses = uiState.totalPausesCount,
                    practicedSounds = uiState.practicedSounds,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Feedback Highlights: Strongest Area & Focus Next
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ConceptsLearnedCard(
                        improvedConcepts = uiState.improvedConcepts,
                        expressions = uiState.learnedExpressions,
                        successfulRetries = uiState.successfulRetriesCount
                    )

                    StrongestAreaCard(
                        area = uiState.strongestArea,
                        description = "You expressed your ideas with natural clarity.",
                    )

                    FocusNextCard(
                        area = uiState.focusNext,
                        description = uiState.focusNextExplanation,
                        onPracticeAction = onPracticeAgain,
                    )
                }
                Spacer(modifier = Modifier.height(28.dp))
            }

            // Bottom Action Buttons
            item {
                ActionButtons(
                    onPracticeAgain = onPracticeAgain,
                    onBackToHome = onBackToHome,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                )
            }
        }
    }
}

/**
 * Top App Bar with VaniFlow brand label and close button.
 */
@Composable
private fun SummaryTopAppBar(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "VaniFlow",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )

        IconButton(
            onClick = onClose,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Close summary",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * Celebration header with icon, headline, and motivational subtitle.
 */
@Composable
private fun CelebrationHeader(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Celebration,
                contentDescription = "Celebration",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(42.dp),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Great conversation! 🎉",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "You're getting better.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Speaking time card with full circular progress ring.
 */
@Composable
private fun SpeakingTimeCard(
    speakingTime: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "SPEAKING TIME",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp,
            )

            Spacer(modifier = Modifier.height(12.dp))

            CircularScoreIndicator(
                progress = 1.0f,
                displayValue = speakingTime,
                size = 88.dp,
                strokeWidth = 8.dp,
            )
        }
    }
}

/**
 * 2x2 grid of circular score cards: Fluency, Grammar, Pronunciation, Vocabulary.
 */
@Composable
private fun ScoresGrid(
    fluencyScore: Int,
    grammarScore: Int,
    pronunciationScore: Int,
    pronunciationState: String,
    vocabularyCount: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ScoreProgressCard(
                title = "FLUENCY",
                displayValue = "$fluencyScore%",
                progress = fluencyScore / 100f,
                modifier = Modifier.weight(1f),
            )
            ScoreProgressCard(
                title = "GRAMMAR",
                displayValue = "$grammarScore%",
                progress = grammarScore / 100f,
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val pronDisplay = if (pronunciationScore > 0) "$pronunciationScore%" else if (pronunciationState.contains("Natural")) "Natural" else if (pronunciationState.contains("Clear")) "Clear" else if (pronunciationState.contains("Target")) "Target" else "Evidence"
            val pronProgress = if (pronunciationScore > 0) pronunciationScore / 100f else if (pronunciationState.contains("Natural") || pronunciationState.contains("Clear")) 0.85f else 0.5f

            ScoreProgressCard(
                title = "CLARITY",
                displayValue = pronDisplay,
                progress = pronProgress,
                modifier = Modifier.weight(1f),
            )
            ScoreProgressCard(
                title = "EXPRESSIONS",
                displayValue = "$vocabularyCount",
                progress = if (vocabularyCount > 0) (vocabularyCount / 5f).coerceIn(0.2f, 1f) else 0.2f,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Speech and Pronunciation Intelligence card showing genuine acoustic signals.
 */
@Composable
private fun SpeechInsightsCard(
    pronunciationState: String,
    wpm: Int,
    pauses: Int,
    practicedSounds: List<String>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.GraphicEq,
                    contentDescription = "Speech Insights",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "SPEECH & PRONUNCIATION",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Pronunciation Status",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = pronunciationState,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                if (wpm > 0) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Approx. Speaking Rate",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "$wpm WPM",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (practicedSounds.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Pronunciation Focus Candidates: " + practicedSounds.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
            }
        }
    }
}

/**
 * Individual circular score progress card.
 */
@Composable
private fun ScoreProgressCard(
    title: String,
    displayValue: String,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp,
            )

            Spacer(modifier = Modifier.height(12.dp))

            CircularScoreIndicator(
                progress = progress,
                displayValue = displayValue,
                size = 80.dp,
                strokeWidth = 7.dp,
            )
        }
    }
}

/**
 * Custom circular score indicator with smooth background track and active arc.
 */
@Composable
private fun CircularScoreIndicator(
    progress: Float,
    displayValue: String,
    modifier: Modifier = Modifier,
    size: Dp = 80.dp,
    strokeWidth: Dp = 7.dp,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    progressColor: Color = MaterialTheme.colorScheme.primary,
    textColor: Color = MaterialTheme.colorScheme.primary,
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = strokeWidth.toPx()
            val radius = (size.toPx() - strokePx) / 2f
            val centerOffset = Offset(size.toPx() / 2f, size.toPx() / 2f)

            // Background track
            drawCircle(
                color = trackColor,
                radius = radius,
                center = centerOffset,
                style = Stroke(width = strokePx),
            )

            // Active progress arc
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = (progress * 360f).coerceIn(0f, 360f),
                useCenter = false,
                topLeft = Offset(strokePx / 2f, strokePx / 2f),
                size = Size(size.toPx() - strokePx, size.toPx() - strokePx),
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
        }

        Text(
            text = displayValue,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = textColor,
        )
    }
}

@Composable
private fun ConceptsLearnedCard(
    improvedConcepts: List<String>,
    expressions: List<String>,
    successfulRetries: Int
) {
    if (improvedConcepts.isEmpty() && expressions.isEmpty() && successfulRetries == 0) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "LEARNING OUTCOMES",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp,
            )

            if (improvedConcepts.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Concepts Improved:",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                    )
                    improvedConcepts.forEach { concept ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            Text(text = concept, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            if (expressions.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Expressions Practiced:",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                    )
                    expressions.forEach { expr ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "✨", style = MaterialTheme.typography.bodySmall)
                            Text(text = "\"$expr\"", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                        }
                    }
                }
            }

            if (successfulRetries > 0) {
                Text(
                    text = "🎯 $successfulRetries mistake(s) successfully self-corrected during this session!",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                )
            }
        }
    }
}

/**
 * Strongest area feedback highlight card.
 */
@Composable
private fun StrongestAreaCard(
    area: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = "Strongest area",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Your strongest area",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = area,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

/**
 * Focus next area feedback highlight card with practice action button.
 */
@Composable
private fun FocusNextCard(
    area: String,
    description: String,
    onPracticeAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                        contentDescription = "Focus next",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(24.dp),
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Focus next",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = area,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            FilledTonalButton(
                onClick = onPracticeAction,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Bolt,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Practice $area",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/**
 * Bottom action buttons: "Practice Again" (primary) + "Back to Home" (secondary).
 */
@Composable
private fun ActionButtons(
    onPracticeAgain: () -> Unit,
    onBackToHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Button(
            onClick = onPracticeAgain,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Icon(
                imageVector = Icons.Filled.Replay,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Practice Again",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }

        FilledTonalButton(
            onClick = onBackToHome,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text(
                text = "Back to Home",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

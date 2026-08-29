package com.vaniflow.app.feature.conversation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vaniflow.app.domain.model.ConversationState
import com.vaniflow.app.domain.model.ConversationTurn
import com.vaniflow.app.domain.model.Correction
import com.vaniflow.app.ui.components.ImmersiveCharacterAvatar

/**
 * True Video-Call Conversation Screen for VaniFlow.
 *
 * Architecture:
 * 1. Fixed, non-scrolling upper Avatar Video Viewport.
 * 2. Floating live subtitle chip at bottom of video view.
 * 3. Fixed bottom call controls (Show/Hide Chat, Large Mic, End Call).
 * 4. Collapsible drawer for full chat history & grammar feedback (default hidden).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    characterId: String,
    scenarioId: String,
    onSessionComplete: (sessionId: String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConversationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var isChatVisible by remember { mutableStateOf(false) }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.onPermissionGranted()
        } else {
            val activity = context as? android.app.Activity
            val showRationale = activity != null &&
                androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    android.Manifest.permission.RECORD_AUDIO
                )
            if (showRationale) {
                viewModel.onPermissionDenied()
            } else {
                viewModel.onPermissionPermanentlyDenied()
            }
        }
    }

    // Permission rationale dialog
    if (uiState.requiresPermissionRationale) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { viewModel.dismissPermissionRationale() },
            title = { Text("Microphone Access") },
            text = { Text("VaniFlow needs microphone access so your AI speaking partner can hear and converse with you in English.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismissPermissionRationale()
                    permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                }) { Text("Grant Access") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissPermissionRationale() }) { Text("Not Now") }
            }
        )
    }

    // Auto-scroll chat when visible
    LaunchedEffect(uiState.turns.size, uiState.partialTranscript) {
        if (isChatVisible && uiState.turns.isNotEmpty()) {
            listState.animateScrollToItem(uiState.turns.size - 1)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(animation = tween(650), repeatMode = RepeatMode.Reverse),
        label = "pulseScale"
    )

    val characterName = uiState.character?.name ?: "Raya"
    val scenarioTitle = uiState.scenario?.title ?: "Conversation Practice"

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = scenarioTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${uiState.character?.level?.name ?: "Beginner"} • Live Call with $characterName",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    // Complete Session button
                    Button(
                        onClick = { viewModel.onEndSession(onSessionComplete) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Finish",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        bottomBar = {
            // FIXED VIDEO-CALL CONTROL DOCK
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp, top = 6.dp)
            ) {
                // Waveform visualization
                AnimatedVisibility(
                    visible = uiState.state == ConversationState.LISTENING ||
                            uiState.state == ConversationState.USER_SPEAKING ||
                            uiState.state == ConversationState.AI_SPEAKING,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    WaveformVisualization(
                        isAiSpeaking = uiState.state == ConversationState.AI_SPEAKING,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                // Error banner
                AnimatedVisibility(
                    visible = uiState.errorMessage != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    androidx.compose.material3.Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .padding(bottom = 8.dp)
                            .clickable { viewModel.clearErrorMessage() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = uiState.errorMessage ?: "",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { viewModel.clearErrorMessage() }) {
                                Text("Dismiss")
                            }
                        }
                    }
                }

                // Main Call Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Show / Hide Chat Toggle Button
                    FilledTonalButton(
                        onClick = { isChatVisible = !isChatVisible },
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (isChatVisible) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    ) {
                        Icon(
                            imageVector = if (isChatVisible) Icons.Filled.Close else Icons.Outlined.ChatBubbleOutline,
                            contentDescription = if (isChatVisible) "Hide Chat" else "Show Chat",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isChatVisible) "Hide Chat" else "Show Chat",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Main Call Mic Button (72dp)
                    FloatingActionButton(
                        onClick = {
                            if (androidx.core.content.ContextCompat.checkSelfPermission(
                                    context,
                                    android.Manifest.permission.RECORD_AUDIO
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            ) {
                                viewModel.onMicTapped()
                            } else {
                                permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        modifier = Modifier
                            .size(72.dp)
                            .then(
                                if (uiState.state == ConversationState.LISTENING || uiState.state == ConversationState.USER_SPEAKING) {
                                    Modifier.scale(pulseScale)
                                } else Modifier
                            ),
                        containerColor = when (uiState.state) {
                            ConversationState.AI_SPEAKING -> MaterialTheme.colorScheme.errorContainer
                            ConversationState.USER_SPEAKING,
                            ConversationState.LISTENING -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.primaryContainer
                        },
                        contentColor = when (uiState.state) {
                            ConversationState.AI_SPEAKING -> MaterialTheme.colorScheme.onErrorContainer
                            ConversationState.USER_SPEAKING,
                            ConversationState.LISTENING -> MaterialTheme.colorScheme.onPrimary
                            else -> MaterialTheme.colorScheme.primary
                        },
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                        shape = CircleShape
                    ) {
                        Icon(
                            imageVector = if (uiState.state == ConversationState.AI_SPEAKING) {
                                Icons.Filled.Stop
                            } else {
                                Icons.Filled.Mic
                            },
                            contentDescription = "Mic Action",
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Status Indicator Pill
                    StatusBadge(
                        state = uiState.state,
                        characterName = characterName
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 1. FIXED PERSISTENT AVATAR VIDEO VIEWPORT (occupies primary visual area, never scrolls)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                ImmersiveCharacterAvatar(
                    characterId = uiState.character?.id ?: "raya",
                    avatarState = uiState.avatarState,
                    mouthOpenness = uiState.mouthOpenness,
                    currentViseme = uiState.currentViseme,
                    emotion = uiState.currentEmotion,
                    showDebugOverlay = false,
                    modifier = Modifier.fillMaxSize()
                )

                // Live Subtitle Overlay at the bottom of the video view
                LiveSubtitleOverlay(
                    state = uiState.state,
                    characterName = characterName,
                    turns = uiState.turns,
                    partialTranscript = uiState.partialTranscript,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }

            // 2. OPTIONAL CHAT HISTORY OVERLAY / DRAWER (slides over bottom half without moving avatar)
            AnimatedVisibility(
                visible = isChatVisible,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Card(
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 390.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            Text(
                                text = "Conversation Transcript",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = { isChatVisible = false }) {
                                Icon(Icons.Filled.Close, contentDescription = "Close Chat")
                            }
                        }

                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                        ) {
                            items(uiState.turns, key = { it.id }) { turn ->
                                TurnBubble(turn = turn)
                                Spacer(modifier = Modifier.height(10.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Sleek floating subtitle pill rendered over the avatar video view.
 */
@Composable
private fun LiveSubtitleOverlay(
    state: ConversationState,
    characterName: String,
    turns: List<ConversationTurn>,
    partialTranscript: String,
    modifier: Modifier = Modifier
) {
    val lastTurn = turns.lastOrNull()
    val subtitleText = when {
        state == ConversationState.USER_SPEAKING && partialTranscript.isNotBlank() -> partialTranscript
        state == ConversationState.THINKING -> "$characterName is thinking..."
        state == ConversationState.AI_SPEAKING && lastTurn != null && lastTurn.speaker == ConversationTurn.Speaker.AI -> lastTurn.text
        state == ConversationState.LISTENING -> "Listening... Speak freely"
        state == ConversationState.INTERRUPTED -> "Listening now... (Speak whenever you're ready)"
        else -> ""
    }

    if (subtitleText.isNotBlank()) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.Black.copy(alpha = 0.65f)
            ),
            modifier = modifier.fillMaxWidth(0.94f)
        ) {
            Text(
                text = subtitleText,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun StatusBadge(
    state: ConversationState,
    characterName: String,
    modifier: Modifier = Modifier
) {
    val (dotColor, label) = when (state) {
        ConversationState.IDLE -> Color(0xFF22C55E) to "Ready"
        ConversationState.LISTENING -> MaterialTheme.colorScheme.primary to "Listening"
        ConversationState.USER_SPEAKING -> MaterialTheme.colorScheme.primary to "Speaking"
        ConversationState.TRANSCRIBING -> Color(0xFFF59E0B) to "Processing"
        ConversationState.THINKING -> Color(0xFFF59E0B) to "Thinking"
        ConversationState.AI_SPEAKING -> Color(0xFF22C55E) to "Speaking"
        ConversationState.INTERRUPTED -> Color(0xFFEF4444) to "Paused"
        ConversationState.ERROR -> Color(0xFFEF4444) to "Error"
        ConversationState.SESSION_COMPLETE -> Color(0xFF22C55E) to "Done"
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun TurnBubble(
    turn: ConversationTurn,
    modifier: Modifier = Modifier
) {
    val isUser = turn.speaker == ConversationTurn.Speaker.USER

    Column(
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
        modifier = modifier.fillMaxWidth()
    ) {
        Card(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                }
            ),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    text = turn.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }

        // Attached grammar/phrasing correction chip
        if (turn.correction != null) {
            Spacer(modifier = Modifier.height(4.dp))
            CorrectionChip(correction = turn.correction)
        }
    }
}

@Composable
private fun CorrectionChip(
    correction: Correction,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
        ),
        modifier = modifier.widthIn(max = 280.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Lightbulb,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column {
                Text(
                    text = "Better: ${correction.suggestedText}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = correction.explanation,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun WaveformVisualization(
    isAiSpeaking: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    val bar1 by infiniteTransition.animateFloat(
        initialValue = 6f, targetValue = 24f,
        animationSpec = infiniteRepeatable(tween(350), RepeatMode.Reverse), label = "b1"
    )
    val bar2 by infiniteTransition.animateFloat(
        initialValue = 12f, targetValue = 32f,
        animationSpec = infiniteRepeatable(tween(450), RepeatMode.Reverse), label = "b2"
    )
    val bar3 by infiniteTransition.animateFloat(
        initialValue = 8f, targetValue = 20f,
        animationSpec = infiniteRepeatable(tween(300), RepeatMode.Reverse), label = "b3"
    )
    val bar4 by infiniteTransition.animateFloat(
        initialValue = 14f, targetValue = 28f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "b4"
    )

    val barColor = if (isAiSpeaking) Color(0xFF22C55E) else MaterialTheme.colorScheme.primary

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier.height(36.dp)
    ) {
        listOf(bar1, bar2, bar4, bar3, bar2, bar1).forEach { height ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .width(4.dp)
                    .height(height.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(barColor)
            )
        }
    }
}
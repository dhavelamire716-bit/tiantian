package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.CameraEnhance
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.SlowMotionVideo
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.CameraMotionStyle
import com.example.data.SampleVideoPrompts
import com.example.data.VideoAspectRatio
import com.example.data.VideoStyle
import com.example.ui.GenerationStage
import com.example.ui.GenerationUiState
import com.example.ui.StudioControlsState
import java.io.File

@Composable
fun StudioScreen(
    controls: StudioControlsState,
    generationState: GenerationUiState,
    onPromptChange: (String) -> Unit,
    onEnhancePrompt: () -> Unit,
    onAspectRatioSelected: (VideoAspectRatio) -> Unit,
    onStyleSelected: (VideoStyle) -> Unit,
    onCameraMotionSelected: (CameraMotionStyle) -> Unit,
    onSceneCountSelected: (Int) -> Unit,
    onBurnInCaptionsChanged: (Boolean) -> Unit,
    onGenerateVideo: () -> Unit,
    onCancelGeneration: () -> Unit,
    onDismissError: () -> Unit,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isBusy = generationState is GenerationUiState.Generating ||
        generationState is GenerationUiState.EnhancingPrompt
    var sampleIndex by remember { mutableIntStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.widthIn(max = 760.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Hero Studio Banner
            StudioHeroHeader(
                isDarkTheme = controls.isDarkTheme,
                onToggleTheme = onToggleTheme
            )

            // Active Generation Theatre or Error Banner
            AnimatedVisibility(
                visible = generationState is GenerationUiState.Generating,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                if (generationState is GenerationUiState.Generating) {
                    GenerationTheatreCard(
                        state = generationState,
                        onCancel = onCancelGeneration
                    )
                }
            }

            AnimatedVisibility(
                visible = generationState is GenerationUiState.Error,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                if (generationState is GenerationUiState.Error) {
                    ErrorStatusCard(
                        message = generationState.message,
                        onDismiss = onDismissError,
                        onRetry = onGenerateVideo
                    )
                }
            }

            // Prompt Composer Card
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Videocam,
                                contentDescription = "Video Prompt",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Director Prompt",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = {
                                    val nextPrompt = SampleVideoPrompts[sampleIndex % SampleVideoPrompts.size]
                                    sampleIndex++
                                    onPromptChange(nextPrompt)
                                },
                                enabled = !isBusy,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("random_prompt_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Casino,
                                    contentDescription = "Insert random video prompt",
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            }

                            if (controls.prompt.isNotEmpty()) {
                                IconButton(
                                    onClick = { onPromptChange("") },
                                    enabled = !isBusy,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("clear_prompt_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Clear prompt",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = controls.prompt,
                        onValueChange = onPromptChange,
                        enabled = !isBusy,
                        placeholder = {
                            Text(
                                text = "Describe the scenes, lighting, and movement you want to bring to life...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        },
                        minLines = 4,
                        maxLines = 7,
                        shape = MaterialTheme.shapes.medium,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("prompt_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${controls.prompt.length} chars",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        FilledTonalButton(
                            onClick = onEnhancePrompt,
                            enabled = !isBusy && controls.prompt.isNotBlank(),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("enhance_prompt_button")
                        ) {
                            if (generationState is GenerationUiState.EnhancingPrompt) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Enhancing...")
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.AutoAwesome,
                                    contentDescription = "AI Enhance Prompt",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("AI Enhance")
                            }
                        }
                    }

                    // Inspiration Prompt Chips
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 2.dp)
                    ) {
                        items(SampleVideoPrompts) { sample ->
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier
                                    .widthIn(max = 220.dp)
                                    .clickable(enabled = !isBusy) { onPromptChange(sample) }
                            ) {
                                Text(
                                    text = sample,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Director Controls Card
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    // Aspect Ratio + Duration Row
                    SectionLabel(
                        icon = Icons.Outlined.AspectRatio,
                        title = "Frame & Length"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        VideoAspectRatio.entries.forEach { ratio ->
                            val selected = controls.aspectRatio == ratio
                            AspectRatioTile(
                                ratio = ratio,
                                selected = selected,
                                enabled = !isBusy,
                                onClick = { onAspectRatioSelected(ratio) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("aspect_${ratio.label.replace(':', '_')}")
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(3, 4, 5).forEach { count ->
                            val selected = controls.sceneCount == count
                            FilterChip(
                                selected = selected,
                                onClick = { onSceneCountSelected(count) },
                                enabled = !isBusy,
                                label = {
                                    Text(
                                        text = "$count Scenes (~${count * 3}s)",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("scenes_$count")
                            )
                        }
                    }

                    // Visual Style
                    SectionLabel(
                        icon = Icons.Outlined.Palette,
                        title = "Visual Aesthetic"
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(VideoStyle.entries) { style ->
                            val selected = controls.style == style
                            FilterChip(
                                selected = selected,
                                onClick = { onStyleSelected(style) },
                                enabled = !isBusy,
                                label = { Text(style.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("style_${style.name.lowercase()}")
                            )
                        }
                    }

                    // Camera Motion
                    SectionLabel(
                        icon = Icons.Outlined.SlowMotionVideo,
                        title = "Camera Motion"
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(CameraMotionStyle.entries) { motion ->
                            val selected = controls.cameraMotion == motion
                            FilterChip(
                                selected = selected,
                                onClick = { onCameraMotionSelected(motion) },
                                enabled = !isBusy,
                                label = { Text(motion.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                modifier = Modifier.minimumInteractiveComponentSize()
                            )
                        }
                    }

                    // Burn-in Captions Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .clickable(enabled = !isBusy) {
                                onBurnInCaptionsChanged(!controls.burnInCaptions)
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Subtitles,
                                contentDescription = "Cinematic Subtitles",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = "Cinematic Subtitles",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Overlay AI-written captions onto each shot",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Switch(
                            checked = controls.burnInCaptions,
                            onCheckedChange = onBurnInCaptionsChanged,
                            enabled = !isBusy,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("captions_switch")
                        )
                    }
                }
            }

            // Primary Generate Video CTA
            Button(
                onClick = onGenerateVideo,
                enabled = !isBusy && controls.prompt.isNotBlank(),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .testTag("generate_video_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.MovieCreation,
                    contentDescription = "Generate Video",
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (generationState is GenerationUiState.Generating) {
                        "Synthesizing Reel..."
                    } else {
                        "Generate Video Reel"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StudioHeroHeader(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(152.dp)
            .clip(MaterialTheme.shapes.large)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                shape = MaterialTheme.shapes.large
            )
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_studio_hero),
            contentDescription = "VerdeReel emerald cinema studio banner",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xE6041710),
                            Color(0xAA062E20),
                            Color(0x44083B29)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xCC065F46),
                    border = BorderStroke(1.dp, Color(0xFF6EE7B7).copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CameraEnhance,
                            contentDescription = null,
                            tint = Color(0xFFA7F3D0),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "VERDEREEL AI",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFECFDF5)
                        )
                    }
                }

                IconButton(
                    onClick = onToggleTheme,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0x88041A12))
                        .testTag("theme_toggle_button")
                ) {
                    Icon(
                        imageVector = if (isDarkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                        contentDescription = if (isDarkTheme) "Switch to Light Mint Theme" else "Switch to Dark Pine Theme",
                        tint = Color(0xFFA7F3D0)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Prompt to Motion Reel",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color(0xFFECFDF5)
                )
                Text(
                    text = "Write a scene, direct the camera, and render a playable MP4",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFA7F3D0)
                )
            }
        }
    }
}

@Composable
private fun AspectRatioTile(
    ratio: VideoAspectRatio,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        border = BorderStroke(if (selected) 2.dp else 1.dp, borderColor),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val boxRatio = when (ratio) {
                VideoAspectRatio.LANDSCAPE_16_9 -> 16f / 9f
                VideoAspectRatio.PORTRAIT_9_16 -> 9f / 16f
                VideoAspectRatio.SQUARE_1_1 -> 1f
            }
            Box(
                modifier = Modifier
                    .height(22.dp)
                    .aspectRatio(boxRatio)
                    .border(
                        width = 2.dp,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        shape = RoundedCornerShape(3.dp)
                    )
            )
            Text(
                text = ratio.label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}

@Composable
private fun SectionLabel(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun GenerationTheatreCard(
    state: GenerationUiState.Generating,
    onCancel: () -> Unit
) {
    val animatedProgress by animateFloatAsState(
        targetValue = state.progress.coerceIn(0f, 1f),
        label = "generation_progress"
    )

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.planTitle ?: "Generating Video Reel",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = state.statusDetail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                TextButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("cancel_generation_button")
                ) {
                    Text("Cancel", color = MaterialTheme.colorScheme.error)
                }
            }

            LinearProgressIndicator(
                progress = { animatedProgress },
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
            )

            // Stage indicators
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StageChip("1. Script", state.stage == GenerationStage.STORYBOARD)
                StageChip("2. Visuals", state.stage == GenerationStage.KEYFRAMES)
                StageChip("3. MP4 Encode", state.stage == GenerationStage.ENCODING)
            }

            // Live Filmstrip Previews
            if (state.previewFramePaths.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.previewFramePaths) { path ->
                        AsyncImage(
                            model = File(path),
                            contentDescription = "Generated scene keyframe preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(width = 96.dp, height = 64.dp)
                                .clip(MaterialTheme.shapes.small)
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                    MaterialTheme.shapes.small
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StageChip(label: String, active: Boolean) {
    Surface(
        shape = CircleShape,
        color = if (active) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun ErrorStatusCard(
    message: String,
    onDismiss: () -> Unit,
    onRetry: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.ErrorOutline,
                    contentDescription = "Error",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = "Generation Notice",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Dismiss", color = MaterialTheme.colorScheme.onErrorContainer)
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onRetry,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onErrorContainer)
                ) {
                    Text("Retry", color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
    }
}

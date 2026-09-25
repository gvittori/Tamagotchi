package com.example.tamagotchi

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.theme.ButtonRubberA
import com.example.ui.theme.ButtonRubberB
import com.example.ui.theme.ButtonRubberC
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ShellBezel
import com.example.ui.theme.ShellBezelInner
import com.example.ui.theme.ShellMint
import com.example.ui.theme.ShellMintDark

class MainActivity : ComponentActivity() {

    private val viewModel: TamagotchiViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                TamagotchiApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun TamagotchiApp(viewModel: TamagotchiViewModel) {
    val pet by viewModel.pet.collectAsState()
    val currentAction by viewModel.currentAction.collectAsState()
    val frameTick by viewModel.frameTick.collectAsState()
    val statusMsg by viewModel.statusMessage.collectAsState()
    val offlineDecayNotice by viewModel.offlineDecayNotice.collectAsState()

    val context = LocalContext.current
    var showRenameDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }

    // Request Notification permission for Android 13+
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color(0xFF191F26)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Top App Header
                TopHeaderBar(
                    pet = pet,
                    onOpenThemes = { showThemeDialog = true },
                    onRename = { showRenameDialog = true },
                    onReset = { showResetDialog = true }
                )

                // The Handheld Tamagotchi Device Body
                TamagotchiEggShell(
                    pet = pet,
                    currentAction = currentAction,
                    frameTick = frameTick,
                    statusMsg = statusMsg,
                    onButtonA = { viewModel.feedPet() },
                    onButtonB = { viewModel.playWithPet() },
                    onButtonC = { viewModel.toggleSleep() },
                    onScreenTap = {
                        if (pet.isEgg) viewModel.warmEgg()
                        else viewModel.playWithPet()
                    }
                )

                // Stats Dashboard & Quick Actions
                CareDashboard(
                    pet = pet,
                    onFeed = { viewModel.feedPet() },
                    onPlay = { viewModel.playWithPet() },
                    onSleep = { viewModel.toggleSleep() },
                    onClean = { viewModel.cleanPoop() },
                    onHeal = { viewModel.healPet() },
                    onEvolve = { viewModel.triggerEvolution() },
                    onWarmEgg = { viewModel.warmEgg() }
                )
            }
        }
    }

    // Offline Decay Dialog
    offlineDecayNotice?.let { notice ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissOfflineNotice() },
            title = {
                Text(
                    text = "⏰ Welcome Back!",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("You were away for ${notice.formattedOfflineTime}.")
                    Text("While resting offline, ${pet.name}'s stats adjusted:")
                    if (notice.hungerLost > 0f) {
                        Text("• Hunger: -${notice.hungerLost.toInt()}%")
                    }
                    if (notice.happinessLost > 0f) {
                        Text("• Happiness: -${notice.happinessLost.toInt()}%")
                    }
                    if (notice.newPoops > 0) {
                        Text("• Cleanliness: Room needs cleaning (${notice.newPoops} poop left)")
                    }
                    if (notice.becameSick) {
                        Text("• Health: Pet caught a cold and needs medicine!")
                    }
                    Text(
                        "Give ${pet.name} some love and snacks!",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.dismissOfflineNotice() },
                    modifier = Modifier.testTag("dismiss_offline_dialog")
                ) {
                    Text("Care for Pet")
                }
            }
        )
    }

    // Rename Dialog
    if (showRenameDialog) {
        var tempName by remember { mutableStateOf(pet.name) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Virtual Pet") },
            text = {
                OutlinedTextField(
                    value = tempName,
                    onValueChange = { tempName = it },
                    label = { Text("Pet Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("rename_input_field")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.renamePet(tempName)
                        showRenameDialog = false
                    },
                    modifier = Modifier.testTag("confirm_rename_button")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // LCD Themes Dialog
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Choose LCD Screen Style") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LcdTheme.values().forEach { theme ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (pet.lcdTheme == theme) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .clickable {
                                    viewModel.changeLcdTheme(theme)
                                    showThemeDialog = false
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(theme.bg)
                                    .border(2.dp, theme.pixel, CircleShape)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = theme.title,
                                fontWeight = if (pet.lcdTheme == theme) FontWeight.Bold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Reset Egg Dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset to New Egg?") },
            text = { Text("Are you sure you want to start over with a fresh Cosmic Egg? Current pet progress will be reset.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetPet()
                        showResetDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_reset_button")
                ) {
                    Text("Restart Egg")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun TopHeaderBar(
    pet: PetData,
    onOpenThemes: () -> Unit,
    onRename: () -> Unit,
    onReset: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "PIXEL-PET",
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp,
                color = Color(0xFF45B69C)
            )
            Text(
                text = "${pet.name} • ${pet.evolutionStage.displayName}",
                fontSize = 13.sp,
                color = Color(0xFFA0AAB5),
                fontWeight = FontWeight.Medium
            )
        }

        Row {
            IconButton(
                onClick = onRename,
                modifier = Modifier.testTag("button_rename_pet")
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Rename Pet",
                    tint = Color(0xFFA0AAB5)
                )
            }
            IconButton(
                onClick = onOpenThemes,
                modifier = Modifier.testTag("button_lcd_theme")
            ) {
                Icon(
                    imageVector = Icons.Default.Palette,
                    contentDescription = "Change LCD Color",
                    tint = Color(0xFFA0AAB5)
                )
            }
            IconButton(
                onClick = onReset,
                modifier = Modifier.testTag("button_reset_egg")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reset Egg",
                    tint = Color(0xFFA0AAB5)
                )
            }
        }
    }
}

@Composable
fun TamagotchiEggShell(
    pet: PetData,
    currentAction: PetAction,
    frameTick: Long,
    statusMsg: String,
    onButtonA: () -> Unit,
    onButtonB: () -> Unit,
    onButtonC: () -> Unit,
    onScreenTap: () -> Unit
) {
    // Physical Egg Shell Container
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(16.dp, RoundedCornerShape(36.dp))
            .clip(RoundedCornerShape(36.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(ShellMint, ShellMintDark)
                )
            )
            .border(4.dp, Color(0xFF1E725F), RoundedCornerShape(36.dp))
            .padding(18.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top Keychain Loop & Brand Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF225749))
                        .border(2.dp, Color(0xFF12342B), CircleShape)
                )

                Text(
                    text = "★ TAMAGOTCHI 1996 ★",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp,
                    color = Color(0xFFE2F7F2)
                )

                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF225749))
                        .border(2.dp, Color(0xFF12342B), CircleShape)
                )
            }

            // Inset Screen Bezel Frame
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(ShellBezel)
                    .border(3.dp, ShellBezelInner, RoundedCornerShape(18.dp))
                    .padding(12.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // LCD Screen
                    Box(
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onScreenTap
                            )
                            .testTag("virtual_lcd_screen")
                    ) {
                        PixelScreen(
                            pet = pet,
                            currentAction = currentAction,
                            frameTick = frameTick
                        )
                    }

                    // Ticker Message
                    Text(
                        text = statusMsg,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF8FD8C7),
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }

            // Speaker Holes
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 2.dp)
            ) {
                repeat(5) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E5244))
                    )
                }
            }

            // Classic 3-Button Layout
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Button A: Select / Feed
                HardwareButton(
                    label = "A",
                    subLabel = "FEED",
                    color = ButtonRubberA,
                    testTag = "hardware_button_a",
                    onClick = onButtonA
                )

                // Button B: Execute / Play
                HardwareButton(
                    label = "B",
                    subLabel = "PLAY",
                    color = ButtonRubberB,
                    testTag = "hardware_button_b",
                    onClick = onButtonB
                )

                // Button C: Cancel / Sleep
                HardwareButton(
                    label = "C",
                    subLabel = if (pet.isSleeping) "WAKE" else "SLEEP",
                    color = ButtonRubberC,
                    testTag = "hardware_button_c",
                    onClick = onButtonC
                )
            }
        }
    }
}

@Composable
fun HardwareButton(
    label: String,
    subLabel: String,
    color: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .shadow(6.dp, CircleShape)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(color, color.copy(alpha = 0.75f))
                    )
                )
                .border(3.dp, Color(0xFF12181F), CircleShape)
                .clickable(onClick = onClick)
                .testTag(testTag),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = Color.White
            )
        }

        Text(
            text = subLabel,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFFE2F7F2)
        )
    }
}

@Composable
fun CareDashboard(
    pet: PetData,
    onFeed: () -> Unit,
    onPlay: () -> Unit,
    onSleep: () -> Unit,
    onClean: () -> Unit,
    onHeal: () -> Unit,
    onEvolve: () -> Unit,
    onWarmEgg: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF222933))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Stats Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "VITAL STATS",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp,
                    color = Color(0xFFA0B2C6)
                )

                Text(
                    text = "Age: ${pet.ageDays}d ${pet.ageHours % 24}h",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF45B69C)
                )
            }

            // Stat Progress Bars
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PixelStatBar(
                    label = "Hunger",
                    value = pet.hunger,
                    icon = Icons.Default.Restaurant,
                    barColor = Color(0xFFFF5964)
                )
                PixelStatBar(
                    label = "Happy",
                    value = pet.happiness,
                    icon = Icons.Default.Favorite,
                    barColor = Color(0xFFFFB400)
                )
                PixelStatBar(
                    label = "Energy",
                    value = pet.energy,
                    icon = Icons.Default.FitnessCenter,
                    barColor = Color(0xFF35A7FF)
                )
                PixelStatBar(
                    label = "Hygiene",
                    value = pet.hygiene,
                    icon = Icons.Default.CleaningServices,
                    barColor = Color(0xFF2EC4B6)
                )
            }

            // Evolution Banner / Button
            if (pet.isEgg) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2C3E50)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "🥚 Egg Incubation",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "${pet.evolutionProgress.toInt()}%",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF45B69C),
                                fontSize = 13.sp
                            )
                        }
                        Button(
                            onClick = onWarmEgg,
                            modifier = Modifier.fillMaxWidth().testTag("button_warm_egg"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE67E22))
                        ) {
                            Icon(Icons.Default.WbSunny, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Gently Warm Egg (Tap to Hatch)")
                        }
                    }
                }
            } else {
                // Evolution Milestone Progress
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val next = pet.evolutionStage.next()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (next != null) "Growth towards ${next.displayName}" else "Master Stage Reached!",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFE2E8F0)
                        )
                        Text(
                            text = "${pet.evolutionProgress.toInt()}%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF1C40F)
                        )
                    }

                    // Progress bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(0xFF141A22))
                    ) {
                        val progressFraction = (pet.evolutionProgress / 100f).coerceIn(0f, 1f)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progressFraction)
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(Color(0xFFF1C40F))
                        )
                    }

                    // Evolution Available Button!
                    if (pet.canEvolve) {
                        Button(
                            onClick = onEvolve,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("button_trigger_evolution"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9B51E0))
                        ) {
                            Icon(Icons.Default.Star, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("EVOLVE PET NOW!", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Quick Interactive Action Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionCard(
                    modifier = Modifier.weight(1f),
                    title = "Feed",
                    icon = Icons.Default.Restaurant,
                    color = Color(0xFFFF5964),
                    testTag = "action_feed",
                    onClick = onFeed
                )
                ActionCard(
                    modifier = Modifier.weight(1f),
                    title = "Play",
                    icon = Icons.Default.SportsEsports,
                    color = Color(0xFFFFB400),
                    testTag = "action_play",
                    onClick = onPlay
                )
                ActionCard(
                    modifier = Modifier.weight(1f),
                    title = if (pet.isSleeping) "Wake" else "Sleep",
                    icon = Icons.Default.Bedtime,
                    color = Color(0xFF35A7FF),
                    testTag = "action_sleep",
                    onClick = onSleep
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionCard(
                    modifier = Modifier.weight(1f),
                    title = "Clean (${pet.poopCount})",
                    icon = Icons.Default.CleaningServices,
                    color = if (pet.poopCount > 0) Color(0xFFE74C3C) else Color(0xFF2EC4B6),
                    testTag = "action_clean",
                    onClick = onClean
                )
                ActionCard(
                    modifier = Modifier.weight(1f),
                    title = if (pet.isSick) "Cure Sick!" else "Health OK",
                    icon = Icons.Default.MedicalServices,
                    color = if (pet.isSick) Color(0xFFE74C3C) else Color(0xFF27AE60),
                    testTag = "action_heal",
                    onClick = onHeal
                )
            }
        }
    }
}

@Composable
fun PixelStatBar(
    label: String,
    value: Float,
    icon: ImageVector,
    barColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = barColor,
            modifier = Modifier.size(16.dp)
        )

        Text(
            text = label,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFFD5DFE8),
            modifier = Modifier.width(55.dp)
        )

        // Segmented Pixel Bar (10 blocks)
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            val filledBlocks = ((value / 10f).toInt()).coerceIn(0, 10)
            for (i in 0 until 10) {
                val isFilled = i < filledBlocks
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (isFilled) barColor else Color(0xFF141920))
                        .border(1.dp, Color(0xFF2D3748), RoundedCornerShape(2.dp))
                )
            }
        }

        Text(
            text = "${value.toInt()}%",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFFA0B2C6),
            modifier = Modifier.width(36.dp),
            textAlign = TextAlign.End
        )
    }
}

@Composable
fun ActionCard(
    modifier: Modifier = Modifier,
    title: String,
    icon: ImageVector,
    color: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        color = Color(0xFF2D3748),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = color,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

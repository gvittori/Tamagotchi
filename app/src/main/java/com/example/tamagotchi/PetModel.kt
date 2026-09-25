package com.example.tamagotchi

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.LcdAmberBg
import com.example.ui.theme.LcdAmberPixel
import com.example.ui.theme.LcdClassicBg
import com.example.ui.theme.LcdClassicGhost
import com.example.ui.theme.LcdClassicPixel
import com.example.ui.theme.LcdCyberBg
import com.example.ui.theme.LcdCyberPixel
import com.example.ui.theme.LcdPocketBg
import com.example.ui.theme.LcdPocketPixel

enum class EvolutionStage(
    val displayName: String,
    val description: String,
    val requiredProgress: Float,
    val daysMilestone: Int
) {
    EGG("Cosmic Egg", "Ready to hatch into a new companion", 0f, 0),
    BABY("Baby Pip", "A tiny bouncing hatchling with big curious eyes", 20f, 0),
    CHILD("Poko-Paws", "A playful creature that loves to chase bouncy balls", 45f, 1),
    TEEN("Spikemon", "An energetic rebel with spiked crest and speedy paws", 75f, 3),
    ADULT("Titanus", "A magnificent crowned guardian with mighty presence", 100f, 7),
    MYTHIC("Astro Dragon", "A celestial legendary beast born from pristine care", 120f, 14);

    fun next(): EvolutionStage? {
        return when (this) {
            EGG -> BABY
            BABY -> CHILD
            CHILD -> TEEN
            TEEN -> ADULT
            ADULT -> MYTHIC
            MYTHIC -> null
        }
    }
}

enum class PetAction {
    IDLE,
    EATING,
    PLAYING,
    SLEEPING,
    POOPING,
    HEALING,
    EVOLVING
}

enum class LcdTheme(
    val title: String,
    val bg: Color,
    val pixel: Color,
    val ghost: Color
) {
    CLASSIC_GREEN("Classic DMG", LcdClassicBg, LcdClassicPixel, LcdClassicGhost),
    AMBER_GLOW("Amber Retro", LcdAmberBg, LcdAmberPixel, LcdAmberBg.copy(alpha = 0.4f)),
    CYBER_CYAN("Cyber Neo", LcdCyberBg, LcdCyberPixel, LcdCyberBg.copy(alpha = 0.4f)),
    POCKET_GRAY("Pocket Silver", LcdPocketBg, LcdPocketPixel, LcdPocketBg.copy(alpha = 0.4f))
}

data class PetData(
    val name: String = "Mametchi",
    val hunger: Float = 85f,          // 0 to 100 (100 = full)
    val happiness: Float = 80f,       // 0 to 100 (100 = joyful)
    val energy: Float = 90f,          // 0 to 100 (100 = rested)
    val hygiene: Float = 95f,         // 0 to 100 (100 = clean)
    val evolutionStage: EvolutionStage = EvolutionStage.EGG,
    val birthTimestamp: Long = System.currentTimeMillis(),
    val lastUpdatedTimestamp: Long = System.currentTimeMillis(),
    val isSleeping: Boolean = false,
    val poopCount: Int = 0,
    val isSick: Boolean = false,
    val evolutionProgress: Float = 0f,
    val totalMealsFed: Int = 0,
    val totalGamesPlayed: Int = 0,
    val lcdTheme: LcdTheme = LcdTheme.CLASSIC_GREEN
) {
    val isEgg: Boolean get() = evolutionStage == EvolutionStage.EGG

    val ageHours: Long
        get() = maxOf(0L, (System.currentTimeMillis() - birthTimestamp) / (1000L * 3600L))

    val ageDays: Long
        get() = ageHours / 24L

    val isCritical: Boolean
        get() = !isEgg && (hunger <= 25f || happiness <= 25f || energy <= 20f || isSick)

    val canEvolve: Boolean
        get() {
            val next = evolutionStage.next() ?: return false
            return evolutionProgress >= next.requiredProgress && (!isEgg || evolutionProgress >= 10f)
        }
}

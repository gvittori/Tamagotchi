package com.example.tamagotchi

import android.content.Context
import android.content.SharedPreferences

class PetPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("pixel_pet_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_NAME = "pet_name"
        private const val KEY_HUNGER = "pet_hunger"
        private const val KEY_HAPPINESS = "pet_happiness"
        private const val KEY_ENERGY = "pet_energy"
        private const val KEY_HYGIENE = "pet_hygiene"
        private const val KEY_STAGE = "pet_stage"
        private const val KEY_BIRTH = "pet_birth"
        private const val KEY_LAST_UPDATE = "pet_last_update"
        private const val KEY_SLEEPING = "pet_sleeping"
        private const val KEY_POOP_COUNT = "pet_poop_count"
        private const val KEY_IS_SICK = "pet_is_sick"
        private const val KEY_EVO_PROGRESS = "pet_evo_progress"
        private const val KEY_MEALS = "pet_meals"
        private const val KEY_GAMES = "pet_games"
        private const val KEY_THEME = "pet_theme"
    }

    fun loadPet(): Pair<PetData, OfflineDecayResult> {
        val now = System.currentTimeMillis()
        val birth = prefs.getLong(KEY_BIRTH, now)
        val lastUpdate = prefs.getLong(KEY_LAST_UPDATE, now)

        val rawStageStr = prefs.getString(KEY_STAGE, EvolutionStage.EGG.name) ?: EvolutionStage.EGG.name
        val stage = try {
            EvolutionStage.valueOf(rawStageStr)
        } catch (_: Exception) {
            EvolutionStage.EGG
        }

        val rawThemeStr = prefs.getString(KEY_THEME, LcdTheme.CLASSIC_GREEN.name) ?: LcdTheme.CLASSIC_GREEN.name
        val theme = try {
            LcdTheme.valueOf(rawThemeStr)
        } catch (_: Exception) {
            LcdTheme.CLASSIC_GREEN
        }

        val storedData = PetData(
            name = prefs.getString(KEY_NAME, "Mametchi") ?: "Mametchi",
            hunger = prefs.getFloat(KEY_HUNGER, 85f),
            happiness = prefs.getFloat(KEY_HAPPINESS, 80f),
            energy = prefs.getFloat(KEY_ENERGY, 90f),
            hygiene = prefs.getFloat(KEY_HYGIENE, 95f),
            evolutionStage = stage,
            birthTimestamp = birth,
            lastUpdatedTimestamp = lastUpdate,
            isSleeping = prefs.getBoolean(KEY_SLEEPING, false),
            poopCount = prefs.getInt(KEY_POOP_COUNT, 0),
            isSick = prefs.getBoolean(KEY_IS_SICK, false),
            evolutionProgress = prefs.getFloat(KEY_EVO_PROGRESS, if (stage == EvolutionStage.EGG) 15f else 25f),
            totalMealsFed = prefs.getInt(KEY_MEALS, 0),
            totalGamesPlayed = prefs.getInt(KEY_GAMES, 0),
            lcdTheme = theme
        )

        // Calculate retroactive offline decay
        val elapsedSeconds = maxOf(0L, (now - lastUpdate) / 1000L)
        val decayResult = applyOfflineDecay(storedData, elapsedSeconds, now)

        // Save fresh updated state immediately
        savePet(decayResult.updatedPet)

        return Pair(decayResult.updatedPet, decayResult)
    }

    fun savePet(pet: PetData) {
        prefs.edit()
            .putString(KEY_NAME, pet.name)
            .putFloat(KEY_HUNGER, pet.hunger.coerceIn(0f, 100f))
            .putFloat(KEY_HAPPINESS, pet.happiness.coerceIn(0f, 100f))
            .putFloat(KEY_ENERGY, pet.energy.coerceIn(0f, 100f))
            .putFloat(KEY_HYGIENE, pet.hygiene.coerceIn(0f, 100f))
            .putString(KEY_STAGE, pet.evolutionStage.name)
            .putLong(KEY_BIRTH, pet.birthTimestamp)
            .putLong(KEY_LAST_UPDATE, System.currentTimeMillis())
            .putBoolean(KEY_SLEEPING, pet.isSleeping)
            .putInt(KEY_POOP_COUNT, pet.poopCount.coerceIn(0, 3))
            .putBoolean(KEY_IS_SICK, pet.isSick)
            .putFloat(KEY_EVO_PROGRESS, pet.evolutionProgress)
            .putInt(KEY_MEALS, pet.totalMealsFed)
            .putInt(KEY_GAMES, pet.totalGamesPlayed)
            .putString(KEY_THEME, pet.lcdTheme.name)
            .apply()
    }

    private fun applyOfflineDecay(
        pet: PetData,
        elapsedSeconds: Long,
        currentTimeMs: Long
    ): OfflineDecayResult {
        if (elapsedSeconds < 5) {
            return OfflineDecayResult(
                updatedPet = pet.copy(lastUpdatedTimestamp = currentTimeMs),
                elapsedSeconds = elapsedSeconds,
                hungerLost = 0f,
                happinessLost = 0f,
                energyLostOrGained = 0f,
                newPoops = 0,
                becameSick = false
            )
        }

        // Egg state has slower decay and simply warms up toward hatching
        if (pet.isEgg) {
            val progressGain = minOf(40f, elapsedSeconds / 60f * 1.5f)
            val updated = pet.copy(
                evolutionProgress = (pet.evolutionProgress + progressGain).coerceAtMost(100f),
                lastUpdatedTimestamp = currentTimeMs
            )
            return OfflineDecayResult(
                updatedPet = updated,
                elapsedSeconds = elapsedSeconds,
                hungerLost = 0f,
                happinessLost = 0f,
                energyLostOrGained = 0f,
                newPoops = 0,
                becameSick = false
            )
        }

        // Minutes offline (capped to 24 hours to prevent total instant death)
        val effectiveSeconds = minOf(elapsedSeconds, 86400L).toFloat()
        val minutesOffline = effectiveSeconds / 60f

        val hungerDecayRate = if (pet.isSleeping) 0.04f else 0.12f // points per min
        val hungerLoss = (minutesOffline * hungerDecayRate).coerceAtMost(85f)
        val newHunger = (pet.hunger - hungerLoss).coerceAtLeast(0f)

        // Energy decays if awake, recovers if sleeping
        val energyDelta: Float
        val newEnergy: Float
        val newSleepingState: Boolean
        if (pet.isSleeping) {
            val recovery = (minutesOffline * 0.4f).coerceAtMost(100f)
            newEnergy = (pet.energy + recovery).coerceAtMost(100f)
            energyDelta = recovery
            // Auto wake up when full energy if away long enough
            newSleepingState = newEnergy < 95f
        } else {
            val energyLoss = (minutesOffline * 0.08f).coerceAtMost(80f)
            newEnergy = (pet.energy - energyLoss).coerceAtLeast(0f)
            energyDelta = -energyLoss
            newSleepingState = false
        }

        // Hygiene drops, generating poops
        val hygieneLoss = (minutesOffline * 0.05f).coerceAtMost(100f)
        val newHygiene = (pet.hygiene - hygieneLoss).coerceAtLeast(0f)
        val generatedPoop = when {
            newHygiene < 30f && pet.poopCount == 0 -> 2
            newHygiene < 60f && pet.poopCount == 0 -> 1
            else -> 0
        }
        val totalPoop = minOf(3, pet.poopCount + generatedPoop)

        // Happiness decay accelerated by hunger and poop
        val neglectMultiplier = (if (newHunger < 30f) 1.8f else 1.0f) * (if (totalPoop > 0) 1.5f else 1.0f)
        val happinessLoss = (minutesOffline * 0.10f * neglectMultiplier).coerceAtMost(90f)
        val newHappiness = (pet.happiness - happinessLoss).coerceAtLeast(0f)

        // Sickness trigger if stats drop critically for long time
        val becameSick = !pet.isSick && (newHunger <= 5f || totalPoop >= 2 || newHygiene <= 15f)

        // Evolution progress naturally accrues over days survived with decent care
        val careBonus = if (newHunger > 50f && newHappiness > 50f) 1f else 0.2f
        val evoAccrued = (minutesOffline / 60f * 1.2f * careBonus).coerceAtMost(30f)
        val newEvoProgress = (pet.evolutionProgress + evoAccrued).coerceAtMost(120f)

        val updatedPet = pet.copy(
            hunger = newHunger,
            happiness = newHappiness,
            energy = newEnergy,
            hygiene = newHygiene,
            poopCount = totalPoop,
            isSick = pet.isSick || becameSick,
            isSleeping = newSleepingState,
            evolutionProgress = newEvoProgress,
            lastUpdatedTimestamp = currentTimeMs
        )

        return OfflineDecayResult(
            updatedPet = updatedPet,
            elapsedSeconds = elapsedSeconds,
            hungerLost = hungerLoss,
            happinessLost = happinessLoss,
            energyLostOrGained = energyDelta,
            newPoops = generatedPoop,
            becameSick = becameSick
        )
    }

    fun resetPet(name: String = "Mametchi"): PetData {
        val now = System.currentTimeMillis()
        val fresh = PetData(
            name = name,
            hunger = 85f,
            happiness = 85f,
            energy = 95f,
            hygiene = 100f,
            evolutionStage = EvolutionStage.EGG,
            birthTimestamp = now,
            lastUpdatedTimestamp = now,
            isSleeping = false,
            poopCount = 0,
            isSick = false,
            evolutionProgress = 0f,
            totalMealsFed = 0,
            totalGamesPlayed = 0,
            lcdTheme = LcdTheme.CLASSIC_GREEN
        )
        savePet(fresh)
        return fresh
    }
}

data class OfflineDecayResult(
    val updatedPet: PetData,
    val elapsedSeconds: Long,
    val hungerLost: Float,
    val happinessLost: Float,
    val energyLostOrGained: Float,
    val newPoops: Int,
    val becameSick: Boolean
) {
    val hadSignificantDecay: Boolean
        get() = elapsedSeconds > 60 && (hungerLost > 3f || happinessLost > 3f || newPoops > 0 || becameSick)

    val formattedOfflineTime: String
        get() {
            val minutes = elapsedSeconds / 60
            val hours = minutes / 60
            val days = hours / 24
            return when {
                days > 0 -> "$days d ${hours % 24} h"
                hours > 0 -> "$hours h ${minutes % 60} m"
                else -> "$minutes m"
            }
        }
}

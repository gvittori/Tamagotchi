package com.example.tamagotchi

import android.app.Application
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TamagotchiViewModel(application: Application) : AndroidViewModel(application) {

    private val petPrefs = PetPreferences(application)
    private val vibrator = application.getSystemService(Vibrator::class.java)

    private val _pet = MutableStateFlow(PetData())
    val pet: StateFlow<PetData> = _pet.asStateFlow()

    private val _currentAction = MutableStateFlow(PetAction.IDLE)
    val currentAction: StateFlow<PetAction> = _currentAction.asStateFlow()

    private val _frameTick = MutableStateFlow(0L)
    val frameTick: StateFlow<Long> = _frameTick.asStateFlow()

    private val _offlineDecayNotice = MutableStateFlow<OfflineDecayResult?>(null)
    val offlineDecayNotice: StateFlow<OfflineDecayResult?> = _offlineDecayNotice.asStateFlow()

    private val _statusMessage = MutableStateFlow("Ready for adventure!")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    init {
        // Initialize Notification Channels
        NotificationWorker.createNotificationChannel(application)
        NotificationWorker.schedulePeriodicCareCheck(application)

        // Load persisted pet with offline decay calculation
        val (loadedPet, decayResult) = petPrefs.loadPet()
        _pet.value = loadedPet

        if (decayResult.hadSignificantDecay) {
            _offlineDecayNotice.value = decayResult
        }

        // Start 60 FPS animation tick loop
        startAnimationLoop()

        // Start dynamic real-time stats decay loop
        startStatsDecayLoop()
    }

    private fun startAnimationLoop() {
        viewModelScope.launch {
            while (isActive) {
                _frameTick.value += 1L
                delay(16L) // ~60 FPS animation target
            }
        }
    }

    private fun startStatsDecayLoop() {
        viewModelScope.launch {
            var secondCounter = 0
            while (isActive) {
                delay(1000L) // 1 second intervals for decay
                secondCounter++

                val current = _pet.value
                val now = System.currentTimeMillis()

                if (current.isEgg) {
                    // Eggs gain incubation progress
                    val newProgress = (current.evolutionProgress + 0.1f).coerceAtMost(100f)
                    _pet.value = current.copy(
                        evolutionProgress = newProgress,
                        lastUpdatedTimestamp = now
                    )
                } else {
                    // Awake vs Sleeping decay
                    var newHunger = current.hunger
                    var newHappiness = current.happiness
                    var newEnergy = current.energy
                    var newHygiene = current.hygiene
                    var poopCount = current.poopCount
                    var isSick = current.isSick

                    if (current.isSleeping) {
                        // Sleeping recovers energy and slows hunger decay
                        newEnergy = (newEnergy + 0.35f).coerceAtMost(100f)
                        newHunger = (newHunger - 0.04f).coerceAtLeast(0f)
                    } else {
                        // Awake: normal stats decay
                        newHunger = (newHunger - 0.15f).coerceAtLeast(0f)
                        newEnergy = (newEnergy - 0.08f).coerceAtLeast(0f)
                        newHygiene = (newHygiene - 0.06f).coerceAtLeast(0f)

                        // Happiness decay faster if hungry or dirty
                        val hungerPenalty = if (newHunger < 30f) 2.2f else 1.0f
                        val poopPenalty = if (poopCount > 0) 1.8f else 1.0f
                        val happinessDecayRate = 0.12f * hungerPenalty * poopPenalty
                        newHappiness = (newHappiness - happinessDecayRate).coerceAtLeast(0f)

                        // Hygiene creates poops
                        if (newHygiene < 35f && poopCount == 0) {
                            poopCount = 1
                            triggerVibration(longArrayOf(0, 100, 50, 100))
                            _statusMessage.value = "${current.name} made a mess!"
                        } else if (newHygiene < 15f && poopCount == 1) {
                            poopCount = 2
                        }

                        // Sickness if neglected
                        if (!isSick && (newHunger <= 0f || (poopCount >= 2 && newHygiene <= 10f))) {
                            isSick = true
                            _statusMessage.value = "${current.name} got sick! Needs medicine!"
                            triggerVibration(longArrayOf(0, 250, 100, 250))
                        }
                    }

                    // Natural evolution progress if well cared for
                    val careBonus = if (newHunger > 60f && newHappiness > 60f && !isSick) 0.08f else 0.01f
                    val newEvoProgress = (current.evolutionProgress + careBonus).coerceAtMost(150f)

                    _pet.value = current.copy(
                        hunger = newHunger,
                        happiness = newHappiness,
                        energy = newEnergy,
                        hygiene = newHygiene,
                        poopCount = poopCount,
                        isSick = isSick,
                        evolutionProgress = newEvoProgress,
                        lastUpdatedTimestamp = now
                    )
                }

                // Auto-save every 5 seconds
                if (secondCounter % 5 == 0) {
                    petPrefs.savePet(_pet.value)
                }
            }
        }
    }

    fun dismissOfflineNotice() {
        _offlineDecayNotice.value = null
    }

    fun feedPet() {
        val current = _pet.value
        if (current.isEgg) {
            _statusMessage.value = "Keep the egg warm to hatch it!"
            return
        }
        if (current.isSleeping) {
            _statusMessage.value = "${current.name} is asleep! Wake up first."
            return
        }
        if (current.hunger >= 98f) {
            _statusMessage.value = "${current.name} is completely full!"
            return
        }

        triggerHapticClick()
        val updated = current.copy(
            hunger = (current.hunger + 25f).coerceAtMost(100f),
            happiness = (current.happiness + 8f).coerceAtMost(100f),
            evolutionProgress = (current.evolutionProgress + 3f).coerceAtMost(150f),
            totalMealsFed = current.totalMealsFed + 1,
            lastUpdatedTimestamp = System.currentTimeMillis()
        )
        _pet.value = updated
        petPrefs.savePet(updated)

        _statusMessage.value = "Nom nom! ${current.name} enjoyed the meal!"
        playTemporaryAction(PetAction.EATING, 2600L)
    }

    fun playWithPet() {
        val current = _pet.value
        if (current.isEgg) {
            // Tapping egg warms it up and hastens hatching!
            warmEgg()
            return
        }
        if (current.isSleeping) {
            _statusMessage.value = "${current.name} is asleep! Zzz..."
            return
        }
        if (current.energy <= 10f) {
            _statusMessage.value = "${current.name} is too exhausted to play! Needs sleep."
            return
        }

        triggerHapticClick()
        val updated = current.copy(
            happiness = (current.happiness + 25f).coerceAtMost(100f),
            hunger = (current.hunger - 6f).coerceAtLeast(0f),
            energy = (current.energy - 12f).coerceAtLeast(0f),
            evolutionProgress = (current.evolutionProgress + 4f).coerceAtMost(150f),
            totalGamesPlayed = current.totalGamesPlayed + 1,
            lastUpdatedTimestamp = System.currentTimeMillis()
        )
        _pet.value = updated
        petPrefs.savePet(updated)

        _statusMessage.value = "Wheee! ${current.name} had so much fun!"
        playTemporaryAction(PetAction.PLAYING, 3000L)
    }

    fun toggleSleep() {
        val current = _pet.value
        if (current.isEgg) {
            _statusMessage.value = "Eggs don't need sleep! Warm it up to hatch."
            return
        }

        triggerHapticClick()
        val newSleep = !current.isSleeping
        val updated = current.copy(
            isSleeping = newSleep,
            lastUpdatedTimestamp = System.currentTimeMillis()
        )
        _pet.value = updated
        petPrefs.savePet(updated)

        if (newSleep) {
            _currentAction.value = PetAction.SLEEPING
            _statusMessage.value = "Lights out! ${current.name} is resting..."
        } else {
            _currentAction.value = PetAction.IDLE
            _statusMessage.value = "Good morning! ${current.name} woke up energized!"
        }
    }

    fun cleanPoop() {
        val current = _pet.value
        if (current.poopCount == 0 && current.hygiene >= 90f) {
            _statusMessage.value = "The room is already spotless!"
            return
        }

        triggerHapticClick()
        val updated = current.copy(
            poopCount = 0,
            hygiene = 100f,
            happiness = (current.happiness + 15f).coerceAtMost(100f),
            lastUpdatedTimestamp = System.currentTimeMillis()
        )
        _pet.value = updated
        petPrefs.savePet(updated)

        _statusMessage.value = "Sparkling clean! Fresh and tidy!"
    }

    fun healPet() {
        val current = _pet.value
        if (!current.isSick) {
            _statusMessage.value = "${current.name} is in great health!"
            return
        }

        triggerVibration(longArrayOf(0, 150, 80, 150))
        val updated = current.copy(
            isSick = false,
            happiness = (current.happiness + 20f).coerceAtMost(100f),
            lastUpdatedTimestamp = System.currentTimeMillis()
        )
        _pet.value = updated
        petPrefs.savePet(updated)

        _statusMessage.value = "Medicine administered! ${current.name} feels much better!"
    }

    fun warmEgg() {
        val current = _pet.value
        if (!current.isEgg) return

        triggerHapticClick()
        val newProgress = current.evolutionProgress + 10f
        if (newProgress >= 30f) {
            // Hatch egg into Baby!
            triggerEvolution()
        } else {
            val updated = current.copy(
                evolutionProgress = newProgress,
                lastUpdatedTimestamp = System.currentTimeMillis()
            )
            _pet.value = updated
            petPrefs.savePet(updated)
            _statusMessage.value = "You gently warmed the egg! Crack lines appear... (${newProgress.toInt()}% ready)"
        }
    }

    fun triggerEvolution() {
        val current = _pet.value
        val nextStage = current.evolutionStage.next() ?: return

        triggerVibration(longArrayOf(0, 200, 100, 300, 100, 500))
        viewModelScope.launch {
            _currentAction.value = PetAction.EVOLVING
            _statusMessage.value = "✨ What?! ${current.name} is evolving! ✨"

            delay(3500L)

            val evolvedPet = current.copy(
                evolutionStage = nextStage,
                evolutionProgress = 0f,
                happiness = 100f,
                hunger = 95f,
                energy = 100f,
                isSleeping = false,
                lastUpdatedTimestamp = System.currentTimeMillis()
            )
            _pet.value = evolvedPet
            petPrefs.savePet(evolvedPet)
            _currentAction.value = PetAction.IDLE
            _statusMessage.value = "🎉 Congratulations! ${current.name} evolved into ${nextStage.displayName}!"
        }
    }

    fun changeLcdTheme(newTheme: LcdTheme) {
        val updated = _pet.value.copy(lcdTheme = newTheme)
        _pet.value = updated
        petPrefs.savePet(updated)
        triggerHapticClick()
    }

    fun renamePet(newName: String) {
        if (newName.isBlank()) return
        val updated = _pet.value.copy(name = newName.trim())
        _pet.value = updated
        petPrefs.savePet(updated)
        _statusMessage.value = "Pet renamed to ${updated.name}!"
    }

    fun resetPet() {
        val fresh = petPrefs.resetPet(_pet.value.name)
        _pet.value = fresh
        _currentAction.value = PetAction.IDLE
        _statusMessage.value = "New Cosmic Egg has arrived! Care for it well."
        triggerVibration(longArrayOf(0, 100, 50, 100))
    }

    private fun playTemporaryAction(action: PetAction, durationMs: Long) {
        viewModelScope.launch {
            _currentAction.value = action
            delay(durationMs)
            if (_pet.value.isSleeping) {
                _currentAction.value = PetAction.SLEEPING
            } else {
                _currentAction.value = PetAction.IDLE
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun triggerHapticClick() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                vibrator?.vibrate(30)
            }
        } catch (_: Exception) {
        }
    }

    @Suppress("DEPRECATION")
    private fun triggerVibration(pattern: LongArray) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                vibrator?.vibrate(pattern, -1)
            }
        } catch (_: Exception) {
        }
    }
}

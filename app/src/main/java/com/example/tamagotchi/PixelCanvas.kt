package com.example.tamagotchi

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun PixelScreen(
    pet: PetData,
    currentAction: PetAction,
    frameTick: Long,
    modifier: Modifier = Modifier
) {
    val theme = pet.lcdTheme

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.15f)
            .clip(RoundedCornerShape(12.dp))
            .background(theme.bg)
            .border(3.dp, Color(0xFF14181D), RoundedCornerShape(12.dp))
            .padding(6.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            // Virtual LCD grid size: 36 columns x 32 rows
            val cols = 36
            val rows = 32
            val pixelW = canvasWidth / cols
            val pixelH = canvasHeight / rows

            // 1. Draw subtle LCD matrix ghost dots
            drawLcdGrid(cols, rows, pixelW, pixelH, theme.ghost)

            // 2. Draw Top LCD Icon Indicators
            drawTopIndicators(pet, pixelW, pixelH, theme.pixel, frameTick)

            // 3. Draw Poop if any
            if (pet.poopCount > 0) {
                val poopX = 2
                val poopY = 19
                drawSprite(PixelSprites.POOP, poopX, poopY, pixelW, pixelH, theme.pixel)
                if (pet.poopCount >= 2) {
                    drawSprite(PixelSprites.POOP, 6, 21, pixelW, pixelH, theme.pixel)
                }
            }

            // 4. Draw Sick skull if pet is sick
            if (pet.isSick) {
                val skullY = if ((frameTick / 20) % 2 == 0L) 6 else 7
                drawSprite(PixelSprites.SICK_SKULL, 3, skullY, pixelW, pixelH, theme.pixel)
            }

            // 5. Calculate Pet Animation Frame & Position
            val isActionAnim = currentAction != PetAction.IDLE
            val frameToggle = (frameTick / 18) % 2 == 0L
            val petSprite = getPetSprite(pet, currentAction, frameToggle)

            // Center pet horizontally and on ground
            var petX = (cols - 16) / 2
            var petY = 11

            // Dynamic action movements
            when (currentAction) {
                PetAction.PLAYING -> {
                    // Pet hops left and right with ball
                    val hopPhase = (frameTick / 12) % 4
                    petX += when (hopPhase) {
                        0L -> -2
                        1L -> 0
                        2L -> 2
                        else -> 0
                    }
                    if (hopPhase == 1L || hopPhase == 3L) petY -= 2

                    // Draw bouncing ball
                    val ballX = if (hopPhase < 2) cols - 8 else 4
                    val ballY = 18 + if (frameToggle) -3 else 0
                    drawSprite(PixelSprites.BALL, ballX, ballY, pixelW, pixelH, theme.pixel)

                    // Draw joyful heart
                    if (frameToggle) {
                        drawSprite(PixelSprites.HEART, petX + 4, petY - 6, pixelW, pixelH, theme.pixel)
                    }
                }
                PetAction.EATING -> {
                    // Food falls down into pet's mouth
                    val eatStage = (frameTick / 12) % 3
                    val foodSprite = if (pet.totalMealsFed % 2 == 0) PixelSprites.APPLE else PixelSprites.MEAT
                    if (eatStage < 2) {
                        drawSprite(foodSprite, petX - 5, 10 + (eatStage * 4).toInt(), pixelW, pixelH, theme.pixel)
                    } else {
                        // Heart appears when swallowed
                        drawSprite(PixelSprites.HEART, petX - 4, 10, pixelW, pixelH, theme.pixel)
                    }
                }
                PetAction.SLEEPING -> {
                    // Floating Zzz
                    val zzzCycle = (frameTick / 15) % 3
                    val zOffset = zzzCycle.toInt() * 3
                    drawSprite(PixelSprites.ZZZ, petX + 13 + zOffset, petY - 3 - zOffset, pixelW, pixelH, theme.pixel)
                }
                PetAction.EVOLVING -> {
                    // Sparkling rings around pet
                    val angleOffset = (frameTick % 360) * 0.1f
                    for (i in 0 until 6) {
                        val angle = angleOffset + (i * 3.14159f / 3f)
                        val radius = 9f + sin((frameTick * 0.2f) + i) * 2f
                        val sx = (cols / 2) + (cos(angle) * radius).toInt() - 4
                        val sy = (rows / 2) + (sin(angle) * radius).toInt() - 4
                        if (sx in 0 until cols - 8 && sy in 0 until rows - 8) {
                            drawSprite(PixelSprites.STAR_SPARKLE, sx, sy, pixelW, pixelH, theme.pixel)
                        }
                    }
                    // Flash effect
                    if ((frameTick / 8) % 2 == 0L) {
                        petY -= 1
                    }
                }
                else -> {
                    // Idle bounce for lively feel
                    if (pet.isEgg) {
                        // Egg wobbles
                        if ((frameTick / 25) % 2 == 0L) petX += 1 else petX -= 1
                    } else {
                        // Subtle breathing hop
                        if (!frameToggle) petY += 1
                    }
                }
            }

            // Draw Pet Sprite
            drawSprite(petSprite, petX, petY, pixelW, pixelH, theme.pixel)

            // 6. Ground baseline
            val groundY = 27
            for (gx in 2 until cols - 2 step 2) {
                drawRect(
                    color = theme.pixel,
                    topLeft = Offset(gx * pixelW, groundY * pixelH),
                    size = Size(pixelW, pixelH * 0.7f)
                )
            }
        }
    }
}

private fun DrawScope.drawLcdGrid(
    cols: Int,
    rows: Int,
    pixelW: Float,
    pixelH: Float,
    ghostColor: Color
) {
    for (r in 0 until rows) {
        for (c in 0 until cols) {
            drawCircle(
                color = ghostColor,
                radius = minOf(pixelW, pixelH) * 0.12f,
                center = Offset((c + 0.5f) * pixelW, (r + 0.5f) * pixelH)
            )
        }
    }
}

private fun DrawScope.drawSprite(
    sprite: List<String>,
    startX: Int,
    startY: Int,
    pixelW: Float,
    pixelH: Float,
    pixelColor: Color
) {
    for (r in sprite.indices) {
        val rowStr = sprite[r]
        for (c in rowStr.indices) {
            val char = rowStr[c]
            if (char == '#' || char == '-') {
                val drawX = (startX + c) * pixelW
                val drawY = (startY + r) * pixelH
                drawRect(
                    color = pixelColor,
                    topLeft = Offset(drawX, drawY),
                    size = Size(pixelW * 0.95f, pixelH * 0.95f)
                )
            }
        }
    }
}

private fun DrawScope.drawTopIndicators(
    pet: PetData,
    pixelW: Float,
    pixelH: Float,
    pixelColor: Color,
    tick: Long
) {
    // Mini 3x3 status icons at top row:
    // Food status indicator (dots)
    val hungerBars = (pet.hunger / 25f).toInt().coerceIn(0, 4)
    for (i in 0 until 4) {
        val filled = i < hungerBars
        val x = 2 + i * 2
        val y = 2
        if (filled) {
            drawRect(pixelColor, Offset(x * pixelW, y * pixelH), Size(pixelW, pixelH))
        }
    }

    // Happy heart indicator
    if (pet.happiness > 60f || (tick / 20) % 2 == 0L && pet.happiness > 25f) {
        drawSprite(PixelSprites.HEART, 12, 1, pixelW * 0.75f, pixelH * 0.75f, pixelColor)
    }

    // Sleep Z if sleeping
    if (pet.isSleeping) {
        drawSprite(PixelSprites.ZZZ, 20, 1, pixelW * 0.75f, pixelH * 0.75f, pixelColor)
    }

    // Evolution Star ready indicator
    if (pet.canEvolve && (tick / 15) % 2 == 0L) {
        drawSprite(PixelSprites.STAR_SPARKLE, 28, 1, pixelW * 0.8f, pixelH * 0.8f, pixelColor)
    }
}

private fun getPetSprite(pet: PetData, action: PetAction, frameToggle: Boolean): List<String> {
    if (action == PetAction.SLEEPING || pet.isSleeping) {
        return when (pet.evolutionStage) {
            EvolutionStage.EGG -> PixelSprites.EGG_FRAME_0
            EvolutionStage.BABY -> PixelSprites.BABY_SLEEP
            EvolutionStage.CHILD -> PixelSprites.CHILD_FRAME_0
            EvolutionStage.TEEN -> PixelSprites.TEEN_FRAME_0
            EvolutionStage.ADULT -> PixelSprites.ADULT_FRAME_0
            EvolutionStage.MYTHIC -> PixelSprites.MYTHIC_FRAME_0
        }
    }

    return when (pet.evolutionStage) {
        EvolutionStage.EGG -> {
            if (action == PetAction.EVOLVING) PixelSprites.EGG_FRAME_1
            else if (frameToggle) PixelSprites.EGG_FRAME_0
            else PixelSprites.EGG_FRAME_1
        }
        EvolutionStage.BABY -> {
            if (frameToggle) PixelSprites.BABY_FRAME_0 else PixelSprites.BABY_FRAME_1
        }
        EvolutionStage.CHILD -> {
            if (frameToggle) PixelSprites.CHILD_FRAME_0 else PixelSprites.CHILD_FRAME_1
        }
        EvolutionStage.TEEN -> {
            if (frameToggle) PixelSprites.TEEN_FRAME_0 else PixelSprites.TEEN_FRAME_1
        }
        EvolutionStage.ADULT -> {
            if (frameToggle) PixelSprites.ADULT_FRAME_0 else PixelSprites.ADULT_FRAME_1
        }
        EvolutionStage.MYTHIC -> {
            if (frameToggle) PixelSprites.MYTHIC_FRAME_0 else PixelSprites.MYTHIC_FRAME_1
        }
    }
}

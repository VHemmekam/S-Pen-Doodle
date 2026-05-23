package com.example

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

// A single point in a doodle path
data class DoodlePoint(
    val x: Float,
    val y: Float,
    val pressure: Float = 1.0f,
    val timestamp: Long = System.currentTimeMillis()
)

// A continuous path made of doodle points
data class DoodlePath(
    val id: Long = System.nanoTime(),
    val points: List<DoodlePoint>,
    val color: Color,
    val strokeWidth: Float,
    val initialTimestamp: Long = System.currentTimeMillis()
)

object DoodleState {
    // Current drawing settings
    val brushWidth = MutableStateFlow(8f) // in dp (will convert to px in screen scale)
    val brushColor = MutableStateFlow(Color(0xFF818CF8)) // Frosted Indigo default
    val fadeTimeSeconds = MutableStateFlow(3f) // 3 seconds default (Float.MAX_VALUE means never fade)
    val sPenOnlyMode = MutableStateFlow(false) // Palm rejection (only register stylus input)
    val isTouchThrough = MutableStateFlow(false) // "Watch Mode" where background canvas is click-through

    // Shared list of active paths currently visible on the canvas
    val activePaths = mutableStateListOf<DoodlePath>()

    // Preset color palette (High-contrast, glowing neon shades perfect for drawing overlaying video)
    val colorPalette = listOf(
        Color(0xFF818CF8), // Frosted Indigo
        Color(0xFFF472B6), // Frosted Pink
        Color(0xFF39FF14), // Neon Green
        Color(0xFF00F5FF), // Neon Cyan
        Color(0xFFFFF700), // Neon Yellow
        Color(0xFFFF5E00), // Neon Orange
        Color(0xFFFFFFFF), // Pure White
        Color(0xFF1E1E1E)  // Obsidian Black
    )

    // Preset fade times options
    val fadeOptions = listOf(
        Pair("1s", 1f),
        Pair("2s", 2f),
        Pair("4s", 4f),
        Pair("8s", 8f),
        Pair("12s", 12f),
        Pair("Never", Float.MAX_VALUE)
    )

    // Service active state
    val isServiceRunning = MutableStateFlow(false)

    fun clear() {
        activePaths.clear()
    }

    fun addPath(points: List<DoodlePoint>, color: Color, strokeWidth: Float) {
        if (points.isEmpty()) return
        activePaths.add(
            DoodlePath(
                points = points,
                color = color,
                strokeWidth = strokeWidth
            )
        )
    }
}

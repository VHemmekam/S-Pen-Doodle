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
    val initialTimestamp: Long = System.currentTimeMillis(),
    val isEraser: Boolean = false
)

object DoodleState {
    // Current drawing settings
    val brushWidth = MutableStateFlow(8f) // in dp (will convert to px in screen scale)
    val brushColor = MutableStateFlow(Color(0xFF818CF8)) // Frosted Indigo default
    val fadeTimeSeconds = MutableStateFlow(3f) // 3 seconds default (Float.MAX_VALUE means never fade)
    val sPenOnlyMode = MutableStateFlow(false) // Palm rejection (only register stylus input)
    val isTouchThrough = MutableStateFlow(false) // "Watch Mode" where background canvas is click-through
    val isEraserMode = MutableStateFlow(false) // Eraser drawing tool
    
    // Custom color state selected via custom picker
    val customColorHue = MutableStateFlow(240f) // Hue 0..360
    val customColorSat = MutableStateFlow(1.0f) // Saturation 0..1
    val customColorVal = MutableStateFlow(1.0f) // Value 0..1
    val customColorTone = MutableStateFlow(0.5f) // Tone from 0.0 (Black) to 0.5 (Pure Color) to 1.0 (White)

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
        Color(0xFFFF3366)  // Electric Red (instead of invisible Obsidian Black on black menu!)
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

    fun init(context: android.content.Context) {
        val prefs = context.getSharedPreferences("SPenDoodle_prefs", android.content.Context.MODE_PRIVATE)
        brushWidth.value = prefs.getFloat("brush_width", 8f)
        val colorInt = prefs.getInt("brush_color", Color(0xFF818CF8).toArgbInt())
        brushColor.value = Color(colorInt)
        fadeTimeSeconds.value = prefs.getFloat("fade_time_seconds", 3f)
        sPenOnlyMode.value = prefs.getBoolean("spen_only_mode", false)
        isTouchThrough.value = prefs.getBoolean("is_touch_through", false)
        customColorHue.value = prefs.getFloat("custom_color_hue", 240f)
        customColorSat.value = prefs.getFloat("custom_color_sat", 1.0f)
        customColorVal.value = prefs.getFloat("custom_color_val", 1.0f)
        customColorTone.value = prefs.getFloat("custom_color_tone", 0.5f)
    }

    fun save(context: android.content.Context, key: String, value: Any?) {
        val prefs = context.getSharedPreferences("SPenDoodle_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().apply {
            when (value) {
                is Float -> putFloat(key, value)
                is Int -> putInt(key, value)
                is Boolean -> putBoolean(key, value)
                is String -> putString(key, value)
                null -> remove(key)
            }
            apply()
        }
    }

    fun updateCustomColorFromColor(color: Color) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color.toArgbInt(), hsv)
        val h = hsv[0]
        val s = hsv[1]
        val v = hsv[2]

        // Preserve previous Hue if selected color is highly desaturated/grayscale (e.g. white or gray)
        if (s > 0.04f) {
            customColorHue.value = h
        }
        customColorSat.value = s
        customColorVal.value = v

        val tone = if (v < 0.99f) {
            v * 0.5f
        } else {
            0.5f + (1.0f - s) * 0.5f
        }
        customColorTone.value = tone
    }

    // Extension helper for Color parsing/conversion since we may need basic toArgb/fromArgb mapping
    fun Color.toArgbInt(): Int {
        return ((this.alpha * 255.0f + 0.5f).toInt() shl 24) or
               ((this.red * 255.0f + 0.5f).toInt() shl 16) or
               ((this.green * 255.0f + 0.5f).toInt() shl 8) or
               (this.blue * 255.0f + 0.5f).toInt()
    }

    fun clear() {
        activePaths.clear()
    }

    fun addPath(points: List<DoodlePoint>, color: Color, strokeWidth: Float, isEraser: Boolean = false) {
        if (points.isEmpty()) return
        activePaths.add(
            DoodlePath(
                points = points,
                color = color,
                strokeWidth = strokeWidth,
                isEraser = isEraser
            )
        )
    }
}

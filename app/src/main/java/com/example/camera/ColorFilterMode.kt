package com.example.camera

import androidx.compose.ui.graphics.ColorMatrix

enum class ColorFilterMode(val displayName: String, val matrix: ColorMatrix?) {
    NORMAL("Normale", null),
    HIGH_CONTRAST_BW(
        "B&N Contrasto Alto",
        ColorMatrix(
            floatArrayOf(
                0.33f, 0.59f, 0.11f, 0f, 0f,
                0.33f, 0.59f, 0.11f, 0f, 0f,
                0.33f, 0.59f, 0.11f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    ),
    YELLOW_ON_BLACK(
        "Giallo su Nero",
        ColorMatrix(
            floatArrayOf(
                0.33f, 0.59f, 0.11f, 0f, 0f,
                0.33f, 0.59f, 0.11f, 0f, 0f,
                0f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    ),
    INVERTED(
        "Invertito",
        ColorMatrix(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    )
}

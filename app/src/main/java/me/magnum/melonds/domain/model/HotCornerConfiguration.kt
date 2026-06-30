package me.magnum.melonds.domain.model

data class HotCornerConfiguration(
    val enabled: Boolean = true,
    val topLeftEnabled: Boolean = true,
    val topRightEnabled: Boolean = true,
    val bottomLeftEnabled: Boolean = true,
    val bottomRightEnabled: Boolean = true,
) {
    val hasEnabledCorner: Boolean
        get() = enabled && (topLeftEnabled || topRightEnabled || bottomLeftEnabled || bottomRightEnabled)
}

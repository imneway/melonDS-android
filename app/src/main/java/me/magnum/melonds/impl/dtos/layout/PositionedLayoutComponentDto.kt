package me.magnum.melonds.impl.dtos.layout

import com.google.gson.annotations.SerializedName
import me.magnum.melonds.domain.model.layout.PositionedLayoutComponent
import me.magnum.melonds.utils.enumValueOfIgnoreCase

data class PositionedLayoutComponentDto(
    @SerializedName("rect")
    val rect: RectDto,
    @SerializedName("component")
    val component: String,
    @SerializedName("alpha")
    val alpha: Float? = null,
    @SerializedName("opacity")
    val opacity: Int? = null,
    @SerializedName("onTop")
    val onTop: Boolean? = null,
) {

    companion object {
        fun fromModel(positionedLayoutComponent: PositionedLayoutComponent): PositionedLayoutComponentDto {
            return PositionedLayoutComponentDto(
                RectDto.fromModel(positionedLayoutComponent.rect),
                positionedLayoutComponent.component.name,
                positionedLayoutComponent.alpha,
                null,
                positionedLayoutComponent.onTop,
            )
        }
    }

    fun toModel(): PositionedLayoutComponent {
        val alphaValue = alpha ?: opacity?.coerceIn(0, 100)?.let { it / 100f } ?: 1f
        return PositionedLayoutComponent(
            rect.toModel(),
            enumValueOfIgnoreCase(component),
            alphaValue,
            onTop ?: false,
        )
    }
}

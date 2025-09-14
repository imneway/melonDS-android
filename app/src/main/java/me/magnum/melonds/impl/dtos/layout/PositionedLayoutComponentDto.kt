package me.magnum.melonds.impl.dtos.layout

import com.google.gson.annotations.SerializedName
import me.magnum.melonds.domain.model.layout.PositionedLayoutComponent
import me.magnum.melonds.utils.enumValueOfIgnoreCase

data class PositionedLayoutComponentDto(
    @SerializedName("rect")
    val rect: RectDto,
    @SerializedName("component")
    val component: String,
    @SerializedName("opacity")
    val opacity: Int = 100,
) {

    companion object {
        fun fromModel(positionedLayoutComponent: PositionedLayoutComponent): PositionedLayoutComponentDto {
            return PositionedLayoutComponentDto(
                RectDto.fromModel(positionedLayoutComponent.rect),
                positionedLayoutComponent.component.name,
                positionedLayoutComponent.opacity,
            )
        }
    }

    fun toModel(): PositionedLayoutComponent {
        return PositionedLayoutComponent(rect.toModel(), enumValueOfIgnoreCase(component), opacity)
    }
}
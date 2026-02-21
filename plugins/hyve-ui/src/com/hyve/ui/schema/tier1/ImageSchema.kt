package com.hyve.ui.schema.tier1

import com.hyve.ui.core.id.ElementType
import com.hyve.ui.core.id.PropertyName
import com.hyve.ui.schema.*

/**
 * Schema for Image element - displays images and sprites.
 *
 * Image elements are used to display textures, icons, and other graphics.
 * They support scaling, tinting, and various fit modes.
 *
 * Common properties:
 * - Source: Path to the image file
 * - Tint: Color tint applied to the image
 * - Stretch: How the image is scaled to fit (WPF/NoesisGUI enum)
 */
object ImageSchema {
    fun create(): ElementSchema = ElementSchema(
        type = ElementType("Image"),
        category = ElementCategory.MEDIA,
        description = "Image display element for showing textures, icons, and graphics",
        canHaveChildren = false,
        properties = listOf(
            // Content
            PropertySchema(
                name = PropertyName("Source"),
                type = PropertyType.IMAGE_PATH,
                required = true,
                description = "Path to the image file"
            ),

            // Visual effects
            PropertySchema(
                name = PropertyName("Tint"),
                type = PropertyType.COLOR,
                required = false,
                description = "Color tint applied to the image"
            ),
            PropertySchema(
                name = PropertyName("Opacity"),
                type = PropertyType.NUMBER,
                required = false,
                description = "Image opacity (0.0 to 1.0)"
            ),

            // Scaling and fit (WPF/NoesisGUI Stretch enum)
            PropertySchema(
                name = PropertyName("Stretch"),
                type = PropertyType.TEXT,
                required = false,
                description = "How image is scaled to fit: None, Fill, Uniform, UniformToFill"
            ),

            // UV mapping (for sprites)
            PropertySchema(
                name = PropertyName("UV"),
                type = PropertyType.TUPLE,
                required = false,
                description = "UV coordinates for sprite atlas (X, Y, Width, Height)"
            ),

            // Rotation and flip
            PropertySchema(
                name = PropertyName("Rotation"),
                type = PropertyType.NUMBER,
                required = false,
                description = "Rotation angle in degrees"
            ),
            PropertySchema(
                name = PropertyName("FlipHorizontal"),
                type = PropertyType.BOOLEAN,
                required = false,
                description = "Flip image horizontally"
            ),
            PropertySchema(
                name = PropertyName("FlipVertical"),
                type = PropertyType.BOOLEAN,
                required = false,
                description = "Flip image vertically"
            ),

            // Layout
            CommonPropertySchemas.anchor("Position and size of the image"),

            // State
            CommonPropertySchemas.visible(),

            // Click handling (for clickable images)
            PropertySchema(
                name = PropertyName("OnClick"),
                type = PropertyType.TEXT,
                required = false,
                description = "Event triggered when image is clicked"
            ),
            CommonPropertySchemas.enabled("Whether the image is clickable"),

            // Style reference
            CommonPropertySchemas.style()
        ),
        examples = listOf(
            """
            Image #Logo {
                Source: "textures/ui/logo.png";
                Anchor: (Left: 10, Top: 10, Width: 100, Height: 100);
                Stretch: Uniform;
            }
            """.trimIndent(),
            """
            Image #Icon {
                Source: "textures/icons/sword.png";
                Anchor: (Left: 20, Top: 20, Width: 32, Height: 32);
                Tint: #ffcc00;
            }
            """.trimIndent(),
            """
            Image #Sprite {
                Source: "textures/atlas.png";
                UV: (X: 0, Y: 0, Width: 64, Height: 64);
                Anchor: (Width: 64, Height: 64);
            }
            """.trimIndent()
        )
    )
}

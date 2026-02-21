package com.hyve.ui.rendering.layout

/**
 * Represents a rectangular area in 2D space.
 * Used for defining parent bounds and calculating element positions.
 *
 * @param x Left edge position (pixels)
 * @param y Top edge position (pixels)
 * @param width Width (pixels)
 * @param height Height (pixels)
 */
data class Rect(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
) {
    /**
     * Right edge position (x + width)
     */
    val right: Float get() = x + width

    /**
     * Bottom edge position (y + height)
     */
    val bottom: Float get() = y + height

    /**
     * Center X position
     */
    val centerX: Float get() = x + width / 2f

    /**
     * Center Y position
     */
    val centerY: Float get() = y + height / 2f

    /**
     * Check if this rect contains a point
     */
    fun contains(pointX: Float, pointY: Float): Boolean =
        pointX >= x && pointX <= right && pointY >= y && pointY <= bottom

    /**
     * Check if this rect intersects another rect
     */
    fun intersects(other: Rect): Boolean =
        x < other.right && right > other.x && y < other.bottom && bottom > other.y

    /**
     * Create a new Rect offset by the given amounts
     */
    fun offset(dx: Float, dy: Float): Rect =
        Rect(x + dx, y + dy, width, height)

    override fun toString(): String =
        "Rect(x=$x, y=$y, width=$width, height=$height)"

    companion object {
        /**
         * Create a rect from position and size
         */
        fun fromPosition(x: Float, y: Float, width: Float, height: Float): Rect =
            Rect(x, y, width, height)

        /**
         * Create a rect from left/top/right/bottom edges
         */
        fun fromEdges(left: Float, top: Float, right: Float, bottom: Float): Rect =
            Rect(left, top, right - left, bottom - top)

        /**
         * Create a rect centered at a point
         */
        fun centered(centerX: Float, centerY: Float, width: Float, height: Float): Rect =
            Rect(centerX - width / 2f, centerY - height / 2f, width, height)

        /**
         * Standard screen rect (1920x1080, origin at 0,0)
         */
        fun screen(): Rect = Rect(0f, 0f, 1920f, 1080f)
    }
}

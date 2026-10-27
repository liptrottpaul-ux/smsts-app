package uk.co.asmcoach.ui.theme

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

/**
 * Rounded rectangle with iOS-style continuous ("squircle") corners. Each corner starts its curve
 * further along the edge than a circular arc would (1.4 × radius) and eases in with tangent-aligned
 * control points, so the curvature builds gradually instead of jumping as it does with
 * RoundedCornerShape. Visually matches `RoundedRectangle(cornerRadius:style: .continuous)`.
 */
class ContinuousShape(private val radius: Dp) : Shape {

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { radius.toPx() }
        val w = size.width
        val h = size.height
        // How far along each edge the corner curve extends, clamped for small or capsule shapes.
        val p = minOf(r * EXTENT, w / 2f, h / 2f)
        val c = p * CONTROL
        val path = Path().apply {
            moveTo(p, 0f)
            lineTo(w - p, 0f)
            cubicTo(w - c, 0f, w, c, w, p)
            lineTo(w, h - p)
            cubicTo(w, h - c, w - c, h, w - p, h)
            lineTo(p, h)
            cubicTo(c, h, 0f, h - c, 0f, h - p)
            lineTo(0f, p)
            cubicTo(0f, c, c, 0f, p, 0f)
            close()
        }
        return Outline.Generic(path)
    }

    override fun equals(other: Any?): Boolean = other is ContinuousShape && other.radius == radius
    override fun hashCode(): Int = radius.hashCode()

    private companion object {
        const val EXTENT = 1.4f
        /** Control point offset as a fraction of the extent; 0.25 keeps the 45° point near a circle's. */
        const val CONTROL = 0.25f
    }
}

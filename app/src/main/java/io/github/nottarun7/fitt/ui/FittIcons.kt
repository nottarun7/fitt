package io.github.nottarun7.fitt.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** One outline family for the five primary destinations. Labels supply semantics. */
object FittIcons {
    private fun icon(name: String, autoMirror: Boolean = false, draw: PathBuilder.() -> Unit) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f, autoMirror = autoMirror).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = draw)
    }.build()
    val Training = icon("Training") {
        moveTo(3f, 9f); lineTo(3f, 15f); moveTo(6f, 6f); lineTo(6f, 18f)
        moveTo(6f, 12f); lineTo(18f, 12f); moveTo(18f, 6f); lineTo(18f, 18f); moveTo(21f, 9f); lineTo(21f, 15f)
    }
    val Exercises = icon("Exercises") {
        moveTo(16.5f, 10.5f); curveTo(16.5f, 13.8f, 13.8f, 16.5f, 10.5f, 16.5f)
        curveTo(7.2f, 16.5f, 4.5f, 13.8f, 4.5f, 10.5f); curveTo(4.5f, 7.2f, 7.2f, 4.5f, 10.5f, 4.5f)
        curveTo(13.8f, 4.5f, 16.5f, 7.2f, 16.5f, 10.5f); close(); moveTo(15f, 15f); lineTo(20f, 20f)
    }
    val History = icon("History") {
        moveTo(7f, 4f); lineTo(7f, 7f); moveTo(17f, 4f); lineTo(17f, 7f)
        moveTo(4f, 9f); lineTo(20f, 9f); moveTo(6f, 5.5f); lineTo(18f, 5.5f)
        quadTo(20f, 5.5f, 20f, 7.5f); lineTo(20f, 19f); lineTo(4f, 19f); lineTo(4f, 7.5f); quadTo(4f, 5.5f, 6f, 5.5f)
        moveTo(8f, 13f); lineTo(10f, 13f); moveTo(14f, 13f); lineTo(16f, 13f)
    }
    val Nutrition = icon("Nutrition") {
        moveTo(3f, 11f); lineTo(21f, 11f); curveTo(20f, 16f, 17f, 19f, 12f, 19f); curveTo(7f, 19f, 4f, 16f, 3f, 11f)
        moveTo(8f, 21f); lineTo(16f, 21f); moveTo(8f, 7f); lineTo(9f, 4f); moveTo(13f, 7f); lineTo(14f, 4f)
    }
    val Progress = icon("Progress") {
        moveTo(4f, 4f); lineTo(4f, 20f); lineTo(21f, 20f)
        moveTo(7f, 15f); lineTo(11f, 11f); lineTo(15f, 13f); lineTo(21f, 6f)
    }
    val Forward = icon("Forward", autoMirror = true) { moveTo(9f, 5f); lineTo(16f, 12f); lineTo(9f, 19f) }
}

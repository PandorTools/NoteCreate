package com.pandortools.notecreate.ui.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Собственные иконки инструментов (24×24). */
object ToolIcons {

    val Eraser: ImageVector by lazy {
        ImageVector.Builder("eraser", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                moveTo(15.1f, 3f)
                curveTo(14.6f, 3f, 14.1f, 3.2f, 13.7f, 3.6f)
                lineTo(2.6f, 14.7f)
                curveTo(1.8f, 15.5f, 1.8f, 16.8f, 2.6f, 17.6f)
                lineTo(5f, 20f)
                horizontalLineTo(20f)
                verticalLineTo(18f)
                horizontalLineTo(12.8f)
                lineTo(20.4f, 10.4f)
                curveTo(21.2f, 9.6f, 21.2f, 8.3f, 20.4f, 7.5f)
                lineTo(16.5f, 3.6f)
                curveTo(16.1f, 3.2f, 15.6f, 3f, 15.1f, 3f)
                close()
                moveTo(5.9f, 18f)
                lineTo(4f, 16.1f)
                lineTo(9.4f, 10.7f)
                lineTo(13.6f, 14.9f)
                lineTo(10.5f, 18f)
                close()
            }
        }.build()
    }

    val Lasso: ImageVector by lazy {
        ImageVector.Builder("lasso", 24.dp, 24.dp, 24f, 24f).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 4f)
                curveTo(6.5f, 4f, 3f, 6.6f, 3f, 9.5f)
                curveTo(3f, 12.4f, 6.5f, 15f, 12f, 15f)
                curveTo(17.5f, 15f, 21f, 12.4f, 21f, 9.5f)
                curveTo(21f, 6.6f, 17.5f, 4f, 12f, 4f)
                moveTo(8f, 14.3f)
                curveTo(7f, 16f, 7.5f, 18f, 9.5f, 18.5f)
                curveTo(11f, 18.9f, 11.5f, 20f, 10.5f, 21.5f)
            }
        }.build()
    }

    val Highlighter: ImageVector by lazy {
        ImageVector.Builder("highlighter", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(15.5f, 2.5f)
                lineTo(21.5f, 8.5f)
                lineTo(12f, 18f)
                lineTo(6f, 12f)
                close()
                moveTo(5f, 13f)
                lineTo(11f, 19f)
                lineTo(9f, 21f)
                horizontalLineTo(3f)
                verticalLineTo(19.5f)
                lineTo(5.2f, 17.3f)
                lineTo(4f, 16f)
                close()
            }
        }.build()
    }

    val Pencil: ImageVector by lazy {
        ImageVector.Builder("pencil", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                moveTo(17f, 2.5f)
                lineTo(21.5f, 7f)
                lineTo(8.5f, 20f)
                lineTo(3f, 21f)
                lineTo(4f, 15.5f)
                close()
                moveTo(5.4f, 16.6f)
                lineTo(4.9f, 19.1f)
                lineTo(7.4f, 18.6f)
                close()
            }
        }.build()
    }

    val Ballpoint: ImageVector by lazy {
        ImageVector.Builder("ballpoint", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(18.5f, 2f)
                lineTo(22f, 5.5f)
                lineTo(9f, 18.5f)
                lineTo(5.5f, 15f)
                close()
                moveTo(4.8f, 15.8f)
                lineTo(8.2f, 19.2f)
                lineTo(3f, 21f)
                close()
            }
        }.build()
    }

    val Fountain: ImageVector by lazy {
        ImageVector.Builder("fountain", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                moveTo(12f, 2f)
                curveTo(15.5f, 5f, 17f, 8.5f, 17f, 11f)
                lineTo(12f, 22f)
                lineTo(7f, 11f)
                curveTo(7f, 8.5f, 8.5f, 5f, 12f, 2f)
                close()
                moveTo(12f, 9.5f)
                curveTo(11f, 9.5f, 10.5f, 10.2f, 10.5f, 11f)
                curveTo(10.5f, 11.6f, 10.9f, 12.1f, 11.4f, 12.3f)
                verticalLineTo(18f)
                horizontalLineTo(12.6f)
                verticalLineTo(12.3f)
                curveTo(13.1f, 12.1f, 13.5f, 11.6f, 13.5f, 11f)
                curveTo(13.5f, 10.2f, 13f, 9.5f, 12f, 9.5f)
                close()
            }
        }.build()
    }
}

package com.lifelog.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object LifeLogIcons {
    @Suppress("CheckReturnValue")
    public val ListAltAdd: ImageVector
      get() {
        if (_listAltAdd != null) {
          return _listAltAdd!!
        }
        _listAltAdd =
          ImageVector.Builder(
              name = "ListAltAdd",
              defaultWidth = 24.dp,
              defaultHeight = 24.dp,
              viewportWidth = 24f,
              viewportHeight = 24f,
              autoMirror = true,
            )
            .apply {
              path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.Companion.NonZero,
              ) {
                moveTo(5f, 19f)
                verticalLineTo(5f)
                verticalLineTo(19f)
                close()
                moveToRelative(0f, 2f)
                quadTo(4.18f, 21f, 3.59f, 20.41f)
                reflectiveQuadTo(3f, 19f)
                verticalLineTo(5f)
                quadTo(3f, 4.17f, 3.59f, 3.59f)
                reflectiveQuadTo(5f, 3f)
                horizontalLineTo(19f)
                quadToRelative(0.83f, 0f, 1.41f, 0.59f)
                reflectiveQuadTo(21f, 5f)
                verticalLineToRelative(7.52f)
                quadToRelative(0f, 0.43f, -0.29f, 0.7f)
                reflectiveQuadTo(20f, 13.5f)
                reflectiveQuadTo(19.29f, 13.21f)
                quadTo(19f, 12.93f, 19f, 12.5f)
                verticalLineTo(5f)
                horizontalLineTo(5f)
                verticalLineTo(19f)
                horizontalLineToRelative(6f)
                quadToRelative(0.43f, 0f, 0.71f, 0.29f)
                reflectiveQuadTo(12f, 20f)
                reflectiveQuadToRelative(-0.29f, 0.71f)
                reflectiveQuadTo(11f, 21f)
                horizontalLineTo(5f)
                close()
                moveTo(8.71f, 16.71f)
                quadTo(9f, 16.43f, 9f, 16f)
                reflectiveQuadTo(8.71f, 15.29f)
                reflectiveQuadTo(8f, 15f)
                quadTo(7.58f, 15f, 7.29f, 15.29f)
                reflectiveQuadTo(7f, 16f)
                reflectiveQuadToRelative(0.29f, 0.71f)
                reflectiveQuadTo(8f, 17f)
                reflectiveQuadTo(8.71f, 16.71f)
                close()
                moveToRelative(0f, -4f)
                quadTo(9f, 12.43f, 9f, 12f)
                reflectiveQuadTo(8.71f, 11.29f)
                reflectiveQuadTo(8f, 11f)
                quadTo(7.58f, 11f, 7.29f, 11.29f)
                reflectiveQuadTo(7f, 12f)
                reflectiveQuadToRelative(0.29f, 0.71f)
                reflectiveQuadTo(8f, 13f)
                reflectiveQuadTo(8.71f, 12.71f)
                close()
                moveToRelative(0f, -4f)
                quadTo(9f, 8.42f, 9f, 8f)
                quadTo(9f, 7.57f, 8.71f, 7.29f)
                reflectiveQuadTo(8f, 7f)
                quadTo(7.58f, 7f, 7.29f, 7.29f)
                reflectiveQuadTo(7f, 8f)
                quadTo(7f, 8.42f, 7.29f, 8.71f)
                reflectiveQuadTo(8f, 9f)
                reflectiveQuadTo(8.71f, 8.71f)
                close()
                moveTo(16f, 13f)
                quadToRelative(0.43f, 0f, 0.71f, -0.29f)
                quadTo(17f, 12.43f, 17f, 12f)
                reflectiveQuadTo(16.71f, 11.29f)
                reflectiveQuadTo(16f, 11f)
                horizontalLineTo(12f)
                quadToRelative(-0.42f, 0f, -0.71f, 0.29f)
                reflectiveQuadTo(11f, 12f)
                reflectiveQuadToRelative(0.29f, 0.71f)
                reflectiveQuadTo(12f, 13f)
                horizontalLineToRelative(4f)
                close()
                moveTo(16f, 9f)
                quadToRelative(0.43f, 0f, 0.71f, -0.29f)
                reflectiveQuadTo(17f, 8f)
                quadTo(17f, 7.57f, 16.71f, 7.29f)
                reflectiveQuadTo(16f, 7f)
                horizontalLineTo(12f)
                quadTo(11.58f, 7f, 11.29f, 7.29f)
                reflectiveQuadTo(11f, 8f)
                quadToRelative(0f, 0.42f, 0.29f, 0.71f)
                reflectiveQuadTo(12f, 9f)
                horizontalLineToRelative(4f)
                close()
                moveToRelative(-4.71f, 7.71f)
                quadTo(11.58f, 17f, 12f, 17f)
                horizontalLineToRelative(0.05f)
                quadToRelative(0.43f, 0f, 0.71f, -0.29f)
                quadTo(13.05f, 16.43f, 13.05f, 16f)
                reflectiveQuadTo(12.76f, 15.29f)
                reflectiveQuadTo(12.05f, 15f)
                horizontalLineTo(12f)
                quadToRelative(-0.42f, 0f, -0.71f, 0.29f)
                reflectiveQuadTo(11f, 16f)
                reflectiveQuadToRelative(0.29f, 0.71f)
                close()
                moveTo(17f, 20f)
                horizontalLineTo(15f)
                quadToRelative(-0.42f, 0f, -0.71f, -0.29f)
                quadTo(14f, 19.43f, 14f, 19f)
                reflectiveQuadToRelative(0.29f, -0.71f)
                reflectiveQuadTo(15f, 18f)
                horizontalLineToRelative(2f)
                verticalLineTo(16f)
                quadToRelative(0f, -0.43f, 0.29f, -0.71f)
                reflectiveQuadTo(18f, 15f)
                reflectiveQuadToRelative(0.71f, 0.29f)
                reflectiveQuadTo(19f, 16f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(2f)
                quadToRelative(0.43f, 0f, 0.71f, 0.29f)
                reflectiveQuadTo(22f, 19f)
                reflectiveQuadToRelative(-0.29f, 0.71f)
                reflectiveQuadTo(21f, 20f)
                horizontalLineTo(19f)
                verticalLineToRelative(2f)
                quadToRelative(0f, 0.43f, -0.29f, 0.71f)
                reflectiveQuadTo(18f, 23f)
                reflectiveQuadTo(17.29f, 22.71f)
                quadTo(17f, 22.43f, 17f, 22f)
                verticalLineTo(20f)
                close()
              }
            }
            .build()
        return _listAltAdd!!
      }

    private var _listAltAdd: ImageVector? = null
}

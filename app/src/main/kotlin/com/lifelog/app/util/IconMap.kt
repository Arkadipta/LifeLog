package com.lifelog.app.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.vector.ImageVector

// Rounded style throughout — matches every other icon in the app.
val eventIconMap: Map<String, ImageVector> = mapOf(
    "star" to Icons.Rounded.Star,
    "fitness_center" to Icons.Rounded.FitnessCenter,
    "restaurant" to Icons.Rounded.Restaurant,
    "local_drink" to Icons.Rounded.LocalDrink,
    "directions_run" to Icons.AutoMirrored.Rounded.DirectionsRun,
    "bedtime" to Icons.Rounded.Bedtime,
    "mood" to Icons.Rounded.Mood,
    "medication" to Icons.Rounded.Medication,
    "book" to Icons.Rounded.Book,
    "work" to Icons.Rounded.Work,
    "pets" to Icons.Rounded.Pets,
    "local_florist" to Icons.Rounded.LocalFlorist,
    "music_note" to Icons.Rounded.MusicNote,
    "self_improvement" to Icons.Rounded.SelfImprovement,
    "directions_bike" to Icons.AutoMirrored.Rounded.DirectionsBike,
    "coffee" to Icons.Rounded.Coffee,
    "checkroom" to Icons.Rounded.Checkroom,
    "attach_money" to Icons.Rounded.AttachMoney,
    "code" to Icons.Rounded.Code,
    "favorite" to Icons.Rounded.Favorite,
)

fun iconForName(name: String): ImageVector = eventIconMap[name] ?: Icons.Rounded.Star

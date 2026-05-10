package com.lifelog.app.util

import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

val eventIconMap: Map<String, ImageVector> = mapOf(
    "star" to Icons.Filled.Star,
    "fitness_center" to Icons.Filled.FitnessCenter,
    "restaurant" to Icons.Filled.Restaurant,
    "local_drink" to Icons.Filled.LocalDrink,
    "directions_run" to Icons.AutoMirrored.Filled.DirectionsRun,
    "bedtime" to Icons.Filled.Bedtime,
    "mood" to Icons.Filled.Mood,
    "medication" to Icons.Filled.Medication,
    "book" to Icons.Filled.Book,
    "work" to Icons.Filled.Work,
    "pets" to Icons.Filled.Pets,
    "local_florist" to Icons.Filled.LocalFlorist,
    "music_note" to Icons.Filled.MusicNote,
    "self_improvement" to Icons.Filled.SelfImprovement,
    "directions_bike" to Icons.AutoMirrored.Filled.DirectionsBike,
    "coffee" to Icons.Filled.Coffee,
    "checkroom" to Icons.Filled.Checkroom,
    "attach_money" to Icons.Filled.AttachMoney,
    "code" to Icons.Filled.Code,
    "favorite" to Icons.Filled.Favorite,
)

fun iconForName(name: String): ImageVector = eventIconMap[name] ?: Icons.Filled.Star

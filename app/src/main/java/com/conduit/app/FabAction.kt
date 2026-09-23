package com.conduit.app

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector

data class FabAction(
    val id: String,
    val label: String,
    val iconName: String,
    val type: String, // "APP", "MENU", "SYSTEM"
    val target: String
)

val DEFAULT_FABS = listOf(
    FabAction("1", "Recorder", "PlayArrow", "MENU", "RECORDER"),
    FabAction("2", "AI", "Star", "MENU", "AI"),
    FabAction("3", "Notes", "List", "MENU", "NOTES"),
    FabAction("4", "Compose", "Edit", "MENU", "COMPOSE")
)

fun getFabIcon(name: String): ImageVector {
    return when (name) {
        "PlayArrow" -> Icons.Filled.PlayArrow
        "Star" -> Icons.Filled.Star
        "List" -> Icons.AutoMirrored.Filled.List
        "Edit" -> Icons.Filled.Edit
        "DoneAll" -> Icons.Filled.DoneAll
        "Archive" -> Icons.Filled.Archive
        "Search" -> Icons.Filled.Search
        "Email" -> Icons.Filled.Email
        "PushPin" -> Icons.Filled.PushPin
        else -> Icons.Filled.Star
    }
}

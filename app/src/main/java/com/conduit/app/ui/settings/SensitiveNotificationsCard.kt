package com.conduit.app.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.conduit.app.SensitiveAccessUiState
import com.conduit.app.data.SensitiveAccessState
import com.conduit.app.performHapticClick
import com.conduit.app.shizuku.ShizukuStatus

private const val ADB_COMMAND_TEXT = "adb shell appops set --user 0 com.conduit.app RECEIVE_SENSITIVE_NOTIFICATIONS allow"
private val GrantedGreen = Color(0xFF2E7D32)

@Composable
fun SensitiveNotificationsCard(
    state: SensitiveAccessUiState,
    isAmoled: Boolean,
    cardContainerColor: Color,
    borderColor: Color,
    onGrantWithShizuku: () -> Unit,
    onOpenShizuku: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardContainerColor),
        border = BorderStroke(width = 1.dp, color = borderColor)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Read Sensitive Notifications",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (state.accessState == SensitiveAccessState.GRANTED) {
                    GrantedBadge()
                }
            }
            Spacer(modifier = Modifier.height(4.dp))

            when (state.accessState) {
                SensitiveAccessState.GRANTED -> CardBodyText(
                    "Conduit can read verification codes (OTPs), bank alerts, and other sensitive notifications."
                )
                SensitiveAccessState.NOT_REQUIRED -> CardBodyText(
                    "Your Android version doesn't redact sensitive notifications, so Conduit already reads them in full. No extra access is needed."
                )
                SensitiveAccessState.NOT_GRANTED, SensitiveAccessState.UNKNOWN -> {
                    if (state.isLoaded) {
                        GrantSection(state, isAmoled, onGrantWithShizuku, onOpenShizuku)
                    }
                }
            }
        }
    }
}

@Composable
private fun GrantSection(
    state: SensitiveAccessUiState,
    isAmoled: Boolean,
    onGrantWithShizuku: () -> Unit,
    onOpenShizuku: () -> Unit
) {
    val context = LocalContext.current

    if (state.shizukuStatus == ShizukuStatus.NOT_INSTALLED) {
        CardBodyText(
            "Android 15+ redacts verification codes (OTPs), bank alerts, and other sensitive notifications by default. To allow Conduit to read and log these messages, connect your device to a PC with ADB enabled and run the command below."
        )
        Spacer(modifier = Modifier.height(14.dp))
        AdbCommandBox(isAmoled)
        Spacer(modifier = Modifier.height(10.dp))
        CardBodyText("Have Shizuku? Start it and Conduit can grant this on your device, no PC needed.")
        return
    }

    CardBodyText(
        "Android 15+ redacts verification codes (OTPs), bank alerts, and other sensitive notifications by default. Shizuku can allow Conduit to read and log these messages right on your device."
    )
    Spacer(modifier = Modifier.height(14.dp))

    when (state.shizukuStatus) {
        ShizukuStatus.READY, ShizukuStatus.PERMISSION_REQUIRED -> {
            Button(
                onClick = {
                    performHapticClick(context)
                    onGrantWithShizuku()
                },
                enabled = !state.isGranting,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isGranting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Grant with Shizuku")
                }
            }
            if (state.shizukuStatus == ShizukuStatus.PERMISSION_REQUIRED) {
                Spacer(modifier = Modifier.height(6.dp))
                CardBodyText("Shizuku will ask you to allow Conduit first.")
            }
        }
        ShizukuStatus.PERMISSION_DENIED -> ShizukuActionRow(
            message = "Conduit was denied in Shizuku. Allow it in the Shizuku app, then come back.",
            onOpenShizuku = onOpenShizuku
        )
        ShizukuStatus.NOT_RUNNING -> ShizukuActionRow(
            message = "Shizuku is installed but not running. Start it, then come back.",
            onOpenShizuku = onOpenShizuku
        )
        ShizukuStatus.OUTDATED -> ShizukuActionRow(
            message = "Update Shizuku to v11 or newer to grant this on your device.",
            onOpenShizuku = onOpenShizuku
        )
        ShizukuStatus.NOT_INSTALLED -> Unit
    }

    state.errorMessage?.let { message ->
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }

    Spacer(modifier = Modifier.height(8.dp))
    var showAdb by rememberSaveable { mutableStateOf(false) }
    TextButton(
        onClick = { showAdb = !showAdb },
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
    ) {
        Text("Manual (ADB)")
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            if (showAdb) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
    }
    AnimatedVisibility(visible = showAdb) {
        Column {
            Spacer(modifier = Modifier.height(4.dp))
            AdbCommandBox(isAmoled)
        }
    }
}

@Composable
private fun ShizukuActionRow(message: String, onOpenShizuku: () -> Unit) {
    val context = LocalContext.current
    CardBodyText(message)
    Spacer(modifier = Modifier.height(10.dp))
    OutlinedButton(
        onClick = {
            performHapticClick(context)
            onOpenShizuku()
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Open Shizuku")
    }
}

@Composable
private fun GrantedBadge() {
    Surface(
        color = GrantedGreen.copy(alpha = 0.15f),
        shape = RoundedCornerShape(50)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = GrantedGreen,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Granted",
                style = MaterialTheme.typography.labelMedium,
                color = GrantedGreen,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun CardBodyText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun AdbCommandBox(isAmoled: Boolean) {
    val context = LocalContext.current
    Surface(
        color = if (isAmoled) Color(0xFF1E1E1E) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = ADB_COMMAND_TEXT,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.weight(1f),
                maxLines = 3
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Conduit ADB Command", ADB_COMMAND_TEXT)
                    clipboard.setPrimaryClip(clip)

                    performHapticClick(context)
                    Toast.makeText(context, "Command copied to clipboard!", Toast.LENGTH_SHORT).show()
                }
            ) {
                Icon(
                    Icons.Filled.ContentCopy,
                    contentDescription = "Copy command",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

package com.ambay.billing.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ambay.billing.ui.theme.BrandAccent
import com.ambay.billing.ui.theme.BrandGradient
import com.ambay.billing.ui.theme.BrandSuccess

@Composable
fun AmbayAppBar(
    title: String = "AMBAY Billing",
    subtitle: String? = null,
    syncStatus: String = "synced", // "synced" | "pending" | "offline"
    pendingCount: Int = 0,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null
) {
    var showUpdate by remember { mutableStateOf(false) }
    if (showUpdate) UpdateDialog(onDismiss = { showUpdate = false })
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(BrandGradient)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (navigationIcon != null) {
                navigationIcon()
                Spacer(modifier = Modifier.width(8.dp))
            }

            Box(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    if (subtitle != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "($subtitle)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Sync Status Pill
            SyncStatusBadge(syncStatus = syncStatus, pendingCount = pendingCount)

            IconButton(onClick = { showUpdate = true }) {
                Icon(Icons.Default.SystemUpdate, contentDescription = "App update", tint = Color.White)
            }
            if (actions != null) {
                Spacer(modifier = Modifier.width(4.dp))
                actions()
            }
        }
    }
}

@Composable
fun SyncStatusBadge(syncStatus: String, pendingCount: Int) {
    val (bgColor, icon, label) = when (syncStatus) {
        "pending" -> Triple(
            BrandAccent.copy(alpha = 0.25f),
            Icons.Default.Sync,
            if (pendingCount > 0) "$pendingCount" else "Syncing"
        )
        "offline" -> Triple(
            Color.Red.copy(alpha = 0.25f),
            Icons.Default.CloudOff,
            "Offline"
        )
        else -> Triple(
            BrandSuccess.copy(alpha = 0.25f),
            Icons.Default.CloudDone,
            "Synced"
        )
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

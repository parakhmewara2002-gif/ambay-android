package com.ambay.billing.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ambay.billing.core.auth.AuthManager
import com.ambay.billing.core.print.BluetoothPrintManager
import com.ambay.billing.core.scan.BeepManager
import com.ambay.billing.core.sync.AmbayRepository
import com.ambay.billing.ui.components.AmbayAppBar
import com.ambay.billing.ui.theme.BrandAccent
import com.ambay.billing.ui.theme.BrandBlue

@Composable
fun StaffMainScreen(
    repository: AmbayRepository,
    beepManager: BeepManager,
    bluetoothPrintManager: BluetoothPrintManager,
    authManager: AuthManager,
    onLockRequested: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: SCAN, 1: PRINT

    Scaffold(
        topBar = {
            AmbayAppBar(
                title = "AMBAY Billing",
                subtitle = "Staff",
                syncStatus = "synced",
                actions = {
                    IconButton(onClick = onLockRequested) {
                        Icon(imageVector = Icons.Default.Lock, contentDescription = "Lock", tint = Color.White)
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(imageVector = Icons.Default.QrCodeScanner, contentDescription = "Scan") },
                    label = { Text("Scan", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = BrandBlue,
                        selectedTextColor = BrandBlue,
                        indicatorColor = BrandBlue.copy(alpha = 0.15f)
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(imageVector = Icons.Default.Print, contentDescription = "Print") },
                    label = { Text("Print", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = BrandBlue,
                        selectedTextColor = BrandBlue,
                        indicatorColor = BrandBlue.copy(alpha = 0.15f)
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Crossfade(targetState = selectedTab, label = "tab_crossfade") { tab ->
                when (tab) {
                    0 -> StaffScanTab(
                        repository = repository,
                        beepManager = beepManager,
                        onSaleCompleted = { sale ->
                            // Switch to print tab after checkout
                            selectedTab = 1
                        }
                    )
                    1 -> StaffPrintTab(
                        repository = repository,
                        bluetoothPrintManager = bluetoothPrintManager
                    )
                }
            }
        }
    }
}

package com.ambay.billing.ui.owner

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ambay.billing.data.remote.GitHubUpdateClient
import com.ambay.billing.ui.common.AppUpdateDialog
import com.ambay.billing.ui.theme.BrandCyan
import com.ambay.billing.ui.theme.GlassNavItem
import com.ambay.billing.ui.theme.GlassPillBottomBar
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

@Composable
fun OwnerMainScreen(
    hazeState: HazeState,
    hasUpdateBadge: Boolean,
    updateClient: GitHubUpdateClient,
    onSwitchToStaff: () -> Unit,
    onLogout: () -> Unit,
    vm: OwnerViewModel = hiltViewModel()
) {
    var tabIndex by remember { mutableIntStateOf(0) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    val msg by vm.message.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(msg) {
        if (msg != null) {
            snackbarHostState.showSnackbar(msg!!)
            vm.clearMessage()
        }
    }

    val navItems = remember {
        listOf(
            GlassNavItem("HOME", Icons.Rounded.Home),
            GlassNavItem("BILLING", Icons.Rounded.ReceiptLong),
            GlassNavItem("ITEMS", Icons.Rounded.Inventory2),
            GlassNavItem("HISTORY", Icons.Rounded.History),
            GlassNavItem("MORE", Icons.Rounded.Menu)
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("AMBAY OWNER", style = MaterialTheme.typography.labelLarge, color = BrandCyan)
                    Text(navItems[tabIndex].label, style = MaterialTheme.typography.headlineMedium, color = Color.White)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        IconButton(onClick = { showUpdateDialog = true }) {
                            Icon(Icons.Rounded.SystemUpdate, contentDescription = "App Update", tint = Color.White)
                        }
                        if (hasUpdateBadge) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .align(Alignment.TopEnd)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEF4444))
                            )
                        }
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Rounded.Logout, contentDescription = "Logout", tint = Color.White)
                    }
                }
            }
        },
        bottomBar = {
            GlassPillBottomBar(
                items = navItems,
                selectedIndex = tabIndex,
                hazeState = hazeState,
                onSelect = { tabIndex = it }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .padding(padding)
        ) {
            Crossfade(targetState = tabIndex, label = "owner_tabs") { tab ->
                when (tab) {
                    0 -> OwnerHomeTab(vm = vm, hazeState = hazeState, onNavigateTab = { tabIndex = it })
                    1 -> OwnerBillingTab(vm = vm, hazeState = hazeState)
                    2 -> OwnerItemsTab(vm = vm, hazeState = hazeState)
                    3 -> OwnerHistoryTab(vm = vm, hazeState = hazeState)
                    else -> OwnerMoreTab(
                        vm = vm,
                        hazeState = hazeState,
                        onSwitchToStaff = onSwitchToStaff,
                        onOpenUpdate = { showUpdateDialog = true },
                        onLogout = onLogout
                    )
                }
            }
        }
    }

    if (showUpdateDialog) {
        AppUpdateDialog(updateClient = updateClient, initialRelease = null, onDismiss = { showUpdateDialog = false })
    }
}

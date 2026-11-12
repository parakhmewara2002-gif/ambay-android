package com.ambay.billing.ui.owner

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ambay.billing.BuildConfig
import com.ambay.billing.core.Item
import com.ambay.billing.core.MoneyEngine
import com.ambay.billing.hardware.PdfBillGenerator
import com.ambay.billing.ui.auth.glassTextFieldColors
import com.ambay.billing.ui.theme.BrandCyan
import com.ambay.billing.ui.theme.BrandError
import com.ambay.billing.ui.theme.BrandSuccess
import com.ambay.billing.ui.theme.BrandWarning
import com.ambay.billing.ui.theme.GlassButton
import com.ambay.billing.ui.theme.GlassCard
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Composable
fun OwnerItemsTab(
    vm: OwnerViewModel,
    hazeState: HazeState
) {
    val allItems by vm.items.collectAsState()
    val active = remember(allItems) { allItems.filter { !it.deleted } }
    var search by remember { mutableStateOf("") }
    var editingItem by remember { mutableStateOf<Item?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var stockAdjustItem by remember { mutableStateOf<Item?>(null) }

    val filtered = remember(active, search) {
        val q = MoneyEngine.normName(search)
        if (q.isEmpty()) active else active.filter {
            MoneyEngine.normName("${it.name} ${it.model} ${it.code} ${it.cat}").contains(q)
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text("Item search...") },
                colors = glassTextFieldColors(),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            GlassButton("Add", icon = Icons.Rounded.Add, onClick = {
                editingItem = null
                showAddDialog = true
            })
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassButton(
                text = "Print Labels PDF",
                icon = Icons.Rounded.Print,
                containerColor = Color.White.copy(alpha = 0.16f),
                contentColor = Color.White,
                onClick = { vm.printBarcodeLabels(filtered) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered, key = { it.id }, contentType = { "owner_item" }) { item ->
                GlassCard(
                    hazeState = hazeState,
                    cornerRadius = 18.dp,
                    modifier = Modifier.fillMaxWidth().animateItem().clickable {
                        editingItem = item
                        showAddDialog = true
                    }
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.name, color = Color.White, fontWeight = FontWeight.Bold)
                            Text("${item.cat} • Code: ${item.code}", color = Color.White.copy(alpha = 0.72f))
                            Text("Cost: ${MoneyEngine.fmtINR(item.cost)} | Sell: ${MoneyEngine.fmtINR(item.price)}", color = BrandCyan)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(if (item.tracked) "Qty: ${item.qty ?: 0}" else "Untracked", color = Color.White, fontWeight = FontWeight.Bold)
                            TextButton(onClick = { stockAdjustItem = item }) {
                                Text("Stock +/-", color = BrandCyan)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        val base = editingItem
        var name by remember { mutableStateOf(base?.name ?: "") }
        var model by remember { mutableStateOf(base?.model ?: "") }
        var cat by remember { mutableStateOf(base?.cat ?: "Accessories") }
        var code by remember { mutableStateOf(base?.code ?: "") }
        var fac by remember { mutableStateOf(base?.fac ?: "") }
        var priceStr by remember { mutableStateOf(base?.let { (it.price / 100.0).toString() } ?: "") }
        var costStr by remember { mutableStateOf(base?.let { (it.cost / 100.0).toString() } ?: "") }
        var mdStr by remember { mutableStateOf(base?.md?.let { (it / 100.0).toString() } ?: "") }
        var ws by remember { mutableStateOf(base?.ws ?: "N") }
        var war by remember { mutableStateOf(base?.war ?: "") }
        var openingStr by remember { mutableStateOf("") }

        var dupWarning by remember { mutableStateOf<String?>(null) }
        var dupArmed by remember { mutableStateOf(false) }

        LaunchedEffect(dupWarning) {
            if (dupWarning != null) {
                dupArmed = false
                delay(900) // 900ms armed second confirm
                dupArmed = true
            }
        }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = Color(0xFF0A1D42),
            titleContentColor = Color.White,
            textContentColor = Color.White,
            title = { Text(if (base == null) "Naya Item" else "Edit Item") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Name*") }, colors = glassTextFieldColors(), singleLine = true)
                    OutlinedTextField(model, { model = it }, label = { Text("Model") }, colors = glassTextFieldColors(), singleLine = true)
                    OutlinedTextField(cat, { cat = it }, label = { Text("Category*") }, colors = glassTextFieldColors(), singleLine = true)
                    OutlinedTextField(code, { code = it }, label = { Text("Code (blank = auto)") }, colors = glassTextFieldColors(), singleLine = true)
                    OutlinedTextField(fac, { fac = it }, label = { Text("Factory Barcodes") }, colors = glassTextFieldColors(), singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(priceStr, { priceStr = it }, label = { Text("Sell ₹") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), colors = glassTextFieldColors(), singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(costStr, { costStr = it }, label = { Text("Cost ₹") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), colors = glassTextFieldColors(), singleLine = true, modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(mdStr, { mdStr = it }, label = { Text("Max Discount ₹ (optional)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), colors = glassTextFieldColors(), singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = ws == "N", onClick = { ws = "N"; war = "" }, label = { Text("No Warranty (N)") })
                        FilterChip(selected = ws == "W", onClick = { ws = "W"; if (war.isBlank()) war = "6m" }, label = { Text("Warranty (W)") })
                    }
                    if (ws == "W") {
                        OutlinedTextField(war, { war = it }, label = { Text("Duration (e.g. 6m, 1y)") }, colors = glassTextFieldColors(), singleLine = true)
                    }
                    if (base == null) {
                        OutlinedTextField(openingStr, { openingStr = it }, label = { Text("Opening Stock (optional)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = glassTextFieldColors(), singleLine = true)
                    }
                    if (dupWarning != null) {
                        Text("Duplicate item mila: \"$dupWarning\". Dobara confirm karne ke liye 1s ruk kar tap karo.", color = BrandWarning)
                    }
                }
            },
            confirmButton = {
                val itemObj = Item(
                    id = base?.id ?: UUID.randomUUID().toString(),
                    name = name.trim(),
                    model = model.trim(),
                    cat = cat.trim().ifBlank { "Uncategorized" },
                    code = code.trim(),
                    fac = fac.trim(),
                    price = MoneyEngine.toPaise(priceStr),
                    cost = MoneyEngine.toPaise(costStr),
                    md = mdStr.takeIf { it.isNotBlank() }?.let { MoneyEngine.toPaise(it) },
                    ws = ws,
                    war = if (ws == "W") war.trim() else "",
                    tracked = base?.tracked ?: false,
                    qty = base?.qty,
                    createdAt = base?.createdAt ?: System.currentTimeMillis()
                )
                if (dupWarning != null) {
                    TextButton(enabled = dupArmed, onClick = {
                        vm.saveItem(itemObj, openingStr.toIntOrNull(), forceDup = true, onDuplicate = {}, onSaved = { showAddDialog = false })
                    }) {
                        Text(if (dupArmed) "Create Duplicate Anyway" else "Wait...", color = BrandWarning)
                    }
                } else {
                    TextButton(onClick = {
                        vm.saveItem(itemObj, openingStr.toIntOrNull(), forceDup = false, onDuplicate = { dupWarning = it }, onSaved = { showAddDialog = false })
                    }) {
                        Text("Save", color = BrandCyan)
                    }
                }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Cancel", color = Color.White) } }
        )
    }

    stockAdjustItem?.let { it ->
        var action by remember { mutableStateOf("purchase") }
        var qtyStr by remember { mutableStateOf("") }
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { stockAdjustItem = null },
            containerColor = Color(0xFF0A1D42),
            titleContentColor = Color.White,
            textContentColor = Color.White,
            title = { Text("Stock Adjust: ${it.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = action == "purchase", onClick = { action = "purchase" }, label = { Text("+Purchase") })
                        FilterChip(selected = action == "adjust_by", onClick = { action = "adjust_by" }, label = { Text("Adjust +/-") })
                        FilterChip(selected = action == "opening_fix", onClick = { action = "opening_fix" }, label = { Text("Opening Fix") })
                    }
                    OutlinedTextField(qtyStr, { qtyStr = it }, label = { Text("Quantity / Delta") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = glassTextFieldColors())
                    OutlinedTextField(reason, { reason = it }, label = { Text("Reason (mandatory for adjust/fix)") }, colors = glassTextFieldColors())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val q = qtyStr.toIntOrNull() ?: 0
                    vm.adjustStock(it.id, action, q, reason)
                    stockAdjustItem = null
                }) { Text("Apply", color = BrandCyan) }
            },
            dismissButton = { TextButton(onClick = { stockAdjustItem = null }) { Text("Cancel", color = Color.White) } }
        )
    }
}

@Composable
fun OwnerHistoryTab(
    vm: OwnerViewModel,
    hazeState: HazeState
) {
    val context = LocalContext.current
    val bills by vm.bills.collectAsState()
    val wsales by vm.wsales.collectAsState()
    var showPaper by remember { mutableStateOf(true) }
    val dtFmt = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale("en", "IN")) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = showPaper, onClick = { showPaper = true }, label = { Text("Paper Bills (${bills.size})") })
            FilterChip(selected = !showPaper, onClick = { showPaper = false }, label = { Text("Without Bill (${wsales.size})") })
        }
        Spacer(Modifier.height(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (showPaper) {
                items(bills, key = { it.id }, contentType = { "bill_hist" }) { b ->
                    GlassCard(hazeState = hazeState, cornerRadius = 18.dp, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${b.no} • ${b.status.uppercase()}", color = if (b.status == "cancelled") BrandError else Color.White, fontWeight = FontWeight.Bold)
                                Text(dtFmt.format(Date(b.date)), color = Color.White.copy(alpha = 0.7f))
                                Text("${b.cust.name.ifBlank { "Walk-in" }} • ${MoneyEngine.fmtINR(b.tot.total)}", color = BrandCyan)
                            }
                            Row {
                                IconButton(onClick = { vm.printBillPdf(context, b) }) { Icon(Icons.Rounded.Print, null, tint = Color.White) }
                                IconButton(onClick = { vm.shareBillPdf(b) }) { Icon(Icons.Rounded.Share, null, tint = BrandCyan) }
                                if (b.status != "cancelled") {
                                    IconButton(onClick = { vm.cancelBill(b.id, "Cancelled by owner") }) { Icon(Icons.Rounded.Delete, null, tint = BrandError) }
                                }
                            }
                        }
                    }
                }
            } else {
                items(wsales, key = { it.id }, contentType = { "wsale_hist" }) { w ->
                    GlassCard(hazeState = hazeState, cornerRadius = 18.dp, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${w.no}${if (w.voidInfo != null) " [VOID]" else ""}", color = if (w.voidInfo != null) BrandError else Color.White, fontWeight = FontWeight.Bold)
                                Text(dtFmt.format(Date(w.date)), color = Color.White.copy(alpha = 0.7f))
                                Text("${w.tot.qty} items • ${MoneyEngine.fmtINR(w.tot.total)} (${w.pay})", color = BrandCyan)
                            }
                            if (w.voidInfo == null) {
                                IconButton(onClick = { vm.voidWSale(w.id, "Voided by owner") }) {
                                    Icon(Icons.Rounded.Delete, null, tint = BrandError)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OwnerMoreTab(
    vm: OwnerViewModel,
    hazeState: HazeState,
    onSwitchToStaff: () -> Unit,
    onOpenUpdate: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val items by vm.items.collectAsState()
    val bills by vm.bills.collectAsState()
    val wsales by vm.wsales.collectAsState()
    val shop by vm.shopInfo.collectAsState()
    val devices by vm.linkedDevices.collectAsState()
    val pairingQr by vm.pairingQrText.collectAsState()

    var showDevicesDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showDriveTokenDialog by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) vm.exportBackupToUri(context, uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) vm.importBackupFromUri(context, uri, replaceAll = false)
    }

    val stockVal = remember(items) { items.filter { it.tracked && !it.deleted && (it.qty ?: 0) > 0 }.sumOf { (it.qty ?: 0) * it.cost } }
    val totalGst = remember(bills) { bills.filter { it.status != "cancelled" }.sumOf { it.tot.tax } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        GlassCard(hazeState = hazeState, modifier = Modifier.fillMaxWidth()) {
            Text("Reports & Valuation", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Spacer(Modifier.height(6.dp))
            Text("Stock Valuation (Cost): ${MoneyEngine.fmtINR(stockVal)}", color = BrandCyan, fontWeight = FontWeight.Bold)
            Text("Total GST Collected: ${MoneyEngine.fmtINR(totalGst)}", color = Color.White)
            Text("Total Records: ${items.size} items • ${bills.size} bills • ${wsales.size} WB sales", color = Color.White.copy(alpha = 0.75f))
        }

        GlassCard(hazeState = hazeState, modifier = Modifier.fillMaxWidth()) {
            Text("Website-Compatible Backup & Drive", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassButton("Export JSON", icon = Icons.Rounded.Download, onClick = { exportLauncher.launch("ambay-backup.json") }, modifier = Modifier.weight(1f))
                GlassButton("Import JSON", icon = Icons.Rounded.Upload, containerColor = Color.White.copy(alpha = 0.16f), contentColor = Color.White, onClick = { importLauncher.launch(arrayOf("application/json")) }, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            GlassButton("Google Drive Backup", icon = Icons.Rounded.CloudUpload, onClick = { showDriveTokenDialog = true }, modifier = Modifier.fillMaxWidth())
        }

        GlassCard(hazeState = hazeState, modifier = Modifier.fillMaxWidth()) {
            Text("Security & Linked Devices", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Spacer(Modifier.height(4.dp))
            Text(vm.deviceKeySummary(), color = Color.White.copy(alpha = 0.78f), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassButton("Linked Devices", onClick = {
                    vm.refreshLinkedDevices()
                    showDevicesDialog = true
                }, modifier = Modifier.weight(1f))
                GlassButton("Link QR (60s)", icon = Icons.Rounded.QrCode2, onClick = { vm.generatePairingQr() }, modifier = Modifier.weight(1f))
            }
        }

        GlassCard(hazeState = hazeState, modifier = Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassButton("Settings", onClick = { showSettingsDialog = true }, modifier = Modifier.weight(1f))
                GlassButton("Staff View", icon = Icons.Rounded.SwapHoriz, containerColor = Color.White.copy(alpha = 0.16f), contentColor = Color.White, onClick = onSwitchToStaff, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassButton("App Update", icon = Icons.Rounded.SystemUpdate, onClick = onOpenUpdate, modifier = Modifier.weight(1f))
                GlassButton("Logout", containerColor = BrandError.copy(alpha = 0.75f), contentColor = Color.White, onClick = onLogout, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
            Text("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(16.dp))
    }

    pairingQr?.let { qr ->
        val bmp = remember(qr) { PdfBillGenerator(context).createQrBitmap(qr, 512) }
        AlertDialog(
            onDismissRequest = { vm.clearPairingQr() },
            containerColor = Color(0xFF0A1D42),
            titleContentColor = Color.White,
            textContentColor = Color.White,
            title = { Text("Device Pairing QR (60s Single-Use)") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(bitmap = bmp.asImageBitmap(), contentDescription = "Pairing QR", modifier = Modifier.size(220.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Naye phone me AMBAY Billing kholkar ye QR scan karo.", style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = { TextButton(onClick = { vm.clearPairingQr() }) { Text("Done", color = BrandCyan) } }
        )
    }

    if (showDevicesDialog) {
        AlertDialog(
            onDismissRequest = { showDevicesDialog = false },
            containerColor = Color(0xFF0A1D42),
            titleContentColor = Color.White,
            textContentColor = Color.White,
            title = { Text("Linked Devices") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (devices.isEmpty()) Text("Koi linked device nahi mila.")
                    devices.forEach { d ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${d.name} (${d.platform})", fontWeight = FontWeight.Bold)
                                Text("${d.status} • ${d.connection}", color = Color.White.copy(alpha = 0.7f))
                            }
                            if (d.status == "active") {
                                TextButton(onClick = { vm.revokeLinkedDevice(d.kid) }) {
                                    Text("Revoke", color = BrandError)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDevicesDialog = false }) { Text("Close", color = BrandCyan) } }
        )
    }

    if (showSettingsDialog) {
        var sName by remember { mutableStateOf(shop.name) }
        var sPh by remember { mutableStateOf(shop.ph) }
        var sAddr by remember { mutableStateOf(shop.addr) }
        var sGst by remember { mutableStateOf(shop.gst) }
        var staffPwd by remember { mutableStateOf("") }
        var staffBio by remember { mutableStateOf(false) }
        var baseUrl by remember { mutableStateOf(BuildConfig.BASE_URL) }

        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            containerColor = Color(0xFF0A1D42),
            titleContentColor = Color.White,
            textContentColor = Color.White,
            title = { Text("Shop & Security Settings") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(sName, { sName = it }, label = { Text("Shop Name") }, colors = glassTextFieldColors())
                    OutlinedTextField(sPh, { sPh = it }, label = { Text("Phones") }, colors = glassTextFieldColors())
                    OutlinedTextField(sAddr, { sAddr = it }, label = { Text("Address") }, colors = glassTextFieldColors())
                    OutlinedTextField(sGst, { sGst = it }, label = { Text("GSTIN") }, colors = glassTextFieldColors())
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Staff Biometric Unlock")
                        Switch(checked = staffBio, onCheckedChange = {
                            staffBio = it
                            vm.setStaffBiometric(it)
                        })
                    }
                    OutlinedTextField(staffPwd, { staffPwd = it }, label = { Text("New Staff Password (optional)") }, visualTransformation = PasswordVisualTransformation(), colors = glassTextFieldColors())
                    OutlinedTextField(baseUrl, { baseUrl = it }, label = { Text("Server Base URL (Owner)") }, colors = glassTextFieldColors())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.updateShopInfo(shop.copy(name = sName, ph = sPh, addr = sAddr, gst = sGst))
                    if (staffPwd.isNotBlank()) vm.setStaffPassword(staffPwd)
                    if (baseUrl.isNotBlank()) vm.setHiddenBaseUrl(baseUrl)
                    showSettingsDialog = false
                }) { Text("Save", color = BrandCyan) }
            },
            dismissButton = { TextButton(onClick = { showSettingsDialog = false }) { Text("Cancel", color = Color.White) } }
        )
    }

    if (showDriveTokenDialog) {
        var oauthToken by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showDriveTokenDialog = false },
            containerColor = Color(0xFF0A1D42),
            titleContentColor = Color.White,
            textContentColor = Color.White,
            title = { Text("Google Drive Backup") },
            text = {
                Column {
                    Text("Google Drive OAuth Access Token (drive.file scope) daalo:")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(oauthToken, { oauthToken = it }, label = { Text("Bearer Token") }, colors = glassTextFieldColors())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (oauthToken.isNotBlank()) vm.uploadDriveSnapshot(oauthToken.trim())
                    showDriveTokenDialog = false
                }) { Text("Upload Snapshot", color = BrandCyan) }
            },
            dismissButton = { TextButton(onClick = { showDriveTokenDialog = false }) { Text("Cancel", color = Color.White) } }
        )
    }
}

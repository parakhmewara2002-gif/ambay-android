package com.ambay.billing.ui.staff

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ambay.billing.core.Item
import com.ambay.billing.core.MoneyEngine
import com.ambay.billing.core.WSale
import com.ambay.billing.ui.auth.glassTextFieldColors
import com.ambay.billing.ui.theme.BrandCyan
import com.ambay.billing.ui.theme.BrandError
import com.ambay.billing.ui.theme.BrandSuccess
import com.ambay.billing.ui.theme.BrandWarning
import com.ambay.billing.ui.theme.GlassButton
import com.ambay.billing.ui.theme.GlassCard
import com.ambay.billing.ui.theme.LottieEmptyState
import dev.chrisbanes.haze.HazeState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StaffPrintTab(
    vm: StaffViewModel,
    hazeState: HazeState
) {
    val context = LocalContext.current
    val sales by vm.wsales.collectAsState()
    var filterDays by remember { mutableStateOf(1) } // 1=Today, 7=7 days
    var query by remember { mutableStateOf("") }
    var selectedSale by remember { mutableStateOf<WSale?>(null) }
    var showPrinterPicker by remember { mutableStateOf<WSale?>(null) }

    val cutoff = remember(filterDays) {
        System.currentTimeMillis() - filterDays * 86_400_000L
    }
    val filtered = remember(sales, cutoff, query) {
        val q = MoneyEngine.normName(query)
        sales.filter { s ->
            s.date >= cutoff && (q.isEmpty() || MoneyEngine.normName("${s.no} ${s.cust.name}").contains(q))
        }
    }
    val dtFmt = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale("en", "IN")) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = filterDays == 1, onClick = { filterDays = 1 }, label = { Text("Aaj") })
            FilterChip(selected = filterDays == 7, onClick = { filterDays = 7 }, label = { Text("7 Din") })
        }
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Bill No ya Customer search karo") },
            colors = glassTextFieldColors(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))

        if (filtered.isEmpty()) {
            LottieEmptyState("Koi recent sale nahi mili")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.id }, contentType = { "sale_row" }) { sale ->
                    GlassCard(
                        hazeState = hazeState,
                        cornerRadius = 18.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem()
                            .clickable { selectedSale = sale }
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(sale.no, color = Color.White, fontWeight = FontWeight.Bold)
                                Text(dtFmt.format(Date(sale.date)), color = Color.White.copy(alpha = 0.72f))
                                if (sale.cust.name.isNotBlank()) Text(sale.cust.name, color = BrandCyan)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(MoneyEngine.fmtINR(sale.tot.total), color = BrandCyan, fontWeight = FontWeight.Bold)
                                Text("${sale.tot.qty} items • ${sale.pay}", color = Color.White.copy(alpha = 0.75f))
                            }
                        }
                    }
                }
            }
        }
    }

    selectedSale?.let { sale ->
        AlertDialog(
            onDismissRequest = { selectedSale = null },
            containerColor = Color(0xFF0A1D42),
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.9f),
            title = { Text("Receipt Preview • ${sale.no}") },
            text = {
                Column {
                    Text("AMBAY MOBILE ACCESSORIES", fontWeight = FontWeight.Bold)
                    Text(dtFmt.format(Date(sale.date)), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    sale.lines.forEach { l ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${l.name} x${l.qty}", modifier = Modifier.weight(1f))
                            Text(MoneyEngine.fmtINR(l.total), color = BrandCyan)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("TOTAL (${sale.pay}): ${MoneyEngine.fmtINR(sale.tot.total)}", fontWeight = FontWeight.Bold)
                    Text("Dhanyavaad! Visit again.", color = Color.White.copy(alpha = 0.7f))
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { showPrinterPicker = sale }) {
                        Icon(Icons.Rounded.Bluetooth, null, tint = BrandCyan)
                        Spacer(Modifier.width(4.dp))
                        Text("BT Print", color = BrandCyan)
                    }
                    TextButton(onClick = { vm.printSalePdf(context, sale) }) {
                        Icon(Icons.Rounded.Print, null, tint = Color.White)
                        Spacer(Modifier.width(4.dp))
                        Text("PDF Print", color = Color.White)
                    }
                    TextButton(onClick = { vm.shareSalePdf(sale) }) {
                        Icon(Icons.Rounded.Share, null, tint = Color.White)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedSale = null }) { Text("Band Karo", color = Color.White.copy(alpha = 0.7f)) }
            }
        )
    }

    showPrinterPicker?.let { sale ->
        val printers = remember { vm.bondedPrinters() }
        var widthMm by remember { mutableStateOf(58) }
        AlertDialog(
            onDismissRequest = { showPrinterPicker = null },
            containerColor = Color(0xFF0A1D42),
            titleContentColor = Color.White,
            textContentColor = Color.White,
            title = { Text("Bluetooth Thermal Printer") },
            text = {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = widthMm == 58, onClick = { widthMm = 58 }, label = { Text("58mm") })
                        FilterChip(selected = widthMm == 80, onClick = { widthMm = 80 }, label = { Text("80mm") })
                    }
                    Spacer(Modifier.height(8.dp))
                    if (printers.isEmpty()) {
                        Text("Koi paired Bluetooth printer nahi mila. Phone Settings > Bluetooth me printer pair karo.")
                    } else {
                        printers.forEach { p ->
                            TextButton(onClick = {
                                vm.printToBluetooth(p.mac, p.name, widthMm, sale)
                                showPrinterPicker = null
                            }) {
                                Text("${p.name} (${p.mac})", color = BrandCyan)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showPrinterPicker = null }) { Text("Cancel", color = Color.White) } }
        )
    }
}

@Composable
fun StaffSearchTab(
    vm: StaffViewModel,
    hazeState: HazeState,
    onGoToCart: () -> Unit
) {
    val query by vm.searchQuery.collectAsState()
    val results by vm.searchResults.collectAsState()
    val recent by vm.recentSearches.collectAsState()
    var inStockOnly by remember { mutableStateOf(false) }
    var warrantyOnly by remember { mutableStateOf(false) }
    var detailItem by remember { mutableStateOf<Item?>(null) }

    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val spoken = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) vm.updateSearch(spoken)
        }
    }

    val filtered = remember(results, inStockOnly, warrantyOnly) {
        results.filter {
            (!inStockOnly || !it.tracked || (it.qty ?: 0) > 0) &&
                (!warrantyOnly || it.ws == "W")
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { vm.updateSearch(it) },
            label = { Text("Item, model, code search karo...") },
            colors = glassTextFieldColors(),
            trailingIcon = {
                IconButton(onClick = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    }
                    runCatching { voiceLauncher.launch(intent) }
                }) {
                    Icon(Icons.Rounded.Mic, contentDescription = "Voice Search", tint = BrandCyan)
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = inStockOnly, onClick = { inStockOnly = !inStockOnly }, label = { Text("In Stock") })
            FilterChip(selected = warrantyOnly, onClick = { warrantyOnly = !warrantyOnly }, label = { Text("Warranty (W)") })
        }
        if (recent.isNotEmpty() && query.isBlank()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(recent) { r ->
                    FilterChip(selected = false, onClick = { vm.updateSearch(r) }, label = { Text(r) })
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered, key = { it.id }, contentType = { "search_item" }) { item ->
                GlassCard(
                    hazeState = hazeState,
                    cornerRadius = 18.dp,
                    modifier = Modifier.fillMaxWidth().animateItem().clickable { detailItem = item }
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.name, color = Color.White, fontWeight = FontWeight.Bold)
                            Text("${item.cat} • ${item.code}", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(MoneyEngine.fmtINR(item.price), color = BrandCyan, fontWeight = FontWeight.Bold)
                            Text("Stock: ${if (item.tracked) item.qty ?: 0 else "∞"}", color = Color.White.copy(alpha = 0.78f))
                        }
                    }
                }
            }
        }
    }

    detailItem?.let { it ->
        AlertDialog(
            onDismissRequest = { detailItem = null },
            containerColor = Color(0xFF0A1D42),
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.9f),
            title = { Text(it.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (it.model.isNotBlank()) Text("Model: ${it.model}")
                    Text("Price: ${MoneyEngine.fmtINR(it.price)}")
                    val md = MoneyEngine.maxDiscOf(it)
                    val min = MoneyEngine.minSellOf(it)
                    Text("Max Discount: ${md?.let { m -> MoneyEngine.fmtINR(m) } ?: "No limit"}")
                    Text("Min Price: ${min?.let { m -> MoneyEngine.fmtINR(m) } ?: "—"}")
                    Text("Warranty: ${it.ws} ${MoneyEngine.warLabel(it.war)}")
                    Text("Stock: ${if (it.tracked) it.qty ?: 0 else "Untracked"}")
                }
            },
            confirmButton = {
                GlassButton("Cart me daalo", onClick = {
                    vm.addToCart(it)
                    detailItem = null
                    onGoToCart()
                })
            },
            dismissButton = {
                TextButton(onClick = { detailItem = null }) { Text("Band Karo", color = Color.White.copy(alpha = 0.7f)) }
            }
        )
    }
}

@Composable
fun StaffDatabaseTab(
    vm: StaffViewModel,
    hazeState: HazeState
) {
    val items by vm.items.collectAsState()
    val wsales by vm.wsales.collectAsState()
    val pending by vm.pendingCount.collectAsState()
    val syncing by vm.isSyncing.collectAsState()
    var showCatalog by remember { mutableStateOf(true) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = showCatalog, onClick = { showCatalog = true }, label = { Text("Catalog (${items.size})") })
                FilterChip(selected = !showCatalog, onClick = { showCatalog = false }, label = { Text("Sales (${wsales.size})") })
            }
            IconButton(onClick = { vm.syncNow() }, enabled = !syncing) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Sync", tint = BrandCyan)
            }
        }
        Text("Pending sync: $pending", color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))

        if (showCatalog) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items, key = { it.id }, contentType = { "db_item" }) { item ->
                    val qty = item.qty ?: 0
                    val badgeColor = when {
                        !item.tracked -> Color.White.copy(alpha = 0.6f)
                        qty <= 0 -> BrandError
                        qty <= (item.rl ?: 3) -> BrandWarning
                        else -> BrandSuccess
                    }
                    GlassCard(hazeState = hazeState, cornerRadius = 16.dp, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(item.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                                Text("${item.cat} • ${item.code}", color = Color.White.copy(alpha = 0.7f))
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(MoneyEngine.fmtINR(item.price), color = BrandCyan, fontWeight = FontWeight.Bold)
                                Text(if (item.tracked) "Qty: $qty" else "Untracked", color = badgeColor, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(wsales, key = { it.id }, contentType = { "db_sale" }) { s ->
                    GlassCard(hazeState = hazeState, cornerRadius = 16.dp, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text(s.no, color = Color.White, fontWeight = FontWeight.Bold)
                                Text("${s.tot.qty} items • ${s.pay}", color = Color.White.copy(alpha = 0.72f))
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(MoneyEngine.fmtINR(s.tot.total), color = BrandCyan, fontWeight = FontWeight.Bold)
                                Icon(
                                    imageVector = when (s.sync.state) {
                                        "synced" -> Icons.Rounded.CheckCircle
                                        "conflict", "failed" -> Icons.Rounded.Warning
                                        else -> Icons.Rounded.CloudUpload
                                    },
                                    contentDescription = s.sync.state,
                                    tint = when (s.sync.state) {
                                        "synced" -> BrandSuccess
                                        "conflict", "failed" -> BrandError
                                        else -> BrandWarning
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

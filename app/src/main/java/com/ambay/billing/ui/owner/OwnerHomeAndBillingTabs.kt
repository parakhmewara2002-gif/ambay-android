package com.ambay.billing.ui.owner

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ambay.billing.core.BillLine
import com.ambay.billing.core.Item
import com.ambay.billing.core.MoneyEngine
import com.ambay.billing.data.repository.SaleCartLineInput
import com.ambay.billing.hardware.BarcodeScannerDialog
import com.ambay.billing.ui.auth.glassTextFieldColors
import com.ambay.billing.ui.theme.AnimatedMoneyText
import com.ambay.billing.ui.theme.BrandCyan
import com.ambay.billing.ui.theme.BrandError
import com.ambay.billing.ui.theme.BrandSuccess
import com.ambay.billing.ui.theme.BrandWarning
import com.ambay.billing.ui.theme.GlassButton
import com.ambay.billing.ui.theme.GlassCard
import dev.chrisbanes.haze.HazeState
import java.util.Calendar

@Composable
fun OwnerHomeTab(
    vm: OwnerViewModel,
    hazeState: HazeState,
    onNavigateTab: (Int) -> Unit
) {
    val items by vm.items.collectAsState()
    val bills by vm.bills.collectAsState()
    val wsales by vm.wsales.collectAsState()
    val pending by vm.pendingCount.collectAsState()

    val todayStart = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    val todayBills = remember(bills, todayStart) { bills.filter { it.date >= todayStart && it.status != "cancelled" } }
    val todayWSales = remember(wsales, todayStart) { wsales.filter { it.date >= todayStart && it.voidInfo == null } }
    val todaySalesPaise = todayBills.sumOf { it.tot.total } + todayWSales.sumOf { it.tot.total }
    val todayProfitPaise = todayBills.sumOf { it.tot.profit } + todayWSales.sumOf { s ->
        s.tot.total - s.lines.sumOf { l ->
            val cost = items.firstOrNull { it.id == l.itemId }?.cost ?: 0L
            cost * l.qty
        }
    }
    val lowStock = remember(items) { MoneyEngine.reorderList(items, 3) }

    // 7-day sales totals for Compose Canvas chart
    val sevenDayTotals = remember(bills, wsales, todayStart) {
        (6 downTo 0).map { daysAgo ->
            val start = todayStart - daysAgo * 86_400_000L
            val end = start + 86_400_000L
            val bSum = bills.filter { it.date in start until end && it.status != "cancelled" }.sumOf { it.tot.total }
            val wSum = wsales.filter { it.date in start until end && it.voidInfo == null }.sumOf { it.tot.total }
            (bSum + wSum).toFloat()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            GlassCard(hazeState = hazeState, modifier = Modifier.weight(1f)) {
                Text("Aaj ki Sale", color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                AnimatedMoneyText(paise = todaySalesPaise, color = BrandCyan)
                Text("${todayBills.size + todayWSales.size} bills/sales", color = Color.White.copy(alpha = 0.7f))
            }
            GlassCard(hazeState = hazeState, modifier = Modifier.weight(1f)) {
                Text("Aaj ka Profit", color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                AnimatedMoneyText(paise = todayProfitPaise, color = BrandSuccess)
                Text("Pending sync: $pending", color = Color.White.copy(alpha = 0.7f))
            }
        }

        GlassCard(hazeState = hazeState, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("7-Day Sales Trend", style = MaterialTheme.typography.titleMedium, color = Color.White)
                IconButton(onClick = { vm.syncNow() }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Sync", tint = BrandCyan)
                }
            }
            Spacer(Modifier.height(10.dp))
            Canvas(modifier = Modifier.fillMaxWidth().height(110.dp)) {
                val maxVal = sevenDayTotals.maxOrNull()?.coerceAtLeast(1f) ?: 1f
                val barW = size.width / 11f
                sevenDayTotals.forEachIndexed { idx, v ->
                    val hRatio = (v / maxVal).coerceIn(0.06f, 1f)
                    val barH = size.height * hRatio
                    val x = idx * (barW * 1.55f) + barW * 0.4f
                    drawRoundRect(
                        color = if (idx == 6) BrandCyan else Color.White.copy(alpha = 0.35f),
                        topLeft = Offset(x, size.height - barH),
                        size = Size(barW, barH),
                        cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            GlassButton("New Paper Bill", icon = Icons.Rounded.Add, onClick = { onNavigateTab(1) }, modifier = Modifier.weight(1f))
            GlassButton("Items (${items.count { !it.deleted }})", containerColor = Color.White.copy(alpha = 0.16f), contentColor = Color.White, onClick = { onNavigateTab(2) }, modifier = Modifier.weight(1f))
        }

        GlassCard(hazeState = hazeState, modifier = Modifier.fillMaxWidth()) {
            Text("Kam Stock Alert (${lowStock.size})", style = MaterialTheme.typography.titleMedium, color = BrandWarning)
            Spacer(Modifier.height(6.dp))
            if (lowStock.isEmpty()) {
                Text("Sabhi tracked items stock me hain.", color = Color.White.copy(alpha = 0.75f))
            } else {
                lowStock.take(6).forEach { r ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(r.name, color = Color.White, modifier = Modifier.weight(1f))
                        Text("Qty: ${r.qty} (min ${r.limit})", color = if (r.qty <= 0) BrandError else BrandWarning, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun OwnerBillingTab(
    vm: OwnerViewModel,
    hazeState: HazeState
) {
    val context = LocalContext.current
    val allItems by vm.items.collectAsState()
    val activeItems = remember(allItems) { allItems.filter { !it.deleted } }
    val verdict by vm.verdict.collectAsState()

    var subTab by remember { mutableIntStateOf(0) } // 0=Paper Bill, 1=Without Bill, 2=Verifier
    var showScannerForCart by remember { mutableStateOf(false) }
    var showScannerForVerify by remember { mutableStateOf(false) }

    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) vm.verifyPdfUri(context, uri)
    }

    val cartItems = remember { mutableStateListOf<Pair<Item, Int>>() }
    var custName by remember { mutableStateOf("") }
    var custPhone by remember { mutableStateOf("") }
    var pay by remember { mutableStateOf("Cash") }
    var gstPctStr by remember { mutableStateOf("18") }
    var gstMode by remember { mutableStateOf("exc") }
    var taxType by remember { mutableStateOf("intra") }
    var paperSize by remember { mutableStateOf("A4") }
    var warLang by remember { mutableStateOf("en") }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = subTab == 0, onClick = { subTab = 0 }, label = { Text("Paper Bill") })
            FilterChip(selected = subTab == 1, onClick = { subTab = 1 }, label = { Text("Without Bill") })
            FilterChip(selected = subTab == 2, onClick = { subTab = 2 }, label = { Text("Verifier") })
        }
        Spacer(Modifier.height(8.dp))

        if (subTab == 2) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassCard(hazeState = hazeState, modifier = Modifier.fillMaxWidth()) {
                    Text("Anti-Fake Bill Verifier", style = MaterialTheme.typography.titleLarge, color = Color.White)
                    Spacer(Modifier.height(6.dp))
                    Text("Bill ka QR scan karo ya PDF import karke offline signature check karo.", color = Color.White.copy(alpha = 0.78f))
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassButton("Scan Bill QR", icon = Icons.Rounded.QrCodeScanner, onClick = { showScannerForVerify = true }, modifier = Modifier.weight(1f))
                        GlassButton("Import PDF", icon = Icons.Rounded.PictureAsPdf, containerColor = Color.White.copy(alpha = 0.16f), contentColor = Color.White, onClick = { pdfLauncher.launch(arrayOf("application/pdf")) }, modifier = Modifier.weight(1f))
                    }
                }
                verdict?.let { v ->
                    val tint = when (v.level) {
                        "ok" -> BrandSuccess.copy(alpha = 0.25f)
                        "bad" -> BrandError.copy(alpha = 0.28f)
                        else -> BrandWarning.copy(alpha = 0.25f)
                    }
                    GlassCard(hazeState = hazeState, tintColor = tint, modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.VerifiedUser, null, tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text(v.label, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(6.dp))
                        v.no?.let { Text("Bill No: $it", color = Color.White) }
                        v.kid?.let { Text("Key ID: $it (${v.keyTrust})", color = Color.White.copy(alpha = 0.8f)) }
                        v.notes.forEach { n -> Text("• $n", color = Color.White.copy(alpha = 0.88f)) }
                    }
                }
            }
        } else {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassButton("Barcode Scan", icon = Icons.Rounded.QrCodeScanner, onClick = { showScannerForCart = true }, modifier = Modifier.weight(1f))
                }

                // Quick item picker
                var search by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text("Item search karke add karo") },
                    colors = glassTextFieldColors(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (search.isNotBlank()) {
                    val matches = activeItems.filter {
                        MoneyEngine.normName("${it.name} ${it.code} ${it.model}").contains(MoneyEngine.normName(search))
                    }.take(5)
                    matches.forEach { it ->
                        GlassCard(
                            hazeState = hazeState,
                            cornerRadius = 14.dp,
                            modifier = Modifier.fillMaxWidth().clickable {
                                val idx = cartItems.indexOfFirst { p -> p.first.id == it.id }
                                if (idx >= 0) cartItems[idx] = it to (cartItems[idx].second + 1)
                                else cartItems.add(it to 1)
                                search = ""
                            }
                        ) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(it.name, color = Color.White)
                                Text(MoneyEngine.fmtINR(it.price), color = BrandCyan, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                cartItems.forEachIndexed { idx, (it, q) ->
                    GlassCard(hazeState = hazeState, cornerRadius = 16.dp, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(it.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                                Text("${MoneyEngine.fmtINR(it.price)} x $q", color = BrandCyan)
                            }
                            Row {
                                FilterChip(selected = false, onClick = {
                                    if (q <= 1) cartItems.removeAt(idx) else cartItems[idx] = it to (q - 1)
                                }, label = { Text("-") })
                                Spacer(Modifier.width(4.dp))
                                FilterChip(selected = false, onClick = { cartItems[idx] = it to (q + 1) }, label = { Text("+") })
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(custName, { custName = it }, label = { Text("Customer Name") }, colors = glassTextFieldColors(), singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(custPhone, { custPhone = it }, label = { Text("Phone") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), colors = glassTextFieldColors(), singleLine = true, modifier = Modifier.weight(1f))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Cash", "UPI", "Card", "Credit").forEach { p ->
                        FilterChip(selected = pay == p, onClick = { pay = p }, label = { Text(p) })
                    }
                }

                if (subTab == 0) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(gstPctStr, { gstPctStr = it }, label = { Text("GST %") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = glassTextFieldColors(), singleLine = true, modifier = Modifier.weight(1f))
                        FilterChip(selected = gstMode == "exc", onClick = { gstMode = "exc" }, label = { Text("Exc") })
                        FilterChip(selected = gstMode == "inc", onClick = { gstMode = "inc" }, label = { Text("Inc") })
                        FilterChip(selected = taxType == "intra", onClick = { taxType = "intra" }, label = { Text("CGST/SGST") })
                        FilterChip(selected = taxType == "inter", onClick = { taxType = "inter" }, label = { Text("IGST") })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("A4", "A5", "4X6").forEach { ps ->
                            FilterChip(selected = paperSize == ps, onClick = { paperSize = ps }, label = { Text(ps) })
                        }
                        listOf("en" to "EN", "hi" to "HI", "both" to "Both").forEach { (k, lbl) ->
                            FilterChip(selected = warLang == k, onClick = { warLang = k }, label = { Text(lbl) })
                        }
                    }
                }

                GlassButton(
                    text = if (subTab == 0) "Generate Signed GST Bill & PDF" else "Save Without-Bill Sale",
                    enabled = cartItems.isNotEmpty(),
                    onClick = {
                        if (subTab == 0) {
                            val gstBp = ((gstPctStr.toDoubleOrNull() ?: 0.0) * 100).toInt().coerceIn(0, 10000)
                            val lines = cartItems.map { (it, q) ->
                                BillLine(
                                    itemId = it.id,
                                    name = it.name,
                                    sn = it.sn,
                                    model = it.model,
                                    qty = q,
                                    rate = it.price,
                                    cost = it.cost,
                                    op = it.price,
                                    ws = it.ws,
                                    war = it.war,
                                    swar = it.swar,
                                    gstBp = it.gst ?: gstBp
                                )
                            }
                            vm.createPaperBill(lines, "amt", 0L, gstBp, gstMode, taxType, pay, custName, custPhone, warLang, paperSize) { bill, _ ->
                                cartItems.clear()
                                vm.shareBillPdf(bill, paperSize)
                            }
                        } else {
                            val lines = cartItems.map { (it, q) -> SaleCartLineInput(it.id, q, it.price) }
                            vm.createWithoutBillSale(lines, pay, custName, custPhone)
                            cartItems.clear()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showScannerForCart) {
        BarcodeScannerDialog(
            title = "Item Barcode Scan Karo",
            onDismiss = { showScannerForCart = false },
            onResult = { raw ->
                showScannerForCart = false
                val hit = MoneyEngine.matchCode(activeItems, raw).firstOrNull()
                if (hit != null) {
                    val idx = cartItems.indexOfFirst { it.first.id == hit.id }
                    if (idx >= 0) cartItems[idx] = hit to (cartItems[idx].second + 1)
                    else cartItems.add(hit to 1)
                }
            }
        )
    }

    if (showScannerForVerify) {
        BarcodeScannerDialog(
            title = "Bill Anti-Fake QR Scan Karo",
            onDismiss = { showScannerForVerify = false },
            onResult = { raw ->
                showScannerForVerify = false
                vm.verifyQrCode(raw)
            }
        )
    }
}

package com.ambay.billing.ui.staff

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ambay.billing.core.MoneyEngine
import com.ambay.billing.hardware.BarcodeScannerView
import com.ambay.billing.ui.auth.glassTextFieldColors
import com.ambay.billing.ui.theme.AnimatedMoneyText
import com.ambay.billing.ui.theme.BrandCyan
import com.ambay.billing.ui.theme.BrandError
import com.ambay.billing.ui.theme.BrandSuccess
import com.ambay.billing.ui.theme.GlassButton
import com.ambay.billing.ui.theme.GlassCard
import com.ambay.billing.ui.theme.LottieSuccessBadge
import dev.chrisbanes.haze.HazeState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffScanTab(
    vm: StaffViewModel,
    hazeState: HazeState
) {
    val scan by vm.scanState.collectAsState()
    val cart by vm.cart.collectAsState()
    val lastSaved by vm.lastSavedSale.collectAsState()
    var showCartSheet by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Compact ~38% viewfinder
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.38f)
        ) {
            BarcodeScannerView(
                isActive = scan.cameraActive,
                modifier = Modifier.fillMaxSize(),
                onDecoded = { vm.onBarcodeDecoded(it) }
            )
            // Always visible counter chip
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.58f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    "Scan hue: ${cart.sumOf { it.qty }} items",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // Slide-up glass result card when camera paused after decode
        AnimatedVisibility(
            visible = scan.matchedItem != null || scan.notFoundCode != null,
            enter = slideInVertically { it / 2 },
            exit = slideOutVertically { it / 2 }
        ) {
            val item = scan.matchedItem
            if (item != null) {
                GlassCard(
                    hazeState = hazeState,
                    tintColor = BrandSuccess.copy(alpha = 0.22f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(item.name, style = MaterialTheme.typography.titleMedium, color = Color.White)
                            if (item.model.isNotBlank()) Text("Model: ${item.model}", color = Color.White.copy(alpha = 0.78f))
                            Text(
                                "Price: ${MoneyEngine.fmtINR(item.price)}  |  Warranty: ${item.ws}",
                                color = BrandCyan,
                                fontWeight = FontWeight.SemiBold
                            )
                            val minSell = MoneyEngine.minSellOf(item)
                            if (minSell != null) {
                                Text(
                                    "Max Disc: ${MoneyEngine.fmtINR(MoneyEngine.maxDiscOf(item)!!)} | Min: ${MoneyEngine.fmtINR(minSell)}",
                                    color = Color.White.copy(alpha = 0.8f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Text(
                                "Stock: ${if (item.tracked) (item.qty ?: 0).toString() else "Untracked"}",
                                color = Color.White.copy(alpha = 0.75f)
                            )
                        }
                        LottieSuccessBadge()
                    }
                    Spacer(Modifier.height(10.dp))
                    GlassButton(
                        text = "Scan Next Item",
                        icon = Icons.Rounded.QrCodeScanner,
                        onClick = { vm.resumeScanner() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else if (scan.notFoundCode != null) {
                GlassCard(
                    hazeState = hazeState,
                    tintColor = BrandError.copy(alpha = 0.30f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Ye code kisi item me nahi mila", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Text("Code: ${scan.notFoundCode}", color = Color.White.copy(alpha = 0.85f))
                    Spacer(Modifier.height(10.dp))
                    GlassButton(
                        text = "Scan Next Item",
                        icon = Icons.Rounded.QrCodeScanner,
                        onClick = { vm.resumeScanner() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Scanned list always visible below viewfinder
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(cart, key = { it.item.id }, contentType = { "cart_line" }) { line ->
                GlassCard(
                    hazeState = hazeState,
                    cornerRadius = 18.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItem()
                        .clickable { showCartSheet = true }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(line.item.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${MoneyEngine.fmtINR(line.unitSellPaise)} x ${line.qty}",
                                color = Color.White.copy(alpha = 0.75f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { vm.updateQty(line.item.id, -1) }) {
                                Icon(Icons.Rounded.Remove, contentDescription = "Less", tint = Color.White)
                            }
                            Text("${line.qty}", color = Color.White, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { vm.updateQty(line.item.id, +1) }) {
                                Icon(Icons.Rounded.Add, contentDescription = "More", tint = Color.White)
                            }
                        }
                        Text(
                            MoneyEngine.fmtINR(line.lineTotalPaise),
                            color = BrandCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        if (cart.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            val totalPaise = cart.sumOf { it.lineTotalPaise }
            GlassButton(
                text = "Cart Kholo • ${MoneyEngine.fmtINR(totalPaise)}",
                icon = Icons.Rounded.ShoppingCart,
                onClick = { showCartSheet = true },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showCartSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var custName by remember { mutableStateOf("") }
        var custPhone by remember { mutableStateOf("") }
        var selectedPay by remember { mutableStateOf("Cash") }
        val payments = remember { listOf("Cash", "UPI", "Card", "Credit") }

        ModalBottomSheet(
            onDismissRequest = { showCartSheet = false },
            sheetState = sheetState,
            containerColor = Color(0xFF0A1D42),
            contentColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text("Cart & Checkout", style = MaterialTheme.typography.titleLarge, color = Color.White)
                Spacer(Modifier.height(10.dp))

                LazyColumn(modifier = Modifier.height(220.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(cart, key = { it.item.id }) { line ->
                        var discInput by remember(line.item.id) {
                            mutableStateOf(if (line.discountPerUnitPaise > 0) (line.discountPerUnitPaise / 100.0).toString() else "")
                        }
                        GlassCard(cornerRadius = 16.dp, modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(line.item.name, color = Color.White, fontWeight = FontWeight.Bold)
                                    Text(
                                        "Price: ${MoneyEngine.fmtINR(line.item.price)} | Min: ${line.minPricePerUnitPaise?.let { MoneyEngine.fmtINR(it) } ?: "No limit"}",
                                        color = Color.White.copy(alpha = 0.72f),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                Text(MoneyEngine.fmtINR(line.lineTotalPaise), color = BrandCyan, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                IconButton(onClick = { vm.updateQty(line.item.id, -1) }) {
                                    Icon(Icons.Rounded.Remove, null, tint = Color.White)
                                }
                                Text("${line.qty}", color = Color.White, fontWeight = FontWeight.Bold)
                                IconButton(onClick = { vm.updateQty(line.item.id, +1) }) {
                                    Icon(Icons.Rounded.Add, null, tint = Color.White)
                                }
                                Spacer(Modifier.width(8.dp))
                                OutlinedTextField(
                                    value = discInput,
                                    onValueChange = {
                                        discInput = it
                                        vm.setLineDiscountRupees(line.item.id, it.ifBlank { "0" })
                                    },
                                    label = { Text("Disc/unit ₹") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    colors = glassTextFieldColors(),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = custName,
                        onValueChange = { custName = it },
                        label = { Text("Customer Name") },
                        colors = glassTextFieldColors(),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = custPhone,
                        onValueChange = { custPhone = it },
                        label = { Text("Phone") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        colors = glassTextFieldColors(),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    payments.forEach { p ->
                        FilterChip(
                            selected = selectedPay == p,
                            onClick = { selectedPay = p },
                            label = { Text(p) }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Total:", style = MaterialTheme.typography.titleLarge, color = Color.White)
                    AnimatedMoneyText(paise = cart.sumOf { it.lineTotalPaise }, color = BrandCyan)
                }
                Spacer(Modifier.height(12.dp))
                GlassButton(
                    text = "Save & Print",
                    onClick = {
                        vm.saveAndPrintSale(selectedPay, custName, custPhone) {
                            showCartSheet = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    lastSaved?.let { saved ->
        GlassCard(
            hazeState = hazeState,
            tintColor = BrandSuccess.copy(alpha = 0.30f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LottieSuccessBadge()
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Sale Save Ho Gayi: ${saved.no}", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Total: ${MoneyEngine.fmtINR(saved.tot.total)} (${saved.pay})", color = BrandCyan)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassButton("Share PDF", onClick = { vm.shareSalePdf(saved) }, modifier = Modifier.weight(1f))
                GlassButton("Done", containerColor = Color.White.copy(alpha = 0.2f), contentColor = Color.White, onClick = { vm.clearLastSavedSale() }, modifier = Modifier.weight(1f))
            }
        }
    }
}

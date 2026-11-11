package com.ambay.billing.ui.screens

import android.bluetooth.BluetoothDevice
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.ambay.billing.core.model.Money
import com.ambay.billing.core.model.Sale
import com.ambay.billing.core.print.BluetoothPrintManager
import com.ambay.billing.core.print.EscPosBuilder
import com.ambay.billing.core.print.PdfReceiptGenerator
import com.ambay.billing.core.sync.AmbayRepository
import com.ambay.billing.ui.components.ReceiptView
import com.ambay.billing.ui.theme.BrandAccent
import com.ambay.billing.ui.theme.BrandBlue
import com.ambay.billing.ui.theme.BrandError
import com.ambay.billing.ui.theme.BrandSuccess
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StaffPrintTab(
    repository: AmbayRepository,
    bluetoothPrintManager: BluetoothPrintManager
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var filterDays by remember { mutableStateOf(1) } // 1: Today, 7: 7 Days, 0: All
    var searchQuery by remember { mutableStateOf("") }
    var selectedSaleForPreview by remember { mutableStateOf<Sale?>(null) }
    var showPrinterDialog by remember { mutableStateOf(false) }
    var isPrintingLoading by remember { mutableStateOf(false) }
    var printStatusMessage by remember { mutableStateOf<String?>(null) }

    val salesList by repository.getRecentSales(100).collectAsState(initial = emptyList())

    val filteredSales = remember(salesList, filterDays, searchQuery) {
        val now = System.currentTimeMillis()
        val dayMs = 86400000L
        salesList.filter { sale ->
            val matchesFilter = when (filterDays) {
                1 -> (now - sale.date) <= dayMs
                7 -> (now - sale.date) <= (7 * dayMs)
                else -> true
            }
            val matchesQuery = if (searchQuery.isBlank()) true else {
                sale.no.contains(searchQuery, ignoreCase = true) ||
                sale.cust.name.contains(searchQuery, ignoreCase = true) ||
                sale.lines.any { it.name.contains(searchQuery, ignoreCase = true) }
            }
            matchesFilter && matchesQuery
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "Haliye Sales & Print",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Sale no, customer, item khojo...") },
                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Filter Chips
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = filterDays == 1,
                    onClick = { filterDays = 1 },
                    label = { Text("Aaj") }
                )
                FilterChip(
                    selected = filterDays == 7,
                    onClick = { filterDays = 7 },
                    label = { Text("Pichhle 7 Din") }
                )
                FilterChip(
                    selected = filterDays == 0,
                    onClick = { filterDays = 0 },
                    label = { Text("Sabhi") }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (filteredSales.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Koi sales nahi mili",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(filteredSales, key = { it.id }) { sale ->
                        SaleHistoryItem(
                            sale = sale,
                            onClick = { selectedSaleForPreview = sale }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }

        // Receipt Preview & Action Dialog
        if (selectedSaleForPreview != null) {
            val sale = selectedSaleForPreview!!
            AlertDialog(
                onDismissRequest = {
                    selectedSaleForPreview = null
                    printStatusMessage = null
                },
                title = {
                    Text(text = "Receipt Preview (${sale.no})", fontWeight = FontWeight.Bold)
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ReceiptView(sale = sale)

                        if (printStatusMessage != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = printStatusMessage ?: "",
                                color = BrandBlue,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                    }
                },
                confirmButton = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Bluetooth Thermal Print Button
                            Button(
                                onClick = {
                                    val savedMac = bluetoothPrintManager.getSavedPrinterMac()
                                    if (savedMac != null) {
                                        isPrintingLoading = true
                                        printStatusMessage = "Printing..."
                                        scope.launch {
                                            val is80mm = bluetoothPrintManager.is80mmPrinter()
                                            val escPosBytes = EscPosBuilder.buildSaleReceipt(sale, is80mm)
                                            val res = bluetoothPrintManager.printBytes(savedMac, escPosBytes)
                                            isPrintingLoading = false
                                            printStatusMessage = if (res.isSuccess) "Print ho gaya!" else "Printer error: ${res.exceptionOrNull()?.message}"
                                        }
                                    } else {
                                        showPrinterDialog = true
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
                            ) {
                                Icon(imageVector = Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Thermal Print", fontSize = 12.sp)
                            }

                            // Share PDF Button
                            Button(
                                onClick = {
                                    val pdfFile = PdfReceiptGenerator.generatePdf(context, sale)
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        pdfFile
                                    )
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/pdf"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Share Receipt PDF"))
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BrandAccent)
                            ) {
                                Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("PDF Share", fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        TextButton(
                            onClick = { showPrinterDialog = true },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Icon(imageVector = Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Printer Badlein (Configure)", fontSize = 12.sp)
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { selectedSaleForPreview = null }) {
                        Text("Band Karo")
                    }
                }
            )
        }

        // Bluetooth Printer Picker Dialog
        if (showPrinterDialog) {
            val paired = bluetoothPrintManager.getPairedDevices()
            AlertDialog(
                onDismissRequest = { showPrinterDialog = false },
                title = { Text("Thermal Printer Chuniye", fontWeight = FontWeight.Bold) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Phone ke Bluetooth settings me paired thermal printer select karein:",
                            fontSize = 13.sp,
                            color = Color.DarkGray
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        if (paired.isEmpty()) {
                            Text(
                                text = "Koi paired Bluetooth printer nahi mila. Phone settings me printer pair karein.",
                                color = BrandError,
                                fontSize = 13.sp
                            )
                        } else {
                            LazyColumn {
                                items(paired) { dev ->
                                    val isSaved = dev.address == bluetoothPrintManager.getSavedPrinterMac()
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                bluetoothPrintManager.savePrinterMac(dev.address)
                                                showPrinterDialog = false
                                            }
                                            .padding(vertical = 4.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isSaved) BrandBlue.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(imageVector = Icons.Default.Print, contentDescription = null, tint = BrandBlue)
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(text = dev.name ?: "Unknown Device", fontWeight = FontWeight.SemiBold)
                                                Text(text = dev.address, fontSize = 11.sp, color = Color.Gray)
                                            }
                                            if (isSaved) {
                                                Text("Selected", color = BrandSuccess, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showPrinterDialog = false }) {
                        Text("Theek Hai")
                    }
                }
            )
        }
    }
}

@Composable
fun SaleHistoryItem(sale: Sale, onClick: () -> Unit) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.ENGLISH)
    val dateStr = dateFormat.format(Date(sale.date))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(BrandBlue.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = Icons.Default.Receipt, contentDescription = null, tint = BrandBlue)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = sale.no, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = sale.pay,
                        fontSize = 11.sp,
                        color = Color.DarkGray,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFE8EAF6))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${sale.lines.size} items (${sale.tot.qty} qty) • $dateStr",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                if (sale.cust.name.isNotEmpty()) {
                    Text(
                        text = "Customer: ${sale.cust.name}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = Money.formatInr(sale.tot.total),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = BrandBlue
                )
                if (sale.void != null) {
                    Text(text = "VOID", color = BrandError, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                } else {
                    Text(
                        text = sale.syncStatus.uppercase(),
                        color = if (sale.syncStatus == "synced") BrandSuccess else BrandAccent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

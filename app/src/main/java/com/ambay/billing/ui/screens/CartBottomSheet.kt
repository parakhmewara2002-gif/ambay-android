package com.ambay.billing.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ambay.billing.core.model.Item
import com.ambay.billing.core.model.Money
import com.ambay.billing.core.model.SaleLine
import com.ambay.billing.ui.components.NumCountUp
import com.ambay.billing.ui.theme.BrandAccent
import com.ambay.billing.ui.theme.BrandBlue
import com.ambay.billing.ui.theme.BrandError
import com.ambay.billing.ui.theme.BrandSuccess

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartBottomSheet(
    sheetState: SheetState,
    cartLines: List<SaleLine>,
    itemsMap: Map<String, Item>,
    onQtyChange: (index: Int, newQty: Int) -> Unit,
    onDiscountChange: (index: Int, newDiscountPaise: Long) -> Unit,
    onRemoveLine: (index: Int) -> Unit,
    onCheckout: (custName: String, custPhone: String, payMode: String) -> Unit,
    onDismiss: () -> Unit
) {
    var custName by remember { mutableStateOf("") }
    var custPhone by remember { mutableStateOf("") }
    var payMode by remember { mutableStateOf("Cash") }

    val totalQty = cartLines.sumOf { it.qty }
    val totalOrig = cartLines.sumOf { it.gross }
    val totalDisc = cartLines.sumOf { it.disc }
    val netTotal = cartLines.sumOf { it.total }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Cart ($totalQty items)",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Without Bill",
                    color = BrandAccent,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(BrandAccent.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (cartLines.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Cart khaali hai. Barcode scan karo.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                ) {
                    itemsIndexed(cartLines) { index, line ->
                        val item = itemsMap[line.itemId]
                        CartItemRow(
                            line = line,
                            item = item,
                            onQtyPlus = { onQtyChange(index, line.qty + 1) },
                            onQtyMinus = {
                                if (line.qty > 1) {
                                    onQtyChange(index, line.qty - 1)
                                } else {
                                    onRemoveLine(index)
                                }
                            },
                            onDiscountChange = { newDisc -> onDiscountChange(index, newDisc) },
                            onDelete = { onRemoveLine(index) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Customer Details (Optional)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = custName,
                        onValueChange = { custName = it },
                        label = { Text("Customer Name (optional)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = custPhone,
                        onValueChange = { custPhone = it },
                        label = { Text("Mobile (optional)") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Payment Mode Selector
                Text(text = "Payment Mode", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val modes = listOf("Cash", "UPI", "Card", "Credit")
                    for (mode in modes) {
                        val isSelected = payMode == mode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) BrandBlue else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { payMode = mode }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = mode,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Totals Breakdown
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(text = "Total Qty:", color = Color.Gray, modifier = Modifier.weight(1f))
                    Text(text = "$totalQty", fontWeight = FontWeight.Medium)
                }
                if (totalDisc > 0) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(text = "Total Discount:", color = Color.Gray, modifier = Modifier.weight(1f))
                        Text(text = "-${Money.formatInr(totalDisc)}", color = BrandError, fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Kul Rakam:",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    NumCountUp(
                        targetPaise = netTotal,
                        style = MaterialTheme.typography.headlineMedium,
                        color = BrandBlue
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Save & Print Button
                Button(
                    onClick = {
                        onCheckout(custName, custPhone, payMode)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
                ) {
                    Icon(imageVector = Icons.Default.Print, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Save aur Print Karo (${Money.formatInr(netTotal)})",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun CartItemRow(
    line: SaleLine,
    item: Item?,
    onQtyPlus: () -> Unit,
    onQtyMinus: () -> Unit,
    onDiscountChange: (Long) -> Unit,
    onDelete: () -> Unit
) {
    var discInput by remember(line.disc) {
        mutableStateOf(if (line.disc > 0) (line.disc / 100).toString() else "")
    }
    var discError by remember { mutableStateOf(false) }

    val maxDiscPerUnit = item?.maxDiscountPaise()
    val maxAllowedLineDisc = if (maxDiscPerUnit != null) maxDiscPerUnit * line.qty else line.gross
    val minLineTotal = line.gross - maxAllowedLineDisc

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = line.name + (if (line.model.isNotEmpty()) " (${line.model})" else ""),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Rate: ${Money.formatInr(line.op)}" + (if (item?.maxDiscountPaise() != null) " (Max Disc: ₹${item.maxDiscountPaise()!! / 100}/pc)" else ""),
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete item",
                        tint = BrandError
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Qty controls
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White)
                        .border(1.dp, Color(0xFFD0D0D0), RoundedCornerShape(8.dp))
                ) {
                    IconButton(onClick = onQtyMinus, modifier = Modifier.size(32.dp)) {
                        Icon(imageVector = Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(16.dp))
                    }
                    Text(
                        text = "${line.qty}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    IconButton(onClick = onQtyPlus, modifier = Modifier.size(32.dp)) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(16.dp))
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Discount per line
                OutlinedTextField(
                    value = discInput,
                    onValueChange = { input ->
                        discInput = input
                        val rupees = input.toLongOrNull() ?: 0L
                        val paise = rupees * 100L
                        if (paise > maxAllowedLineDisc) {
                            discError = true
                        } else {
                            discError = false
                            onDiscountChange(paise)
                        }
                    },
                    label = { Text("Disc (₹)") },
                    isError = discError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Line Total
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = Money.formatInr(line.total),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = BrandBlue
                    )
                    if (line.ws == "W") {
                        Text(text = "WAR", color = BrandSuccess, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (discError) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Max discount ₹${maxAllowedLineDisc / 100} se zyada nahi de sakte (Min total: ₹${minLineTotal / 100})",
                    color = BrandError,
                    fontSize = 11.sp
                )
            }
        }
    }
}

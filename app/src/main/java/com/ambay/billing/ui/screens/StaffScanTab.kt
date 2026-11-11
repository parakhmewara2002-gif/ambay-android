package com.ambay.billing.ui.screens

import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ambay.billing.core.model.Item
import com.ambay.billing.core.model.Money
import com.ambay.billing.core.model.Sale
import com.ambay.billing.core.model.SaleLine
import com.ambay.billing.core.scan.BarcodeAnalyzer
import com.ambay.billing.core.scan.BeepManager
import com.ambay.billing.core.sync.AmbayRepository
import com.ambay.billing.ui.components.ScanViewfinder
import com.ambay.billing.ui.theme.BrandAccent
import com.ambay.billing.ui.theme.BrandBlue
import com.ambay.billing.ui.theme.BrandError
import com.ambay.billing.ui.theme.BrandSuccess
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffScanTab(
    repository: AmbayRepository,
    beepManager: BeepManager,
    onSaleCompleted: (Sale) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var isTorchOn by remember { mutableStateOf(false) }
    var cameraControl by remember { mutableStateOf<androidx.camera.core.CameraControl?>(null) }

    // Cart State
    val cartLines = remember { mutableStateListOf<SaleLine>() }
    val itemsMap = remember { mutableStateMapOf<String, Item>() }

    // Scan result card state
    var scannedResultItem by remember { mutableStateOf<Item?>(null) }
    var scannedNotFoundCode by remember { mutableStateOf<String?>(null) }

    // Bottom sheet state
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isCartOpen by remember { mutableStateOf(false) }

    val barcodeAnalyzer = remember {
        BarcodeAnalyzer { barcode ->
            scope.launch {
                beepManager.playBeepAndVibrate()
                val item = repository.findExactMatch(barcode)
                if (item != null) {
                    itemsMap[item.id] = item
                    scannedNotFoundCode = null
                    scannedResultItem = item

                    // Add 1 qty to cart
                    val existingIndex = cartLines.indexOfFirst { it.itemId == item.id }
                    if (existingIndex >= 0) {
                        val existing = cartLines[existingIndex]
                        val newQty = existing.qty + 1
                        val newGross = item.price * newQty
                        val newTotal = (newGross - existing.disc).coerceAtLeast(0L)
                        cartLines[existingIndex] = existing.copy(
                            qty = newQty,
                            gross = newGross,
                            total = newTotal
                        )
                    } else {
                        cartLines.add(
                            SaleLine(
                                itemId = item.id,
                                name = item.name,
                                sn = item.sn,
                                model = item.model,
                                qty = 1,
                                op = item.price,
                                gross = item.price,
                                disc = 0L,
                                total = item.price,
                                ws = item.ws,
                                war = ""
                            )
                        )
                    }
                } else {
                    scannedResultItem = null
                    scannedNotFoundCode = barcode
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // CameraX Viewfinder (~40% of screen height)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.42f)
            ) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }

                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }

                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                                .also {
                                    it.setAnalyzer(Executors.newSingleThreadExecutor(), barcodeAnalyzer)
                                }

                            try {
                                cameraProvider.unbindAll()
                                val camera = cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    imageAnalysis
                                )
                                cameraControl = camera.cameraControl
                            } catch (ignored: Exception) {}
                        }, ContextCompat.getMainExecutor(ctx))

                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Viewfinder Overlay
                ScanViewfinder(
                    isTorchOn = isTorchOn,
                    onTorchToggle = {
                        isTorchOn = !isTorchOn
                        cameraControl?.enableTorch(isTorchOn)
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // "Scan hue: N items" Floating Counter Tag
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Scan hue: ${cartLines.sumOf { it.qty }} items",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            // Scanned Items List below Camera (takes remaining screen space)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.58f)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                if (cartLines.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Item scan karein — cart yahan dikhegi",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(cartLines.reversed(), key = { it.itemId }) { line ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = line.name + (if (line.model.isNotEmpty()) " (${line.model})" else ""),
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = "Qty: ${line.qty} x ${Money.formatInr(line.op)}" + (if (line.disc > 0) " (Disc: -${Money.formatInr(line.disc)})" else ""),
                                            fontSize = 12.sp,
                                            color = Color.Gray
                                        )
                                    }
                                    Text(
                                        text = Money.formatInr(line.total),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = BrandBlue
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom Cart Summary Bar
                if (cartLines.isNotEmpty()) {
                    Button(
                        onClick = { isCartOpen = true },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
                    ) {
                        Icon(imageVector = Icons.Default.ShoppingBag, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Cart Dekho (${cartLines.sumOf { it.qty }} Items) — ${Money.formatInr(cartLines.sumOf { it.total })}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }

        // Slide-up Result Card on Scan Match
        AnimatedVisibility(
            visible = scannedResultItem != null,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            scannedResultItem?.let { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(BrandSuccess.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = BrandSuccess)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = item.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                if (item.model.isNotEmpty()) {
                                    Text(text = "Model: ${item.model}", fontSize = 12.sp, color = Color.Gray)
                                }
                            }
                            Text(
                                text = if (item.ws == "W") "WARRANTY" else "NO WAR",
                                color = if (item.ws == "W") BrandSuccess else Color.Gray,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Price: ${Money.formatInr(item.price)}", fontWeight = FontWeight.SemiBold)
                            Text(text = "Stock: ${item.qty} pcs", color = if (item.qty > 0) BrandBlue else BrandError)
                        }

                        if (item.maxDiscountPaise() != null) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = "Max Disc: ₹${item.maxDiscountPaise()!! / 100}", color = BrandAccent, fontSize = 12.sp)
                                Text(text = "Min Price: ₹${item.minSellingPricePaise() / 100}", color = Color.DarkGray, fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                scannedResultItem = null
                                barcodeAnalyzer.resume()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
                        ) {
                            Text(text = "Scan Next Item", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Slide-up Red Card on Code Not Found
        AnimatedVisibility(
            visible = scannedNotFoundCode != null,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = BrandError),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Ye code kisi item me nahi mila",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                    Text(
                        text = "Scanned: $scannedNotFoundCode",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            scannedNotFoundCode = null
                            barcodeAnalyzer.resume()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = BrandError)
                    ) {
                        Text("Scan Next Item", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Cart Bottom Sheet
        if (isCartOpen) {
            CartBottomSheet(
                sheetState = sheetState,
                cartLines = cartLines,
                itemsMap = itemsMap,
                onQtyChange = { index, newQty ->
                    val line = cartLines[index]
                    val item = itemsMap[line.itemId]
                    val unitPrice = item?.price ?: line.op
                    val gross = unitPrice * newQty
                    val total = (gross - line.disc).coerceAtLeast(0L)
                    cartLines[index] = line.copy(qty = newQty, gross = gross, total = total)
                },
                onDiscountChange = { index, discPaise ->
                    val line = cartLines[index]
                    val total = (line.gross - discPaise).coerceAtLeast(0L)
                    cartLines[index] = line.copy(disc = discPaise, total = total)
                },
                onRemoveLine = { index ->
                    cartLines.removeAt(index)
                },
                onCheckout = { name, phone, mode ->
                    scope.launch {
                        val result = repository.createWithoutBillSale(
                            lines = cartLines.toList(),
                            payMode = mode,
                            custName = name,
                            custPhone = phone
                        )
                        if (result.isSuccess) {
                            val sale = result.getOrThrow()
                            cartLines.clear()
                            isCartOpen = false
                            onSaleCompleted(sale)
                        }
                    }
                },
                onDismiss = { isCartOpen = false }
            )
        }
    }
}

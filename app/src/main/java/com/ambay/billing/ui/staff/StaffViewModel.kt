package com.ambay.billing.ui.staff

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ambay.billing.core.CustomerInfo
import com.ambay.billing.core.Item
import com.ambay.billing.core.MoneyEngine
import com.ambay.billing.core.ShopInfo
import com.ambay.billing.core.WSale
import com.ambay.billing.data.local.SecurePrefs
import com.ambay.billing.data.repository.BillingRepository
import com.ambay.billing.data.repository.SaleCartLineInput
import com.ambay.billing.data.repository.SyncRepository
import com.ambay.billing.hardware.BtPrinterDevice
import com.ambay.billing.hardware.EscPosPrinter
import com.ambay.billing.hardware.PdfBillGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@Immutable
data class StaffCartLine(
    val item: Item,
    val qty: Int = 1,
    val discountPerUnitPaise: Long = 0L
) {
    val unitSellPaise: Long get() = (item.price - discountPerUnitPaise).coerceAtLeast(0L)
    val lineTotalPaise: Long get() = unitSellPaise * qty
    val maxDiscPerUnitPaise: Long? get() = MoneyEngine.maxDiscOf(item)
    val minPricePerUnitPaise: Long? get() = MoneyEngine.minSellOf(item)
}

@Immutable
data class StaffScanState(
    val cameraActive: Boolean = true,
    val lastScannedCode: String? = null,
    val matchedItem: Item? = null,
    val notFoundCode: String? = null,
    val scannedCount: Int = 0
)

@HiltViewModel
class StaffViewModel @Inject constructor(
    private val billingRepo: BillingRepository,
    private val syncRepo: SyncRepository,
    private val securePrefs: SecurePrefs,
    private val escPosPrinter: EscPosPrinter,
    private val pdfGen: PdfBillGenerator
) : ViewModel() {

    // Strip cost price from all items for Staff role
    val items: StateFlow<List<Item>> = billingRepo.observeActiveItems()
        .map { list -> list.map { it.copy(cost = 0L) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val wsales: StateFlow<List<WSale>> = billingRepo.observeWSales()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingCount: StateFlow<Int> = billingRepo.observePendingQueueCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _scanState = MutableStateFlow(StaffScanState())
    val scanState: StateFlow<StaffScanState> = _scanState.asStateFlow()

    private val _cart = MutableStateFlow<List<StaffCartLine>>(emptyList())
    val cart: StateFlow<List<StaffCartLine>> = _cart.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Item>>(emptyList())
    val searchResults: StateFlow<List<Item>> = _searchResults.asStateFlow()

    private val _recentSearches = MutableStateFlow<List<String>>(emptyList())
    val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _lastSavedSale = MutableStateFlow<WSale?>(null)
    val lastSavedSale: StateFlow<WSale?> = _lastSavedSale.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _shopInfo = MutableStateFlow(ShopInfo())
    val shopInfo: StateFlow<ShopInfo> = _shopInfo.asStateFlow()

    init {
        viewModelScope.launch { _shopInfo.value = billingRepo.getShopInfo() }
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            _searchQuery.debounce(180).collect { q ->
                val res = billingRepo.searchItems(q).map { it.copy(cost = 0L) }
                _searchResults.value = res
            }
        }
    }

    fun onBarcodeDecoded(raw: String) {
        viewModelScope.launch {
            val clean = MoneyEngine.scanText(raw)
            val hit = billingRepo.findExactByBarcode(clean)?.copy(cost = 0L)
            if (hit != null) {
                addToCart(hit)
                _scanState.update {
                    it.copy(
                        cameraActive = false,
                        lastScannedCode = clean,
                        matchedItem = hit,
                        notFoundCode = null,
                        scannedCount = it.scannedCount + 1
                    )
                }
            } else {
                _scanState.update {
                    it.copy(
                        cameraActive = false,
                        lastScannedCode = clean,
                        matchedItem = null,
                        notFoundCode = clean
                    )
                }
            }
        }
    }

    fun resumeScanner() {
        _scanState.update { it.copy(cameraActive = true, matchedItem = null, notFoundCode = null) }
    }

    fun addToCart(item: Item) {
        val safeItem = item.copy(cost = 0L)
        _cart.update { list ->
            val idx = list.indexOfFirst { it.item.id == safeItem.id }
            if (idx >= 0) {
                list.toMutableList().also { it[idx] = it[idx].copy(qty = it[idx].qty + 1) }
            } else {
                list + StaffCartLine(item = safeItem, qty = 1)
            }
        }
    }

    fun updateQty(itemId: String, delta: Int) {
        _cart.update { list ->
            list.mapNotNull { line ->
                if (line.item.id != itemId) line
                else {
                    val nq = line.qty + delta
                    if (nq <= 0) null else line.copy(qty = nq)
                }
            }
        }
    }

    fun setLineDiscountRupees(itemId: String, discRupeesStr: String) {
        val paise = MoneyEngine.toPaise(discRupeesStr).coerceAtLeast(0L)
        _cart.update { list ->
            list.map { line ->
                if (line.item.id != itemId) line
                else {
                    val maxD = line.maxDiscPerUnitPaise
                    if (maxD != null && paise > maxD) {
                        _message.value = "${line.item.name}: max discount ${MoneyEngine.fmtINR(maxD)} per unit hai"
                        line.copy(discountPerUnitPaise = maxD)
                    } else {
                        line.copy(discountPerUnitPaise = paise.coerceAtMost(line.item.price))
                    }
                }
            }
        }
    }

    fun saveAndPrintSale(
        pay: String,
        custName: String,
        custPhone: String,
        onSaved: (WSale) -> Unit
    ) {
        val lines = _cart.value
        if (lines.isEmpty()) {
            _message.value = "Cart khali hai"
            return
        }
        viewModelScope.launch {
            val result = runCatching {
                billingRepo.saveWSale(
                    saleId = UUID.randomUUID().toString(),
                    linesInput = lines.map { l ->
                        SaleCartLineInput(
                            itemId = l.item.id,
                            qty = l.qty,
                            unitRatePaise = l.unitSellPaise
                        )
                    },
                    pay = pay,
                    cust = CustomerInfo(custName.trim(), custPhone.trim())
                )
            }
            result.onSuccess { sale ->
                _cart.value = emptyList()
                _lastSavedSale.value = sale
                _scanState.update { it.copy(scannedCount = 0) }
                onSaved(sale)
                // Auto-print to saved Bluetooth printer if configured
                securePrefs.getSavedPrinter()?.let { (mac, _, width) ->
                    escPosPrinter.printWSaleReceipt(mac, width, _shopInfo.value, sale)
                }
            }.onFailure { err ->
                _message.value = err.message ?: "Sale save nahi ho paya"
            }
        }
    }

    fun updateSearch(q: String) {
        _searchQuery.value = q
        if (q.trim().length >= 2) {
            _recentSearches.update { (listOf(q.trim()) + it).distinct().take(6) }
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            _isSyncing.value = true
            val res = syncRepo.syncNow()
            _isSyncing.value = false
            _message.value = when {
                res.revoked -> "Is device ko owner ne logout kar diya"
                res.error != null -> "Sync error: ${res.error}"
                else -> "Sync complete (pushed ${res.pushed}, pulled ${res.pulledSales})"
            }
        }
    }

    fun bondedPrinters(): List<BtPrinterDevice> = escPosPrinter.bondedPrinters()

    fun printToBluetooth(mac: String, name: String, widthMm: Int, sale: WSale) {
        viewModelScope.launch {
            securePrefs.setSavedPrinter(mac, name, widthMm)
            val res = escPosPrinter.printWSaleReceipt(mac, widthMm, _shopInfo.value, sale)
            _message.value = if (res.isSuccess) "Receipt print ho gaya" else "Printer error: ${res.exceptionOrNull()?.message}"
        }
    }

    fun shareSalePdf(sale: WSale) {
        val file = pdfGen.generateWSalePdf(_shopInfo.value, sale)
        pdfGen.sharePdf(file, "Share Receipt ${sale.no}")
    }

    fun printSalePdf(activityContext: Context, sale: WSale) {
        val file = pdfGen.generateWSalePdf(_shopInfo.value, sale)
        pdfGen.printPdfWithSystemManager(activityContext, file, "Receipt_${sale.no}")
    }

    fun clearMessage() { _message.value = null }
    fun clearLastSavedSale() { _lastSavedSale.value = null }
}

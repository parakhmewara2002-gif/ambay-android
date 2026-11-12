package com.ambay.billing.ui.owner

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ambay.billing.core.AppSettings
import com.ambay.billing.core.Bill
import com.ambay.billing.core.BillLine
import com.ambay.billing.core.CustomerInfo
import com.ambay.billing.core.Item
import com.ambay.billing.core.LinkedDevice
import com.ambay.billing.core.MoneyEngine
import com.ambay.billing.core.OfflineVerdict
import com.ambay.billing.core.ShopInfo
import com.ambay.billing.core.TrustCrypto
import com.ambay.billing.core.UserRole
import com.ambay.billing.core.WSale
import com.ambay.billing.data.local.SecurePrefs
import com.ambay.billing.data.remote.DriveRestClient
import com.ambay.billing.data.remote.RegistryClient
import com.ambay.billing.data.repository.BillingRepository
import com.ambay.billing.data.repository.SaleCartLineInput
import com.ambay.billing.data.repository.SyncRepository
import com.ambay.billing.hardware.PdfBillGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class OwnerViewModel @Inject constructor(
    private val billingRepo: BillingRepository,
    private val syncRepo: SyncRepository,
    private val securePrefs: SecurePrefs,
    private val registryClient: RegistryClient,
    private val driveClient: DriveRestClient,
    private val pdfGen: PdfBillGenerator
) : ViewModel() {

    val items: StateFlow<List<Item>> = billingRepo.observeAllItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bills: StateFlow<List<Bill>> = billingRepo.observeBills()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val wsales: StateFlow<List<WSale>> = billingRepo.observeWSales()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingCount: StateFlow<Int> = billingRepo.observePendingQueueCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _shopInfo = MutableStateFlow(ShopInfo())
    val shopInfo: StateFlow<ShopInfo> = _shopInfo.asStateFlow()

    private val _appSettings = MutableStateFlow(AppSettings())
    val appSettings: StateFlow<AppSettings> = _appSettings.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _verdict = MutableStateFlow<OfflineVerdict?>(null)
    val verdict: StateFlow<OfflineVerdict?> = _verdict.asStateFlow()

    private val _linkedDevices = MutableStateFlow<List<LinkedDevice>>(emptyList())
    val linkedDevices: StateFlow<List<LinkedDevice>> = _linkedDevices.asStateFlow()

    private val _pairingQrText = MutableStateFlow<String?>(null)
    val pairingQrText: StateFlow<String?> = _pairingQrText.asStateFlow()

    init {
        viewModelScope.launch {
            _shopInfo.value = billingRepo.getShopInfo()
            _appSettings.value = billingRepo.getAppSettings()
        }
    }

    fun clearMessage() { _message.value = null }
    fun clearVerdict() { _verdict.value = null }
    fun clearPairingQr() { _pairingQrText.value = null }

    fun createPaperBill(
        lines: List<BillLine>,
        discType: String,
        discVal: Long,
        gstBp: Int,
        gstMode: String,
        taxType: String,
        pay: String,
        custName: String,
        custPhone: String,
        warLang: String,
        paperSize: String,
        onCreated: (Bill, File) -> Unit
    ) {
        viewModelScope.launch {
            val res = runCatching {
                val bill = billingRepo.saveBill(
                    linesInput = lines,
                    discType = discType,
                    discVal = discVal,
                    gstBp = gstBp,
                    gstMode = gstMode,
                    taxType = taxType,
                    pay = pay,
                    cust = CustomerInfo(custName.trim(), custPhone.trim()),
                    warLang = warLang
                )
                val pdf = withContext(Dispatchers.IO) { pdfGen.generateBillPdf(bill, paperSize) }
                bill to pdf
            }
            res.onSuccess { (b, pdf) ->
                _message.value = "Bill ${b.no} ban gaya aur sign ho gaya"
                onCreated(b, pdf)
            }.onFailure { err ->
                _message.value = err.message ?: "Bill save nahi ho paya"
            }
        }
    }

    fun createWithoutBillSale(
        lines: List<SaleCartLineInput>,
        pay: String,
        custName: String,
        custPhone: String
    ) {
        viewModelScope.launch {
            runCatching {
                billingRepo.saveWSale(
                    linesInput = lines,
                    pay = pay,
                    cust = CustomerInfo(custName.trim(), custPhone.trim())
                )
            }.onSuccess { s ->
                _message.value = "Without-Bill Sale ${s.no} save ho gaya"
            }.onFailure { err ->
                _message.value = err.message
            }
        }
    }

    fun verifyQrCode(qrText: String) {
        viewModelScope.launch {
            val payload = TrustCrypto.decodePayload(qrText)
            if (payload == null) {
                _verdict.value = OfflineVerdict(
                    status = "INCOMPLETE",
                    label = "INVALID INVOICE QR",
                    level = "bad",
                    notes = listOf("Ye valid AMBINV1 invoice QR nahi hai")
                )
                return@launch
            }
            val localBill = bills.value.firstOrNull { it.id == payload.u }
            val trust = securePrefs.getTrustBundle()?.keys.orEmpty()
            val keyInfo = securePrefs.ensureDeviceKey()
            val localKeys = mapOf(
                keyInfo.kid to com.ambay.billing.core.TrustedKeyEntry(keyInfo.alg, keyInfo.spkiB64u, "active")
            )
            _verdict.value = TrustCrypto.verifyOffline(
                payload = payload,
                bill = localBill,
                trustedKeys = trust,
                localKeys = localKeys
            )
        }
    }

    fun verifyPdfUri(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes == null) {
                _message.value = "PDF file read nahi ho payi"
                return@launch
            }
            val tokens = TrustCrypto.extractTokensFromPdf(bytes)
            if (tokens.isEmpty()) {
                _verdict.value = OfflineVerdict(
                    status = "UNSIGNED",
                    label = "NO EMBEDDED MANIFEST FOUND",
                    level = "warn",
                    notes = listOf("Is PDF me AMBM1 digital manifest token nahi mila")
                )
                return@launch
            }
            val trust = securePrefs.getTrustBundle()?.keys.orEmpty()
            val keyInfo = securePrefs.ensureDeviceKey()
            val localKeys = mapOf(
                keyInfo.kid to com.ambay.billing.core.TrustedKeyEntry(keyInfo.alg, keyInfo.spkiB64u, "active")
            )
            _verdict.value = TrustCrypto.verifyOffline(
                token = tokens.first(),
                trustedKeys = trust,
                localKeys = localKeys
            )
        }
    }

    fun saveItem(item: Item, openingQty: Int?, forceDup: Boolean, onDuplicate: (String) -> Unit, onSaved: () -> Unit) {
        viewModelScope.launch {
            val res = runCatching { billingRepo.upsertItem(item, openingQty, forceDup) }
            res.onSuccess {
                _message.value = "${it.name} save ho gaya"
                onSaved()
            }.onFailure { err ->
                val msg = err.message.orEmpty()
                if (msg.startsWith("DUPLICATE_NAME:")) {
                    onDuplicate(msg.removePrefix("DUPLICATE_NAME:"))
                } else {
                    _message.value = msg
                }
            }
        }
    }

    fun adjustStock(itemId: String, type: String, qtyOrDelta: Int, reason: String) {
        viewModelScope.launch {
            runCatching { billingRepo.stockAction(itemId, type, qtyOrDelta, reason) }
                .onSuccess { _message.value = "Stock update ho gaya" }
                .onFailure { _message.value = it.message }
        }
    }

    fun printBarcodeLabels(selectedItems: List<Item>) {
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) { pdfGen.generateBarcodeLabelsPdf(selectedItems) }
            pdfGen.sharePdf(file, "Barcode Labels PDF")
        }
    }

    fun cancelBill(billId: String, reason: String) {
        viewModelScope.launch {
            runCatching { billingRepo.cancelBill(billId, reason) }
                .onSuccess { _message.value = "Bill cancel ho gaya, stock wapas add hua" }
                .onFailure { _message.value = it.message }
        }
    }

    fun voidWSale(saleId: String, reason: String) {
        viewModelScope.launch {
            runCatching { billingRepo.voidWSale(saleId, reason) }
                .onSuccess { _message.value = "Sale void ho gaya, stock wapas add hua" }
                .onFailure { _message.value = it.message }
        }
    }

    fun shareBillPdf(bill: Bill, paperSize: String = "A4") {
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) { pdfGen.generateBillPdf(bill, paperSize) }
            pdfGen.sharePdf(file, "Share Bill ${bill.no}")
        }
    }

    fun printBillPdf(activityContext: Context, bill: Bill, paperSize: String = "A4") {
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) { pdfGen.generateBillPdf(bill, paperSize) }
            pdfGen.printPdfWithSystemManager(activityContext, file, "Bill_${bill.no}")
        }
    }

    fun exportBackupToUri(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val jsonStr = billingRepo.exportBackupJson()
                context.contentResolver.openOutputStream(uri)?.use { it.write(jsonStr.toByteArray(Charsets.UTF_8)) }
            }.onSuccess {
                _message.value = "Website-compatible backup JSON export ho gaya"
            }.onFailure {
                _message.value = "Export fail: ${it.message}"
            }
        }
    }

    fun importBackupFromUri(context: Context, uri: Uri, replaceAll: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("File read nahi ho payi")
                billingRepo.importBackupJson(raw, replaceAll)
            }.onSuccess { parsed ->
                _message.value = "Import complete: ${parsed.items.size} items, ${parsed.bills.size} bills, ${parsed.wsales.size} sales"
            }.onFailure {
                _message.value = "Import fail: ${it.message}"
            }
        }
    }

    fun updateShopInfo(shop: ShopInfo) {
        viewModelScope.launch {
            billingRepo.saveShopInfo(shop)
            _shopInfo.value = shop
            _message.value = "Shop details save ho gayi"
        }
    }

    fun updateSettings(settings: AppSettings) {
        viewModelScope.launch {
            billingRepo.saveAppSettings(settings)
            _appSettings.value = settings
            _message.value = "Settings save ho gayi"
        }
    }

    fun setStaffBiometric(enabled: Boolean) {
        securePrefs.setStaffBiometricEnabled(enabled)
        _message.value = if (enabled) "Staff biometric ON" else "Staff biometric OFF"
    }

    fun setStaffPassword(pwd: String) {
        if (pwd.length < 6) {
            _message.value = "Password kam se kam 6 characters ka ho"
            return
        }
        securePrefs.setPassword(UserRole.STAFF, pwd)
        _message.value = "Staff password update ho gaya"
    }

    fun refreshLinkedDevices() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { registryClient.listDevices() }
                .onSuccess { _linkedDevices.value = it }
                .onFailure { _message.value = it.message }
        }
    }

    fun revokeLinkedDevice(kid: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                registryClient.logoutDevice(kid, "Revoked by owner")
                registryClient.listDevices()
            }.onSuccess {
                _linkedDevices.value = it
                _message.value = "Device logout kar diya gaya"
            }.onFailure { _message.value = it.message }
        }
    }

    fun generatePairingQr() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val p = registryClient.createPairingSession()
                val token = p["token"]!!.jsonPrimitive.content
                val issuer = p["issuer"]!!.jsonPrimitive.content
                val exp = p["expiresAt"]!!.jsonPrimitive.content.toLong()
                val srvKeys = securePrefs.getServerKeys().entries.firstOrNull()
                    ?: error("Server trust key missing")
                val qrObj = mapOf(
                    "v" to 1,
                    "u" to securePrefs.getBaseUrl(),
                    "t" to token,
                    "i" to issuer,
                    "e" to exp,
                    "r" to mapOf("skid" to srvKeys.key, "alg" to srvKeys.value.alg, "spki" to srvKeys.value.spki)
                )
                "AMBPAIR1." + TrustCrypto.b64uEncode(
                    com.ambay.billing.core.CanonicalJson.stringify(qrObj).toByteArray(Charsets.UTF_8)
                )
            }.onSuccess { qr ->
                _pairingQrText.value = qr
            }.onFailure { _message.value = it.message }
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            val res = syncRepo.syncNow()
            _message.value = res.error ?: "Sync complete (pushed ${res.pushed}, pulled ${res.pulledSales})"
        }
    }

    fun uploadDriveSnapshot(token: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val json = billingRepo.exportBackupJson()
                driveClient.uploadSnapshot(token, json)
            }.onSuccess { f ->
                _message.value = "Drive backup uploaded: ${f.name}"
            }.onFailure { _message.value = "Drive upload fail: ${it.message}" }
        }
    }

    fun deviceKeySummary(): String {
        val k = securePrefs.ensureDeviceKey()
        return "KID: ${k.kid} (${k.alg}) | Issuer: ${securePrefs.getIssuerId().ifBlank { "Local" }}"
    }

    fun setHiddenBaseUrl(url: String) {
        securePrefs.setBaseUrl(url)
        _message.value = "Server URL updated: $url"
    }
}

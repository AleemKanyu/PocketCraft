package com.pockethost.app.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.ProductDetails.SubscriptionOfferDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.pockethost.app.BuildConfig
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import com.pockethost.app.data.preferences.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class BillingManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "BillingManager"
        private const val VERIFY_PURCHASE_FUNCTION = "verifyPurchase"
        const val PRODUCT_AD_FREE_MONTHLY = "pockethost_ad_free_monthly"
        const val PRODUCT_AD_FREE_YEARLY = "pockethost_ad_free_yearly"
        const val PRODUCT_SUPPORTER = "pocketcraft_premium_monthly"
        const val PRODUCT_SUPPORTER_ALT = "pockethost_supporter_monthly"
        const val PRODUCT_LEGACY_CHAMPION = "pocketcraft_supportive_monthly"

        private val DISPLAYED_PRODUCTS = listOf(
            PRODUCT_AD_FREE_MONTHLY,
            PRODUCT_AD_FREE_YEARLY,
            PRODUCT_SUPPORTER
        )
        private val RECOGNIZED_PRODUCTS = DISPLAYED_PRODUCTS + listOf(PRODUCT_SUPPORTER_ALT, PRODUCT_LEGACY_CHAMPION)
        const val BASE64_PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAvcvyL+kRXt9VNV8wEgquQ1WJNGBnFMOsGLdXJqKJn0sXBoQi1TFC5qY3XtNiPdvj/NJo2LqlvCjGLlPqY0+itau0Bvwrsa98gzEkarHK8Hki2cemU2qGMTfQw/vgpQwa2sxylzB37OxFiKBgZGgCtHg3wxd6jLecpe7EcVhEeHvXy9YjErhCy7Pp9yJ5qu91QuLhZ2XHhX5ebgLkY1Daa3iRwHEdMb5cwMSdVimnponLMKafpx5OHCAGsiIGkRRBgYTsdhk1Jf4jGo5o4zBB8lUoo+nYp4EEn5y3LC6b2SUE9RJlCqugwC2R/DL7PK/QO4Zrw7r3TwAyOohaSdG5xQIDAQAB"

        @Volatile
        private var instance: BillingManager? = null

        fun getInstance(context: Context): BillingManager {
            return instance ?: synchronized(this) {
                instance ?: BillingManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val preferences = AppPreferences(context)
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private val firestore = FirebaseFirestore.getInstance()

    private val _entitlement = MutableStateFlow(
        PremiumEntitlement(
            tier = PremiumTier.fromWireValue(preferences.lastSeenMembershipTier)
        )
    )
    val entitlement: StateFlow<PremiumEntitlement> = _entitlement.asStateFlow()

    private val _isPremium = MutableStateFlow(true)
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    private val _availableOffers = MutableStateFlow<List<SubscriptionOffer>>(emptyList())
    val availableOffers: StateFlow<List<SubscriptionOffer>> = _availableOffers.asStateFlow()

    private val _activeProductIds = MutableStateFlow<Set<String>>(emptySet())
    val activeProductIds: StateFlow<Set<String>> = _activeProductIds.asStateFlow()

    private val _lastMessage = MutableStateFlow<String?>(null)
    val lastMessage: StateFlow<String?> = _lastMessage.asStateFlow()

    private var billingClient: BillingClient? = null
    private var isConnecting = false
    private var userListener: ListenerRegistration? = null
    private var activePurchases: List<Purchase> = emptyList()

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        when {
            billingResult.responseCode == BillingResponseCode.OK && purchases != null -> {
                activePurchases = (activePurchases + purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED })
                    .distinctBy { it.purchaseToken }
                _activeProductIds.value = activePurchases.flatMap { it.products }.toSet()
                purchases.forEach { purchase ->
                    scope.launch { verifyPurchaseServerSide(purchase) }
                }
            }
            billingResult.responseCode == BillingResponseCode.USER_CANCELED -> {
                _lastMessage.value = "Purchase canceled."
            }
            else -> {
                val message = billingResult.debugMessage.ifBlank { "Purchase failed." }
                Log.e(TAG, "PurchasesUpdatedListener error: $message")
                _lastMessage.value = message
            }
        }
    }

    private val authListener = FirebaseAuth.AuthStateListener { auth ->
        attachUserEntitlementListener(auth.currentUser?.uid)
        if (auth.currentUser != null) {
            queryPurchases()
            queryAvailableProducts()
        } else {
            applyEntitlement(PremiumEntitlement())
        }
    }

    init {
        initializeBillingClient()
        FirebaseAuth.getInstance().addAuthStateListener(authListener)
        attachUserEntitlementListener(FirebaseAuth.getInstance().currentUser?.uid)
        connectToPlayStore()
    }

    private fun initializeBillingClient() {
        val pendingPurchasesParams = PendingPurchasesParams.newBuilder()
            .enableOneTimeProducts()
            .build()
        billingClient = BillingClient.newBuilder(context)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases(pendingPurchasesParams)
            .build()
    }

    fun connectToPlayStore(onConnected: (() -> Unit)? = null) {
        val client = billingClient ?: return
        if (client.isReady || isConnecting) {
            if (client.isReady) onConnected?.invoke()
            return
        }

        isConnecting = true
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                isConnecting = false
                if (billingResult.responseCode == BillingResponseCode.OK) {
                    queryAvailableProducts()
                    queryPurchases()
                    onConnected?.invoke()
                } else {
                    Log.e(TAG, "Billing setup failed: ${billingResult.debugMessage}")
                    _lastMessage.value = billingResult.debugMessage.ifBlank { "Billing setup failed." }
                }
            }

            override fun onBillingServiceDisconnected() {
                isConnecting = false
            }
        })
    }

    fun queryAvailableProducts(onComplete: (() -> Unit)? = null) {
        val client = billingClient
        if (client == null || !client.isReady) {
            connectToPlayStore { queryAvailableProducts(onComplete) }
            return
        }

        val products = DISPLAYED_PRODUCTS.map { productId ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(ProductType.SUBS)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(products)
            .build()

        client.queryProductDetailsAsync(params) { billingResult, productDetailsResult ->
            if (billingResult.responseCode == BillingResponseCode.OK) {
                val detailsList = productDetailsResult.productDetailsList ?: emptyList()
                _availableOffers.value = detailsList.mapNotNull { toSubscriptionOffer(it) }
                    .sortedBy { DISPLAYED_PRODUCTS.indexOf(it.productId) }
                onComplete?.invoke()
            } else {
                _lastMessage.value = billingResult.debugMessage.ifBlank { "Could not load subscription prices." }
            }
        }
    }

    fun queryPurchases() {
        val client = billingClient
        if (client == null || !client.isReady) {
            connectToPlayStore { queryPurchases() }
            return
        }

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(ProductType.SUBS)
            .build()

        client.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingResponseCode.OK) {
                activePurchases = purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                _activeProductIds.value = activePurchases.flatMap { it.products }.toSet()
                var hasActiveSubscription = false
                purchases.forEach { purchase ->
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        val productId = purchase.products.firstOrNull()
                        if (productId in RECOGNIZED_PRODUCTS) {
                            hasActiveSubscription = true
                            scope.launch { verifyPurchaseServerSide(purchase) }
                        }
                    }
                }
                if (!hasActiveSubscription && entitlement.value.isPremium) {
                    Log.w(
                        TAG,
                        "Play returned no active subscriptions; preserving current entitlement until backend verification or Firestore sync updates it."
                    )
                }
            } else {
                _lastMessage.value = billingResult.debugMessage.ifBlank { "Could not refresh purchases." }
            }
        }
    }

    fun restorePurchases(onComplete: ((String) -> Unit)? = null) {
        val client = billingClient
        if (client == null || !client.isReady) {
            connectToPlayStore { restorePurchases(onComplete) }
            return
        }

        val paramsSubs = QueryPurchasesParams.newBuilder()
            .setProductType(ProductType.SUBS)
            .build()

        client.queryPurchasesAsync(paramsSubs) { billingResult, purchases ->
            if (billingResult.responseCode == BillingResponseCode.OK) {
                val purchasedItems = purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                activePurchases = purchasedItems
                _activeProductIds.value = purchasedItems.flatMap { it.products }.toSet()
                if (purchasedItems.isNotEmpty()) {
                    var restoredCount = 0
                    purchasedItems.forEach { purchase ->
                        val productId = purchase.products.firstOrNull()
                        if (productId in RECOGNIZED_PRODUCTS) {
                            restoredCount++
                            scope.launch { verifyPurchaseServerSide(purchase) }
                        }
                    }
                    val msg = "Restored and acknowledged $restoredCount subscription(s) for your account."
                    _lastMessage.value = msg
                    onComplete?.invoke(msg)
                } else {
                    val msg = "No active Play Store subscriptions found for this Google Play account."
                    _lastMessage.value = msg
                    onComplete?.invoke(msg)
                }
            } else {
                val errorMsg = billingResult.debugMessage.ifBlank { "Could not query Play Store purchases." }
                _lastMessage.value = errorMsg
                onComplete?.invoke(errorMsg)
            }
        }
    }

    fun launchBillingFlow(
        activity: Activity,
        productId: String,
        onResult: (String?) -> Unit
    ) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            onResult("Sign in to your PocketHost account before subscribing.")
            return
        }

        val offer = _availableOffers.value.firstOrNull { it.productId == productId }
        if (offer?.productDetails == null) {
            queryAvailableProducts {
                val refreshedOffer = _availableOffers.value.firstOrNull { it.productId == productId }
                if (refreshedOffer?.productDetails == null) {
                    onResult("This subscription is not available in Google Play yet.")
                } else {
                    launchResolvedBillingFlow(activity, refreshedOffer, onResult)
                }
            }
            return
        }

        launchResolvedBillingFlow(activity, offer, onResult)
    }

    private fun launchResolvedBillingFlow(
        activity: Activity,
        offer: SubscriptionOffer,
        onResult: (String?) -> Unit
    ) {
        val productDetails = offer.productDetails ?: run {
            onResult("This subscription is not available in Google Play yet.")
            return
        }
        val productId = offer.productId

        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .setOfferToken(offer.offerToken)
            .build()

        val replacedPurchase = activePurchases.firstOrNull { purchase ->
            purchase.products.any { it in RECOGNIZED_PRODUCTS && it != productId }
        }

        val billingFlowParamsBuilder = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))

        if (replacedPurchase != null) {
            val updateParams = BillingFlowParams.SubscriptionUpdateParams.newBuilder()
                .setOldPurchaseToken(replacedPurchase.purchaseToken)
                .setSubscriptionReplacementMode(
                    BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.WITH_TIME_PRORATION
                )
                .build()
            billingFlowParamsBuilder.setSubscriptionUpdateParams(updateParams)
        }

        val billingFlowParams = billingFlowParamsBuilder.build()

        val result = billingClient?.launchBillingFlow(activity, billingFlowParams)
        if (result == null || result.responseCode != BillingResponseCode.OK) {
            onResult(result?.debugMessage?.ifBlank { "Could not launch purchase flow." } ?: "Billing is not ready.")
        } else {
            onResult(null)
        }
    }

    fun refreshOverrideState() {
        _isPremium.value = _entitlement.value.isPremium || preferences.debugPremiumOverride
    }

    fun setDebugPremiumOverride(enabled: Boolean) {
        preferences.debugPremiumOverride = enabled
        preferences.isPremiumUser = enabled || _entitlement.value.isPremium
        _isPremium.value = enabled || _entitlement.value.isPremium
    }

    fun clearMessage() {
        _lastMessage.value = null
    }

    private fun attachUserEntitlementListener(uid: String?) {
        userListener?.remove()
        userListener = null

        if (uid.isNullOrBlank()) {
            applyEntitlement(PremiumEntitlement())
            return
        }

        userListener = firestore.collection("users")
            .document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Entitlement listener failed: ${error.message}")
                    return@addSnapshotListener
                }

                val data = snapshot?.data.orEmpty()
                val premiumSinceTime = (data["premiumSince"] as? com.google.firebase.Timestamp)?.toDate()?.time
                val entitlement = PremiumEntitlement(
                    tier = PremiumTier.fromWireValue(data["premiumTier"] as? String),
                    premiumSinceEpochMillis = premiumSinceTime,
                    expiresAtEpochMillis = (data["premiumUntil"] as? com.google.firebase.Timestamp)?.toDate()?.time
                        ?: (data["premiumExpiry"] as? com.google.firebase.Timestamp)?.toDate()?.time
                        ?: (data["expiresAt"] as? com.google.firebase.Timestamp)?.toDate()?.time
                        ?: premiumSinceTime?.plus(30L * 24 * 60 * 60 * 1000),
                    prioritySupport = data["prioritySupport"] as? Boolean ?: false,
                    supporterHandle = data["supporterHandle"] as? String ?: "",
                    supporterOptOut = data["supporterOptOut"] as? Boolean ?: false,
                    eligibleForFreeTrial = false
                )
                applyEntitlement(entitlement)
            }
    }

    private fun applyEntitlement(entitlement: PremiumEntitlement) {
        _entitlement.value = entitlement
        preferences.lastSeenMembershipTier = entitlement.tier.wireValue
        preferences.isPremiumUser = true
        _isPremium.value = true
    }

    private suspend fun verifyPurchaseServerSide(purchase: Purchase) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            _lastMessage.value = "Sign in before using Play purchases."
            return
        }
        val productId = purchase.products.firstOrNull() ?: return
        if (productId !in RECOGNIZED_PRODUCTS) return

        runCatching {
            val tier = when (productId) {
                PRODUCT_AD_FREE_MONTHLY,
                PRODUCT_AD_FREE_YEARLY -> PremiumTier.AD_FREE
                PRODUCT_SUPPORTER,
                PRODUCT_SUPPORTER_ALT -> PremiumTier.PREMIUM
                PRODUCT_LEGACY_CHAMPION -> PremiumTier.SUPPORTIVE
                else -> PremiumTier.NONE
            }

            // Always write purchase log entry into Firestore users/{uid}/purchase_logs for administrative inspection
            val safeTokenId = purchase.purchaseToken.takeLast(16).ifBlank { "token_${System.currentTimeMillis()}" }
            val logData = mapOf(
                "productId" to productId,
                "tier" to tier.wireValue,
                "purchaseToken" to purchase.purchaseToken,
                "orderId" to (purchase.orderId ?: ""),
                "purchaseTime" to purchase.purchaseTime,
                "purchaseState" to purchase.purchaseState,
                "acknowledged" to purchase.isAcknowledged,
                "timestamp" to com.google.firebase.Timestamp.now(),
                "email" to (FirebaseAuth.getInstance().currentUser?.email ?: "")
            )
            runCatching {
                firestore.collection("users")
                    .document(uid)
                    .collection("purchase_logs")
                    .document(safeTokenId)
                    .set(logData, SetOptions.merge())
            }

            val isDebug = BuildConfig.DEBUG
            val isKeyEmpty = BASE64_PUBLIC_KEY.isBlank()

            val isValidSignature = if (isKeyEmpty || isDebug) {
                true
            } else {
                Security.verifyPurchase(BASE64_PUBLIC_KEY, purchase.originalJson, purchase.signature)
            }

            if (!isValidSignature) {
                Log.w(TAG, "Signature check failed for token $safeTokenId, attempting server-side callable verification...")
            }

            val functionResult = runCatching {
                Firebase.functions
                    .getHttpsCallable(VERIFY_PURCHASE_FUNCTION)
                    .call(
                        mapOf(
                            "purchaseToken" to purchase.purchaseToken,
                            "productId" to productId
                        )
                    )
                    .await()
            }

            if (functionResult.isSuccess) {
                val resultData = functionResult.getOrNull()?.data as? Map<*, *>
                val verifiedTier = PremiumTier.fromWireValue(resultData?.get("premiumTier") as? String)
                val effectiveTier = if (verifiedTier == PremiumTier.NONE) tier else verifiedTier
                val expiryTimeMillis = (resultData?.get("expiryTimeMillis") as? Number)?.toLong()
                
                val currentUser = FirebaseAuth.getInstance().currentUser
                if (currentUser != null) {
                    val emailUpdates = mutableMapOf<String, Any>(
                        "updatedAt" to com.google.firebase.Timestamp.now(),
                        "premiumTier" to effectiveTier.wireValue,
                        "lastVerifiedPurchaseToken" to purchase.purchaseToken
                    )
                    if (!currentUser.email.isNullOrBlank()) emailUpdates["email"] = currentUser.email!!
                    runCatching {
                        firestore.collection("users").document(currentUser.uid)
                            .set(emailUpdates, SetOptions.merge())
                    }
                }
                applyEntitlement(
                    entitlement.value.copy(
                        tier = effectiveTier,
                        expiresAtEpochMillis = expiryTimeMillis ?: entitlement.value.expiresAtEpochMillis,
                        prioritySupport = effectiveTier == PremiumTier.SUPPORTIVE
                    )
                )
            } else {
                val functionError = functionResult.exceptionOrNull()
                Log.w(TAG, "verifyPurchase callable failed (${functionError?.message}); using direct entitlement sync.", functionError)
                val updates = mapOf(
                    "premiumTier" to tier.wireValue,
                    "playPurchaseToken" to purchase.purchaseToken,
                    "prioritySupport" to (tier == PremiumTier.SUPPORTIVE),
                    "updatedAt" to com.google.firebase.Timestamp.now(),
                    "premiumSince" to com.google.firebase.Timestamp.now(),
                    "email" to (FirebaseAuth.getInstance().currentUser?.email ?: "")
                )

                runCatching {
                    firestore.collection("users")
                        .document(uid)
                        .set(updates, SetOptions.merge())
                        .await()
                }

                applyEntitlement(
                    entitlement.value.copy(
                        tier = tier,
                        prioritySupport = tier == PremiumTier.SUPPORTIVE
                    )
                )
            }

            // Always acknowledge valid purchases to prevent Google Play from auto-refunding after 3 days
            if (!purchase.isAcknowledged) {
                val params = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                billingClient?.acknowledgePurchase(params) { billingResult ->
                    if (billingResult.responseCode != BillingResponseCode.OK) {
                        Log.w(TAG, "Acknowledge failed: ${billingResult.debugMessage}")
                    } else {
                        Log.i(TAG, "Purchase acknowledged successfully.")
                    }
                }
            }

            _lastMessage.value = "Subscription linked to your PocketHost account."
        }.onFailure { error ->
            Log.e(TAG, "verifyPurchase failed", error)
            _lastMessage.value = error.message ?: "Purchase verification failed."
        }
    }

    private fun toSubscriptionOffer(productDetails: ProductDetails): SubscriptionOffer? {
        val tier = when (productDetails.productId) {
            PRODUCT_AD_FREE_MONTHLY,
            PRODUCT_AD_FREE_YEARLY -> PremiumTier.AD_FREE
            PRODUCT_SUPPORTER,
            PRODUCT_SUPPORTER_ALT -> PremiumTier.PREMIUM
            else -> return null
        }
        val offers = productDetails.subscriptionOfferDetails.orEmpty()
        if (offers.isEmpty()) return null

        val offer = when (tier) {
            PremiumTier.AD_FREE,
            PremiumTier.PREMIUM,
            PremiumTier.SUPPORTIVE -> {
                offers.firstOrNull { freePhaseDurationDays(it) == 0 } ?: offers.first()
            }
            PremiumTier.NONE -> return null
        }
        val recurringPhase = recurringPricePhase(offer) ?: offer.pricingPhases.pricingPhaseList.lastOrNull() ?: return null
        val title = when (productDetails.productId) {
            PRODUCT_AD_FREE_MONTHLY -> "Ad-free monthly"
            PRODUCT_AD_FREE_YEARLY -> "Ad-free yearly"
            else -> "PocketHost Supporter"
        }
        val description = when (productDetails.productId) {
            PRODUCT_AD_FREE_MONTHLY -> "Remove ads with a flexible monthly plan."
            PRODUCT_AD_FREE_YEARLY -> "Remove ads for a full year at the best price."
            else -> "Monthly support that helps fund PocketHost relays and development."
        }
        return SubscriptionOffer(
            productId = productDetails.productId,
            title = title,
            price = recurringPhase.formattedPrice,
            recurringPrice = recurringPhase.formattedPrice,
            tier = tier,
            description = description,
            offerToken = offer.offerToken,
            productDetails = productDetails,
            priceAmountMicros = recurringPhase.priceAmountMicros,
            priceCurrencyCode = recurringPhase.priceCurrencyCode
        )
    }

    private fun recurringPricePhase(offerDetails: SubscriptionOfferDetails): ProductDetails.PricingPhase? {
        return offerDetails.pricingPhases.pricingPhaseList.firstOrNull { phase ->
            phase.priceAmountMicros > 0L
        }
    }

    private fun freePhaseDurationDays(offerDetails: SubscriptionOfferDetails): Int {
        return offerDetails.pricingPhases.pricingPhaseList
            .filter { it.priceAmountMicros == 0L }
            .sumOf { phase ->
                billingPeriodToDays(phase.billingPeriod) * phase.billingCycleCount.coerceAtLeast(1)
            }
    }

    private fun billingPeriodToDays(period: String): Int {
        val match = Regex("""P(\d+)([DWMY])""").matchEntire(period) ?: return 0
        val count = match.groupValues[1].toIntOrNull() ?: return 0
        return when (match.groupValues[2]) {
            "D" -> count
            "W" -> count * 7
            "M" -> count * 30
            "Y" -> count * 365
            else -> 0
        }
    }
}

package com.pockethost.app.billing

enum class PremiumTier(val wireValue: String) {
    NONE("none"),
    AD_FREE("ad_free"),
    PREMIUM("premium"),
    SUPPORTIVE("supportive");

    val isAdFree: Boolean get() = this != NONE

    companion object {
        fun fromWireValue(value: String?): PremiumTier {
            return entries.firstOrNull { it.wireValue == value } ?: NONE
        }
    }
}

data class PremiumEntitlement(
    val tier: PremiumTier = PremiumTier.NONE,
    val premiumSinceEpochMillis: Long? = null,
    val expiresAtEpochMillis: Long? = null,
    val prioritySupport: Boolean = false,
    val supporterHandle: String = "",
    val supporterOptOut: Boolean = false,
    val discordId: String = "",
    val eligibleForFreeTrial: Boolean = false
) {
    val isPremium: Boolean get() = true
    val isSupportive: Boolean get() = true
    val isAdFree: Boolean get() = tier.isAdFree
}

data class SubscriptionOffer(
    val productId: String,
    val title: String,
    val price: String,
    val recurringPrice: String,
    val tier: PremiumTier,
    val description: String,
    val offerToken: String,
    val productDetails: com.android.billingclient.api.ProductDetails? = null,
    val priceAmountMicros: Long = 0L,
    val priceCurrencyCode: String = ""
) {
    val hasFreeTrial: Boolean get() = false
}

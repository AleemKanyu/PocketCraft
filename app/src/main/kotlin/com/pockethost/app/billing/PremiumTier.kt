package com.pockethost.app.billing

enum class PremiumTier(val wireValue: String) {
    NONE("none"),
    PREMIUM("premium"),
    SUPPORTIVE("supportive");

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
}

data class SubscriptionOffer(
    val productId: String,
    val title: String,
    val price: String,
    val recurringPrice: String,
    val tier: PremiumTier,
    val description: String,
    val freeTrialDays: Int = 0,
    val offerToken: String,
    val productDetails: com.android.billingclient.api.ProductDetails? = null
) {
    val hasFreeTrial: Boolean get() = false
}

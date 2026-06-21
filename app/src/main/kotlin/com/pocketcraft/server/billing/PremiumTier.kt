package com.pocketcraft.server.billing

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
    val eligibleForFreeTrial: Boolean = com.pocketcraft.server.BuildConfig.DEBUG
) {
    val isPremium: Boolean get() = tier != PremiumTier.NONE
    val isSupportive: Boolean get() = tier == PremiumTier.SUPPORTIVE
}

data class SubscriptionOffer(
    val productId: String,
    val title: String,
    val price: String,
    val tier: PremiumTier,
    val description: String,
    val offerToken: String,
    val productDetails: com.android.billingclient.api.ProductDetails
)

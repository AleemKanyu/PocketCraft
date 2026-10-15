package com.pockethost.app.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pockethost.app.R
import com.pockethost.app.billing.BillingManager
import com.pockethost.app.billing.PremiumTier
import com.pockethost.app.billing.SubscriptionOffer
import com.pockethost.app.ui.theme.ButtonFont
import com.pockethost.app.ui.theme.Monocraft
import com.pockethost.app.ui.theme.PocketColors
import com.pockethost.app.ui.theme.raisedBorder
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumUpgradeBottomSheet(
    onDismissRequest: () -> Unit,
    onNavigateToSignUp: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val billingManager = remember { BillingManager.getInstance(context) }
    val offers by billingManager.availableOffers.collectAsState()
    val lastMessage by billingManager.lastMessage.collectAsState()
    val entitlement by billingManager.entitlement.collectAsState()
    val activeProductIds by billingManager.activeProductIds.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var purchasingProductId by remember { mutableStateOf<String?>(null) }

    fun startPurchase(offer: SubscriptionOffer) {
        val activity = context.findActivity()
        if (activity == null) {
            Toast.makeText(context, "Could not launch purchase: invalid activity.", Toast.LENGTH_LONG).show()
            return
        }
        purchasingProductId = offer.productId
        billingManager.launchBillingFlow(activity, offer.productId) { error ->
            purchasingProductId = null
            if (error != null) {
                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            } else {
                scope.launch {
                    sheetState.hide()
                    onDismissRequest()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!com.pockethost.app.BuildConfig.IS_EXTERNAL_BUILD) {
            billingManager.queryAvailableProducts()
        }
    }

    LaunchedEffect(lastMessage) {
        if (!com.pockethost.app.BuildConfig.IS_EXTERNAL_BUILD) {
            val message = lastMessage ?: return@LaunchedEffect
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            billingManager.clearMessage()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        dragHandle = { IosDragHandle() },
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Animated Ambient Hero Header ──
            val heartTransition = rememberInfiniteTransition(label = "support_heart_pulse")
            val heartPulseScale by heartTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.10f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1300, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "heart_pulse_scale"
            )
            val haloAlpha by heartTransition.animateFloat(
                initialValue = 0.25f,
                targetValue = 0.60f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1300, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "halo_alpha"
            )

            Box(
                modifier = Modifier.size(62.dp),
                contentAlignment = Alignment.Center
            ) {
                // Radiant outer glow
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .graphicsLayer {
                            scaleX = heartPulseScale
                            scaleY = heartPulseScale
                            alpha = haloAlpha
                        }
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0xFFFF4B72), Color(0xFFFF8A9A).copy(alpha = 0f))
                            ),
                            shape = CircleShape
                        )
                )

                // Heart badge (hollow with colored outline)
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(
                            Color(0xFFFF4B72).copy(alpha = 0.12f),
                            shape = CircleShape
                        )
                        .border(
                            width = 1.5.dp,
                            color = Color(0xFFFF4B72).copy(alpha = 0.6f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_heart_outline),
                        contentDescription = null,
                        tint = Color(0xFFFF4B72),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Support PocketHost",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = Monocraft,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = if (com.pockethost.app.BuildConfig.IS_EXTERNAL_BUILD) {
                        "All server features stay free.\nHelp keep our server relays and development alive."
                    } else {
                        "All server features stay free.\nGo ad-free for less than you think."
                    },
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            if (com.pockethost.app.BuildConfig.IS_EXTERNAL_BUILD) {
                PatreonSupportCard(
                    onSupportClick = {
                        val patreonUri = Uri.parse("https://www.patreon.com/cw/pockethost")
                        val intent = Intent(Intent.ACTION_VIEW, patreonUri)
                        context.startActivity(intent)
                    }
                )

                Text(
                    text = "Voluntary contribution via Patreon · cancel your membership anytime",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                )
            } else {
                val isIndia = remember(offers) {
                // 1. Check if any offer returned by Google Play Store is INR
                val hasInrOffer = offers.any {
                    it.priceCurrencyCode.equals("INR", ignoreCase = true) ||
                    it.recurringPrice.contains("₹")
                }
                if (hasInrOffer) return@remember true

                // 2. Check SIM / cellular network country
                val telephony = context.getSystemService(android.content.Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager
                val simCountry = telephony?.simCountryIso?.lowercase()
                val netCountry = telephony?.networkCountryIso?.lowercase()
                if (simCountry == "in" || netCountry == "in") return@remember true

                // 3. Check timezone
                val tzId = java.util.TimeZone.getDefault().id
                if (tzId.contains("Kolkata", ignoreCase = true) || tzId.contains("Calcutta", ignoreCase = true) || tzId.contains("India", ignoreCase = true)) {
                    return@remember true
                }

                // 4. Check device locale / currency
                val locale = java.util.Locale.getDefault()
                val currency = runCatching { java.util.Currency.getInstance(locale) }.getOrNull()
                locale.country == "IN" || currency?.currencyCode == "INR"
            }

            val isUK = remember(offers) {
                val hasGbpOffer = offers.any {
                    it.priceCurrencyCode.equals("GBP", ignoreCase = true) ||
                    it.recurringPrice.contains("£")
                }
                if (hasGbpOffer) return@remember true
                val locale = java.util.Locale.getDefault()
                locale.country == "GB" || java.util.TimeZone.getDefault().id.contains("London", ignoreCase = true)
            }

            val isEuro = remember(offers) {
                val hasEurOffer = offers.any {
                    it.priceCurrencyCode.equals("EUR", ignoreCase = true) ||
                    it.recurringPrice.contains("€")
                }
                if (hasEurOffer) return@remember true
                val currency = runCatching { java.util.Currency.getInstance(java.util.Locale.getDefault()) }.getOrNull()
                currency?.currencyCode == "EUR"
            }

            val defaultMonthly = remember(isIndia, isUK, isEuro) {
                when {
                    isIndia -> fallbackOffer(BillingManager.PRODUCT_AD_FREE_MONTHLY, "Ad-free monthly", "₹89", PremiumTier.AD_FREE, 89_000_000L, "INR")
                    isUK -> fallbackOffer(BillingManager.PRODUCT_AD_FREE_MONTHLY, "Ad-free monthly", "£0.99", PremiumTier.AD_FREE, 990_000L, "GBP")
                    isEuro -> fallbackOffer(BillingManager.PRODUCT_AD_FREE_MONTHLY, "Ad-free monthly", "€0.99", PremiumTier.AD_FREE, 990_000L, "EUR")
                    else -> fallbackOffer(BillingManager.PRODUCT_AD_FREE_MONTHLY, "Ad-free monthly", "$0.99", PremiumTier.AD_FREE, 990_000L, "USD")
                }
            }
            val defaultYearly = remember(isIndia, isUK, isEuro) {
                when {
                    isIndia -> fallbackOffer(BillingManager.PRODUCT_AD_FREE_YEARLY, "Ad-free yearly", "₹299", PremiumTier.AD_FREE, 299_000_000L, "INR")
                    isUK -> fallbackOffer(BillingManager.PRODUCT_AD_FREE_YEARLY, "Ad-free yearly", "£2.99", PremiumTier.AD_FREE, 2_990_000L, "GBP")
                    isEuro -> fallbackOffer(BillingManager.PRODUCT_AD_FREE_YEARLY, "Ad-free yearly", "€2.99", PremiumTier.AD_FREE, 2_990_000L, "EUR")
                    else -> fallbackOffer(BillingManager.PRODUCT_AD_FREE_YEARLY, "Ad-free yearly", "$2.99", PremiumTier.AD_FREE, 2_990_000L, "USD")
                }
            }
            val defaultSupporter = remember(isIndia, isUK, isEuro) {
                when {
                    isIndia -> fallbackOffer(BillingManager.PRODUCT_SUPPORTER, "PocketHost Supporter", "₹499", PremiumTier.PREMIUM, 499_000_000L, "INR")
                    isUK -> fallbackOffer(BillingManager.PRODUCT_SUPPORTER, "PocketHost Supporter", "£4.99", PremiumTier.PREMIUM, 4_990_000L, "GBP")
                    isEuro -> fallbackOffer(BillingManager.PRODUCT_SUPPORTER, "PocketHost Supporter", "€4.99", PremiumTier.PREMIUM, 4_990_000L, "EUR")
                    else -> fallbackOffer(BillingManager.PRODUCT_SUPPORTER, "PocketHost Supporter", "$4.99", PremiumTier.PREMIUM, 4_990_000L, "USD")
                }
            }

            val monthlyOffer = offers.firstOrNull { it.productId == BillingManager.PRODUCT_AD_FREE_MONTHLY } ?: defaultMonthly
            val yearlyOffer = offers.firstOrNull { it.productId == BillingManager.PRODUCT_AD_FREE_YEARLY } ?: defaultYearly
            val supporterOffer = offers.firstOrNull {
                it.productId == BillingManager.PRODUCT_SUPPORTER || it.productId == BillingManager.PRODUCT_SUPPORTER_ALT
            } ?: defaultSupporter

            val supporterActive = entitlement.tier == PremiumTier.PREMIUM || entitlement.tier == PremiumTier.SUPPORTIVE
            val unknownAdFreePlan = entitlement.tier == PremiumTier.AD_FREE && activeProductIds.none {
                it == BillingManager.PRODUCT_AD_FREE_MONTHLY || it == BillingManager.PRODUCT_AD_FREE_YEARLY
            }

            val monthlyPriceClean = remember(monthlyOffer.recurringPrice) {
                monthlyOffer.recurringPrice.trim().replace(Regex("""([.,]00)(?=\s*($|[^0-9]))"""), "")
            }
            val yearlyPriceClean = remember(yearlyOffer.recurringPrice) {
                yearlyOffer.recurringPrice.trim().replace(Regex("""([.,]00)(?=\s*($|[^0-9]))"""), "")
            }
            val supporterPriceClean = remember(supporterOffer.recurringPrice) {
                supporterOffer.recurringPrice.trim().replace(Regex("""([.,]00)(?=\s*($|[^0-9]))"""), "")
            }

            val samplePrice = remember(yearlyOffer.recurringPrice, monthlyOffer.recurringPrice, supporterOffer.recurringPrice) {
                yearlyOffer.recurringPrice.ifEmpty { monthlyOffer.recurringPrice.ifEmpty { supporterOffer.recurringPrice } }.trim()
            }
            val currencyPrefix = remember(samplePrice) {
                val match = Regex("""^([^0-9\s]+)""").find(samplePrice)
                match?.value ?: if (Regex("""([^0-9\s]+)$""").containsMatchIn(samplePrice)) "" else "$"
            }
            val currencySuffix = remember(samplePrice) {
                val match = Regex("""([^0-9\s]+)$""").find(samplePrice)
                if (Regex("""^([^0-9\s]+)""").containsMatchIn(samplePrice)) "" else match?.value?.let { " $it" } ?: ""
            }

            val monthlyAmount = remember(monthlyOffer) {
                if (monthlyOffer.priceAmountMicros > 0L) {
                    monthlyOffer.priceAmountMicros / 1_000_000.0
                } else {
                    extractAmount(monthlyOffer.recurringPrice) ?: if (isIndia) 89.0 else 0.99
                }
            }
            val yearlyAmount = remember(yearlyOffer) {
                if (yearlyOffer.priceAmountMicros > 0L) {
                    yearlyOffer.priceAmountMicros / 1_000_000.0
                } else {
                    extractAmount(yearlyOffer.recurringPrice) ?: if (isIndia) 299.0 else 2.99
                }
            }

            val hasCents = remember(monthlyAmount, yearlyAmount, monthlyOffer, yearlyOffer) {
                (monthlyAmount % 1.0 != 0.0) ||
                (yearlyAmount % 1.0 != 0.0) ||
                (monthlyOffer.priceAmountMicros % 1_000_000L != 0L) ||
                (yearlyOffer.priceAmountMicros % 1_000_000L != 0L)
            }

            val total12Months = remember(monthlyAmount) { monthlyAmount * 12.0 }
            val total12MonthsText = remember(total12Months, currencyPrefix, currencySuffix, hasCents) {
                formatAmount(total12Months, currencyPrefix, currencySuffix, hasCents)
            }

            val savingsAmount = remember(total12Months, yearlyAmount) {
                (total12Months - yearlyAmount).coerceAtLeast(0.0)
            }
            val savingsText = remember(savingsAmount, currencyPrefix, currencySuffix, hasCents) {
                formatAmount(savingsAmount, currencyPrefix, currencySuffix, hasCents)
            }

            val savingsPercent = remember(total12Months, savingsAmount) {
                if (total12Months > 0.0) {
                    kotlin.math.round((savingsAmount / total12Months) * 100.0).toInt().coerceIn(10, 95)
                } else {
                    72
                }
            }
            val saveBadgeText = "SAVE $savingsPercent%"

            val monthlyEquiv = remember(yearlyAmount, monthlyAmount) {
                if (yearlyAmount > 0.0) yearlyAmount / 12.0 else monthlyAmount
            }
            val monthlyEquivText = remember(monthlyEquiv, currencyPrefix, currencySuffix, hasCents) {
                formatAmount(monthlyEquiv, currencyPrefix, currencySuffix, hasCents)
            }

            val dailyCost = remember(yearlyAmount) {
                if (yearlyAmount > 0.0) yearlyAmount / 365.0 else 0.0
            }
            val dailyTagText = remember(dailyCost, currencyPrefix, currencySuffix) {
                when {
                    dailyCost <= 1.0 -> "That's less than ${currencyPrefix}1${currencySuffix} a day!"
                    dailyCost <= 10.0 -> "That's less than ${currencyPrefix}10${currencySuffix} a day!"
                    dailyCost <= 100.0 -> "That's less than ${currencyPrefix}100${currencySuffix} a day!"
                    else -> "That's less than ${formatAmount(kotlin.math.ceil(dailyCost), currencyPrefix, currencySuffix, false)} a day!"
                }
            }

            val monthlyButtonLabel = when {
                purchasingProductId == monthlyOffer.productId -> "CONNECTING..."
                supporterActive -> "INCLUDED"
                monthlyOffer.productId in activeProductIds || unknownAdFreePlan -> "CURRENT PLAN"
                else -> "CHOOSE MONTHLY"
            }
            val monthlyEnabled = !supporterActive && !unknownAdFreePlan && monthlyOffer.productId !in activeProductIds && purchasingProductId == null

            val yearlyButtonLabel = when {
                purchasingProductId == yearlyOffer.productId -> "CONNECTING..."
                supporterActive -> "INCLUDED"
                yearlyOffer.productId in activeProductIds || unknownAdFreePlan -> "CURRENT PLAN"
                else -> "CHOOSE YEARLY"
            }
            val yearlyEnabled = !supporterActive && !unknownAdFreePlan && yearlyOffer.productId !in activeProductIds && purchasingProductId == null

            val supporterButtonLabel = when {
                purchasingProductId == supporterOffer.productId -> "CONNECTING..."
                supporterActive -> "SUPPORTER ACTIVE"
                else -> "BECOME A SUPPORTER"
            }
            val supporterEnabled = !supporterActive && purchasingProductId == null

            // ── Side-by-side Monthly & Yearly Cards ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(androidx.compose.foundation.layout.IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Monthly Card
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(18.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = Color(0xFF2E7D32).copy(alpha = 0.25f),
                            shape = RoundedCornerShape(18.dp)
                        )
                        .padding(12.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(Color(0xFF4CAF50).copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                                .border(1.dp, Color(0xFF4CAF50).copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_heart_outline),
                                contentDescription = null,
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "MONTHLY",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32),
                            letterSpacing = 0.5.sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = monthlyPriceClean,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 32.sp
                        )

                        Text(
                            text = "per month",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        androidx.compose.material3.HorizontalDivider(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Outlined.CheckCircleOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Cancel anytime",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))
                        Spacer(modifier = Modifier.height(14.dp))

                        // Secondary CTA — tactile framed button with clear readability
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .then(
                                    if (monthlyEnabled) Modifier
                                        .raisedBorder(
                                            color = Color(0xFF22C55E).copy(alpha = 0.55f),
                                            depthColor = Color(0xFF0F4525),
                                            cornerRadius = 21.dp,
                                            borderWidth = 1.5.dp,
                                            depthWidth = 3.dp
                                        )
                                        .clip(RoundedCornerShape(21.dp))
                                        .background(Color(0xFF132B1C))
                                    else Modifier
                                        .clip(RoundedCornerShape(21.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                )
                                .clickable(enabled = monthlyEnabled) { startPurchase(monthlyOffer) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = monthlyButtonLabel,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = com.pockethost.app.ui.theme.ButtonFont,
                                color = if (monthlyEnabled) Color(0xFF4ADE80) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                maxLines = 1
                            )
                        }
                    }
                }

                // Yearly Card — recommended, highlighted hero
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(
                            color = Color(0xFF22C55E).copy(alpha = 0.08f),
                            shape = RoundedCornerShape(18.dp)
                        )
                        .border(
                            width = 2.dp,
                            brush = Brush.verticalGradient(
                                listOf(Color(0xFF4ADE80), Color(0xFF16A34A))
                            ),
                            shape = RoundedCornerShape(18.dp)
                        )
                        .padding(12.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // SAVE badge — high-urgency flame orange gradient (complementary color to green)
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(Color(0xFFFF3D00), Color(0xFFFF9100))
                                        ),
                                        RoundedCornerShape(6.dp)
                                    )
                                    .padding(horizontal = 7.dp, vertical = 3.5.dp)
                            ) {
                                Text(
                                    text = saveBadgeText,
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.5.sp
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(Color(0xFF4CAF50).copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                                    .border(1.dp, Color(0xFF4CAF50).copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_heart_outline),
                                    contentDescription = null,
                                    tint = Color(0xFF2E7D32),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "YEARLY",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4ADE80),
                            letterSpacing = 0.5.sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = monthlyEquivText,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 32.sp
                        )

                        Text(
                            text = "per month equivalent",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        androidx.compose.material3.HorizontalDivider(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "${yearlyPriceClean} billed yearly",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(5.dp))

                        Box(
                            modifier = Modifier
                                .background(Color(0xFF4CAF50).copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.5.dp)
                        ) {
                            Text(
                                text = dailyTagText,
                                color = Color(0xFF4ADE80),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))
                        Spacer(modifier = Modifier.height(10.dp))

                        // Primary CTA — vibrant 3D emerald gradient that POPS
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .then(
                                    if (yearlyEnabled) Modifier
                                        .raisedBorder(
                                            color = Color(0xFF86EFAC),
                                            depthColor = Color(0xFF15803D),
                                            cornerRadius = 22.dp,
                                            borderWidth = 1.5.dp,
                                            depthWidth = 3.5.dp
                                        )
                                        .clip(RoundedCornerShape(22.dp))
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(Color(0xFF4ADE80), Color(0xFF22C55E))
                                            )
                                        )
                                    else Modifier
                                        .clip(RoundedCornerShape(22.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                )
                                .clickable(enabled = yearlyEnabled) { startPurchase(yearlyOffer) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = yearlyButtonLabel,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = com.pockethost.app.ui.theme.ButtonFont,
                                color = if (yearlyEnabled) Color(0xFF052E16) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // ── Comparison Savings Banner ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFF4CAF50).copy(alpha = 0.18f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Filled.TrendingUp,
                            contentDescription = null,
                            tint = Color(0xFF00E676),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "12 months of monthly payments: ",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = total12MonthsText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.drawWithContent {
                                    drawContent()
                                    drawLine(
                                        color = Color(0xFFD32F2F),
                                        start = Offset(0f, size.height * 0.52f),
                                        end = Offset(size.width, size.height * 0.52f),
                                        strokeWidth = 2.dp.toPx()
                                    )
                                }
                            )
                        }
                        Text(
                            text = "You save $savingsText with yearly!",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32)
                        )
                    }
                }
            }

            // ── PocketHost Supporter Card ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(18.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(18.dp)
                    )
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color(0xFFFF9800).copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                                    .border(1.dp, Color(0xFFFF9800).copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_heart_outline),
                                    contentDescription = null,
                                    tint = Color(0xFFF57C00),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Text(
                                text = "PocketHost Supporter",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                fontFamily = Monocraft,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(Color(0xFFFF9800), Color(0xFFFF5722))
                                        )
                                    )
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "SUP",
                                    color = Color.White,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = supporterPriceClean,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF57C00),
                                fontSize = 16.sp,
                                fontFamily = Monocraft,
                                maxLines = 1
                            )
                            Text(
                                text = " /mo",
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.5.sp,
                                maxLines = 1
                            )
                        }
                    }

                    Text(
                        text = "Help keep PocketHost relays and development alive.\nIncludes ad-free access and priority support.",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )

                    // Supporter CTA — warm golden amber 3D tactile button that stands out proudly
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .then(
                                if (supporterEnabled) Modifier
                                    .raisedBorder(
                                        color = Color(0xFFFDE68A),
                                        depthColor = Color(0xFFB45309),
                                        cornerRadius = 22.dp,
                                        borderWidth = 1.5.dp,
                                        depthWidth = 3.5.dp
                                    )
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color(0xFFFBBF24), Color(0xFFF59E0B))
                                        )
                                    )
                                else Modifier
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            )
                            .clickable(enabled = supporterEnabled) { startPurchase(supporterOffer) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = supporterButtonLabel,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = com.pockethost.app.ui.theme.ButtonFont,
                            color = if (supporterEnabled) Color(0xFF451A03) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            maxLines = 1
                        )
                    }
                }
            }

            com.pockethost.app.ads.WatchAdSupportButton()

            // ── Footer: Restore Purchases (Subtle secondary action so it does not compete with CTAs) ──
            androidx.compose.material3.TextButton(
                onClick = {
                    billingManager.restorePurchases { result ->
                        Toast.makeText(context, result, Toast.LENGTH_LONG).show()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
            ) {
                Text(
                    text = "RESTORE PURCHASES",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = com.pockethost.app.ui.theme.ButtonFont,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    letterSpacing = 0.5.sp
                )
            }

            Text(
                text = "Subscriptions renew automatically until canceled in Google Play.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            )
            }
        }
    }
}

@Composable
private fun PatreonSupportCard(
    onSupportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(18.dp)
            )
            .border(
                width = 1.5.dp,
                brush = Brush.verticalGradient(
                    listOf(Color(0xFFFF5E5B), Color(0xFFFF424D).copy(alpha = 0.45f))
                ),
                shape = RoundedCornerShape(18.dp)
            )
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(Color(0xFFFF424D).copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFFFF424D).copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_heart_outline),
                            contentDescription = null,
                            tint = Color(0xFFFF424D),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Become a Patron",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            fontFamily = Monocraft,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "patreon.com/cw/pockethost",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFFF6978)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFFFF5E5B), Color(0xFFFF424D))
                            )
                        )
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "PATREON",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Text(
                text = "PocketHost is 100% free and open-source. This external build has zero ads and zero locked server features.\n\nYour monthly patronage directly funds our high-speed global relay nodes, covers server bandwidth, and keeps active development alive.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 17.sp
            )

            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )

            // Tactile 3D button that pops
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .raisedBorder(
                        color = Color(0xFFFF858D),
                        depthColor = Color(0xFF991B1E),
                        cornerRadius = 23.dp,
                        borderWidth = 1.5.dp,
                        depthWidth = 3.5.dp
                    )
                    .clip(RoundedCornerShape(23.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFFFF5E5B), Color(0xFFFF424D))
                        )
                    )
                    .clickable { onSupportClick() },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_heart_outline),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "SUPPORT US ON PATREON",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = ButtonFont,
                        color = Color.White,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun fallbackOffer(
    productId: String,
    title: String,
    price: String,
    tier: PremiumTier,
    priceAmountMicros: Long = 0L,
    priceCurrencyCode: String = ""
) = SubscriptionOffer(
    productId = productId,
    title = title,
    price = price,
    recurringPrice = price,
    tier = tier,
    description = "",
    offerToken = "",
    priceAmountMicros = priceAmountMicros,
    priceCurrencyCode = priceCurrencyCode
)

private fun extractAmount(priceString: String): Double? {
    val cleaned = priceString.replace(Regex("""[^0-9.,]"""), "").replace(",", ".")
    return cleaned.toDoubleOrNull()
}

private fun formatAmount(amount: Double, prefix: String, suffix: String, hasCents: Boolean): String {
    val numStr = if (hasCents && (amount < 100.0 || amount % 1.0 != 0.0)) {
        String.format(java.util.Locale.US, "%,.2f", amount)
    } else {
        String.format(java.util.Locale.US, "%,d", kotlin.math.round(amount).toLong())
    }
    return "$prefix$numStr$suffix"
}

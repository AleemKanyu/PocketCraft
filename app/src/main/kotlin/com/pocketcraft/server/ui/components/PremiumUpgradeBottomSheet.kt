package com.pocketcraft.server.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.pocketcraft.server.billing.BillingManager
import com.pocketcraft.server.billing.PremiumTier
import com.pocketcraft.server.ui.theme.Monocraft
import com.pocketcraft.server.ui.theme.PocketColors
import kotlinx.coroutines.launch

private val proGradientColors = listOf(
    Color(0xFF7B1FA2), // deep purple
    Color(0xFFAB47BC), // medium purple
    Color(0xFFFF6F00), // amber
    Color(0xFFFFB300)  // gold
)

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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var purchasingProductId by remember { mutableStateOf<String?>(null) }
    val currentUser = FirebaseAuth.getInstance().currentUser

    LaunchedEffect(Unit) {
        billingManager.queryAvailableProducts()
    }

    LaunchedEffect(lastMessage) {
        val message = lastMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        billingManager.clearMessage()
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
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // ── Premium gradient header ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(75.dp)
                    .background(
                        brush = Brush.linearGradient(colors = proGradientColors),
                        shape = RoundedCornerShape(16.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color.White.copy(alpha = 0.18f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = buildAnnotatedString {
                            append("PocketCraft ")
                            withStyle(SpanStyle(
                                color = Color(0xFFFFD700)
                            )) {
                                append("Pro")
                            }
                        },
                        fontSize = when {
                            androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 360 -> 14.sp
                            androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 400 -> 17.sp
                            else -> 20.sp
                        },
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = Monocraft,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Choose the plan that fits you best, or become a Member to help support PocketCraft more directly.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Cancel anytime with secure Google Play billing.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                if (currentUser == null) {
                    Text(
                        text = "Sign in with your PocketCraft account before subscribing so the purchase can be linked to your UID.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (offers.isEmpty()) {
                    Text(
                        text = "Loading Play Store pricing...",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    offers.forEach { offer ->
                        val isCurrentTier = entitlement.tier == offer.tier
                        val isProOnly = entitlement.tier == PremiumTier.PREMIUM

                        val buttonLabel = when {
                            purchasingProductId == offer.productId -> "CONNECTING..."
                            isCurrentTier -> "ACTIVE SUBSCRIPTION"
                            isProOnly && offer.tier == PremiumTier.SUPPORTIVE -> "UPGRADE MEMBERSHIP"
                            offer.tier == PremiumTier.SUPPORTIVE -> "BECOME MEMBER"
                            offer.tier == PremiumTier.PREMIUM -> {
                                if (entitlement.eligibleForFreeTrial) {
                                    "START FREE, CANCEL ANYTIME"
                                } else {
                                    "GET PRO"
                                }
                            }
                            else -> "GET PRO"
                        }

                        val enabled = !isCurrentTier && purchasingProductId == null

                        SubscriptionOfferCard(
                            title = displayTitleForTier(offer.tier),
                            price = displayPriceForTier(offer.tier),
                            tier = offer.tier,
                            buttonLabel = buttonLabel,
                            eligibleForFreeTrial = entitlement.eligibleForFreeTrial,
                            showMostPopular = offer.tier == PremiumTier.PREMIUM,
                            enabled = enabled,
                            onClick = {
                                if (currentUser == null) {
                                    Toast.makeText(context, "Please sign up or sign in to continue.", Toast.LENGTH_LONG).show()
                                    onNavigateToSignUp()
                                    return@SubscriptionOfferCard
                                }
                                val activity = context.findActivity()
                                if (activity == null) {
                                    Toast.makeText(context, "Could not launch purchase: invalid activity.", Toast.LENGTH_LONG).show()
                                    return@SubscriptionOfferCard
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
                        )
                    }
                }

                DuoButton(
                    text = "MAYBE LATER",
                    onClick = {
                        scope.launch {
                            sheetState.hide()
                            onDismissRequest()
                        }
                    },
                    variant = DuoButtonVariant.SecondaryGray,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    minHeight = 38.dp
                )

                if (entitlement.eligibleForFreeTrial) {
                    Text(
                        text = "After your 7-day free trial, you will be automatically charged ₹89/month unless canceled.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                    )
                } else {
                    Text(
                        text = "You will be automatically charged ₹89/month unless canceled.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                    )
                }

                Text(
                    text = "You can manage or upgrade your plan any time from Account Settings.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                )
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun displayPriceForTier(tier: PremiumTier): String {
    return when (tier) {
        PremiumTier.PREMIUM -> "₹89"
        PremiumTier.SUPPORTIVE -> "₹299"
        PremiumTier.NONE -> ""
    }
}

private fun displayTitleForTier(tier: PremiumTier): String {
    return when (tier) {
        PremiumTier.PREMIUM -> "Pro"
        PremiumTier.SUPPORTIVE -> "Member"
        PremiumTier.NONE -> ""
    }
}

private fun buildOfferSubtitle(tier: PremiumTier, eligibleForFreeTrial: Boolean): String {
    return when (tier) {
        PremiumTier.PREMIUM -> {
            if (eligibleForFreeTrial) {
                "Try 7 days free, then ₹89/month. Cancel anytime in Google Play."
            } else {
                "PocketCraft Pro at ₹89/month. Cancel anytime in Google Play."
            }
        }
        PremiumTier.SUPPORTIVE -> "Everything in Pro, plus a simple way to support PocketCraft directly."
        PremiumTier.NONE -> ""
    }
}

private fun benefitsForTier(tier: PremiumTier): List<String> {
    return when (tier) {
        PremiumTier.PREMIUM -> listOf(
            "Custom IP",
            "Custom Theme Maker",
            "Operator Chat from App",
            "More world slots",
            "More players (up to 50)"
        )
        PremiumTier.SUPPORTIVE -> listOf(
            "Pro features included",
            "Support the project",
            "Priority community access",
            "Member Discord badge"
        )
        PremiumTier.NONE -> emptyList()
    }
}

@Composable
private fun SubscriptionOfferCard(
    title: String,
    price: String,
    tier: PremiumTier,
    buttonLabel: String,
    eligibleForFreeTrial: Boolean = false,
    showMostPopular: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    GameCard(modifier = Modifier.fillMaxWidth()) {
        val isMember = tier == PremiumTier.SUPPORTIVE
        val effectiveSubtitle = buildOfferSubtitle(tier, eligibleForFreeTrial)
        val benefitItems = benefitsForTier(tier)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (showMostPopular) {
                val badgeText = if (tier == PremiumTier.PREMIUM && eligibleForFreeTrial) {
                    "MOST POPULAR · 7 DAYS FREE"
                } else {
                    "MOST POPULAR"
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(Color(0xFF8C4DFF), Color(0xFF5E17EB))
                                )
                            )
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = badgeText,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = if (isMember) Color(0xFFFFB300) else PocketColors.Primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, fontFamily = Monocraft)
                    Text(
                        effectiveSubtitle,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.74f),
                        lineHeight = 15.sp
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    if (tier == PremiumTier.PREMIUM && eligibleForFreeTrial) {
                        Text(
                            text = price,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            fontSize = 14.sp,
                            style = androidx.compose.ui.text.TextStyle(
                                textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough
                            )
                        )
                        Text(
                            text = "Free today",
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF00C853),
                            fontSize = 15.sp
                        )
                        Text(
                            text = "then ₹89/month",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            fontSize = 9.sp
                        )
                    } else {
                        Text(
                            price,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isMember) Color(0xFFE28026) else PocketColors.Primary,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "/month",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                            fontSize = 11.sp
                        )
                    }
                }
            }
            if (benefitItems.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    benefitItems.forEach { benefit ->
                        OfferFeatureRow(
                            text = benefit,
                            highlight = isMember && benefit == "Pro features included"
                        )
                    }
                }
            }
            DuoButton(
                text = buttonLabel,
                onClick = onClick,
                variant = if (enabled) {
                    if (isMember) DuoButtonVariant.Warning else DuoButtonVariant.Primary
                } else {
                    DuoButtonVariant.Secondary
                },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                minHeight = 38.dp,
                fontWeight = FontWeight.Bold,
                backgroundBrush = if (enabled) {
                    if (isMember) {
                        Brush.linearGradient(
                            colors = listOf(Color(0xFFF2994A), Color(0xFFE28026))
                        )
                    } else {
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF8C4DFF), Color(0xFF5E17EB))
                        )
                    }
                } else {
                    null
                }
            )
            if (tier == PremiumTier.PREMIUM && eligibleForFreeTrial && enabled) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "No charge today · cancel anytime in Google Play.",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun OfferFeatureRow(
    text: String,
    highlight: Boolean = false
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = if (highlight) Color(0xFFF2994A) else PocketColors.Primary,
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f)
        )
        if (text == "Custom IP") {
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Color(0xFF8C4DFF), Color(0xFF5E17EB))
                        )
                    )
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "NEW",
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

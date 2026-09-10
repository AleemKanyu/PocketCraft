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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.pockethost.app.R
import com.pockethost.app.billing.BillingManager
import com.pockethost.app.billing.PremiumTier
import com.pockethost.app.billing.SubscriptionOffer
import com.pockethost.app.ui.theme.Monocraft
import com.pockethost.app.ui.theme.PocketColors
import kotlinx.coroutines.launch

private const val KOFI_URL = "https://ko-fi.com/aleemkanyu"

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
                    text = "100% free & open source. All server features are unlocked for everyone. Voluntary donations help fund server hosting and continuous updates.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            if (currentUser == null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { onNavigateToSignUp() }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Sign in first so your donation is linked to your account.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 15.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "SIGN IN",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = PocketColors.Primary
                    )
                }
            }

            // Option 1: Monthly Donation (₹399/mo)
            val supporterOffer = offers.firstOrNull { it.productId == BillingManager.PRODUCT_PREMIUM }
                ?: remember {
                    SubscriptionOffer(
                        productId = BillingManager.PRODUCT_PREMIUM,
                        title = "Monthly Donation",
                        price = "₹399",
                        recurringPrice = "₹399",
                        tier = PremiumTier.PREMIUM,
                        description = "Voluntary monthly contribution to support PocketHost relays and development.",
                        freeTrialDays = 0,
                        offerToken = ""
                    )
                }

            val cleanRecurringPrice = remember(supporterOffer.recurringPrice) {
                supporterOffer.recurringPrice.trim().replace(Regex("""([.,]00)(?=\s*($|[^0-9]))"""), "")
            }
            val isCurrentTier = entitlement.tier == PremiumTier.PREMIUM || entitlement.tier == PremiumTier.SUPPORTIVE
            val buttonLabel = when {
                purchasingProductId == supporterOffer.productId -> "CONNECTING..."
                isCurrentTier -> "ACTIVE DONOR"
                else -> "DONATE $cleanRecurringPrice/MO"
            }
            val enabled = !isCurrentTier && purchasingProductId == null

            MonthlyDonationCard(
                price = cleanRecurringPrice,
                buttonLabel = buttonLabel,
                enabled = enabled,
                onClick = {
                    if (currentUser == null) {
                        Toast.makeText(context, "Please sign up or sign in to continue.", Toast.LENGTH_LONG).show()
                        onNavigateToSignUp()
                        return@MonthlyDonationCard
                    }
                    val activity = context.findActivity()
                    if (activity == null) {
                        Toast.makeText(context, "Could not launch purchase: invalid activity.", Toast.LENGTH_LONG).show()
                        return@MonthlyDonationCard
                    }
                    purchasingProductId = supporterOffer.productId
                    billingManager.launchBillingFlow(activity, supporterOffer.productId) { error ->
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

            // Option 2: Ko-fi Donation
            KofiDonationCard(
                onClick = {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(KOFI_URL))
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        Toast.makeText(context, "Could not open browser.", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            // ── Footer ──
            DuoButton(
                text = "RESTORE PURCHASES",
                onClick = {
                    billingManager.restorePurchases { result ->
                        Toast.makeText(context, result, Toast.LENGTH_LONG).show()
                    }
                },
                variant = DuoButtonVariant.Secondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                minHeight = 36.dp
            )

            Text(
                text = "Voluntary contribution · Cancel monthly donation anytime in Google Play · External link for Ko-fi",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun MonthlyDonationCard(
    price: String,
    buttonLabel: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val accentColor = PocketColors.Primary
    val cleanPrice = remember(price) {
        price.trim().replace(Regex("""([.,]00)(?=\s*($|[^0-9]))"""), "")
    }

    GameCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Top Row: Title + Badge + Price
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
                            .size(34.dp)
                            .background(accentColor.copy(alpha = 0.12f), RoundedCornerShape(9.dp))
                            .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(9.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_heart_outline),
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Monthly Donation",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.5.sp,
                            fontFamily = Monocraft,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFF8C4DFF), Color(0xFF5E17EB))
                                    )
                                )
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "MONTHLY",
                                color = Color.White,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.padding(start = 6.dp)
                ) {
                    Text(
                        text = cleanPrice,
                        fontWeight = FontWeight.ExtraBold,
                        color = accentColor,
                        fontSize = 15.sp,
                        fontFamily = Monocraft,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = "/mo",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Text(
                text = "Voluntary monthly contribution to help fund server relays, infrastructure, and open-source updates.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
                lineHeight = 17.sp
            )

            // Action Button
            DuoButton(
                text = buttonLabel,
                onClick = onClick,
                variant = if (enabled) DuoButtonVariant.Primary else DuoButtonVariant.Secondary,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                minHeight = 38.dp,
                fontWeight = FontWeight.Bold,
                backgroundBrush = if (enabled) {
                    Brush.linearGradient(
                        listOf(Color(0xFF8C4DFF), Color(0xFF5E17EB))
                    )
                } else {
                    null
                }
            )
        }
    }
}

@Composable
private fun KofiDonationCard(
    onClick: () -> Unit
) {
    val kofiColor = Color(0xFFFF5E5B)

    GameCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Top Row: Title + Badge + Type
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
                            .size(34.dp)
                            .background(kofiColor.copy(alpha = 0.12f), RoundedCornerShape(9.dp))
                            .border(1.dp, kofiColor.copy(alpha = 0.4f), RoundedCornerShape(9.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            tint = kofiColor,
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Ko-fi Donation",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.5.sp,
                            fontFamily = Monocraft,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFFFF5E5B), Color(0xFFFF416C))
                                    )
                                )
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "ONE-TIME",
                                color = Color.White,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1
                            )
                        }
                    }
                }

                Text(
                    text = "Custom",
                    fontWeight = FontWeight.ExtraBold,
                    color = kofiColor,
                    fontSize = 15.sp,
                    fontFamily = Monocraft,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }

            Text(
                text = "Direct one-time voluntary donation of any custom amount via card, PayPal, or UPI on Ko-fi.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
                lineHeight = 17.sp
            )

            // Action Button
            DuoButton(
                text = "DONATE ON KO-FI",
                onClick = onClick,
                variant = DuoButtonVariant.Warning,
                enabled = true,
                modifier = Modifier.fillMaxWidth(),
                minHeight = 38.dp,
                fontWeight = FontWeight.Bold,
                backgroundBrush = Brush.linearGradient(
                    listOf(Color(0xFFFF5E5B), Color(0xFFFF416C))
                )
            )
        }
    }
}

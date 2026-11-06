package com.yamahamx.editor.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yamahamx.editor.R
import com.yamahamx.editor.ui.theme.*
import kotlinx.coroutines.delay

/**
 * Paywall dialog shown on startup for Free users.
 * Features a 5-second countdown timer before allowing the user to dismiss and continue in Free mode.
 */
@Composable
fun ProPaywallDialog(
    price: String? = null,
    onUnlockProClicked: () -> Unit,
    onContinueFreeClicked: () -> Unit,
    onRestorePurchasesClicked: (() -> Unit)? = null
) {
    var secondsRemaining by rememberSaveable { mutableIntStateOf(5) }

    LaunchedEffect(Unit) {
        while (secondsRemaining > 0) {
            delay(1000L)
            secondsRemaining--
        }
    }

    Dialog(
        onDismissRequest = {
            if (secondsRemaining <= 0) {
                onContinueFreeClicked()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = (secondsRemaining <= 0),
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .clip(RoundedCornerShape(20.dp)),
            color = SynthDarkBackground,
            border = BorderStroke(1.5.dp, Brush.horizontalGradient(listOf(YamahaCyan, YamahaGold))),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Pro Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(YamahaCyan.copy(alpha = 0.25f), YamahaGold.copy(alpha = 0.25f))
                            )
                        )
                        .border(
                            1.dp,
                            Brush.horizontalGradient(listOf(YamahaCyan, YamahaGold)),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.paywall_badge),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = YamahaGold,
                        letterSpacing = 0.5.sp
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Title & Subtitle
                Text(
                    text = stringResource(R.string.paywall_title),
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = stringResource(R.string.paywall_subtitle),
                    fontSize = 12.sp,
                    color = SynthMutedGray,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Exclusive Pro Benefits List
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SynthCardBackground)
                        .border(1.dp, SynthCardBorder, RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PaywallFeatureRow(
                        icon = Icons.Default.Layers,
                        iconTint = YamahaCyan,
                        title = stringResource(R.string.paywall_benefit_layer_title),
                        description = stringResource(R.string.paywall_benefit_layer)
                    )
                    HorizontalDivider(color = SynthCardBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
                    PaywallFeatureRow(
                        icon = Icons.Default.Bluetooth,
                        iconTint = Color(0xFF2979FF),
                        title = stringResource(R.string.paywall_benefit_ble_title),
                        description = stringResource(R.string.paywall_benefit_ble)
                    )
                    HorizontalDivider(color = SynthCardBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
                    PaywallFeatureRow(
                        icon = Icons.Default.Tune,
                        iconTint = YamahaGold,
                        title = stringResource(R.string.paywall_benefit_support_title),
                        description = stringResource(R.string.paywall_benefit_support)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Immediate Action Button ("Garantir Versão Pro" / "Get Pro Version")
                Button(
                    onClick = onUnlockProClicked,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = YamahaCyan,
                        contentColor = Color.Black
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (price.isNullOrBlank()) {
                            stringResource(R.string.paywall_btn_pro)
                        } else {
                            stringResource(R.string.paywall_btn_pro_with_price, price)
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Close / Continue Button with 5s countdown
                OutlinedButton(
                    onClick = onContinueFreeClicked,
                    enabled = secondsRemaining <= 0,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(
                        1.dp,
                        if (secondsRemaining <= 0) SynthMutedGray.copy(alpha = 0.6f) else SynthCardBorder.copy(alpha = 0.4f)
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (secondsRemaining <= 0) Color.White else SynthMutedGray.copy(alpha = 0.5f),
                        disabledContentColor = SynthMutedGray.copy(alpha = 0.5f)
                    )
                ) {
                    if (secondsRemaining > 0) {
                        Icon(
                            imageVector = Icons.Default.HourglassEmpty,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = SynthMutedGray.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.paywall_btn_wait, secondsRemaining),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = SynthMutedGray.copy(alpha = 0.5f)
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.paywall_btn_continue),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }

                // Optional Restore Purchases Button
                if (onRestorePurchasesClicked != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    TextButton(
                        onClick = onRestorePurchasesClicked,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.btn_restore_purchases),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = SynthMutedGray
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PaywallFeatureRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = description,
                fontSize = 11.sp,
                color = SynthMutedGray,
                lineHeight = 14.sp
            )
        }
    }
}

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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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

@Composable
fun UpgradeToProDialog(
    price: String,
    onBuyClicked: () -> Unit,
    onRestoreClicked: () -> Unit,
    onDismissRequest: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
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
                // Header with Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.pro_badge_lifetime),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = YamahaGold,
                            letterSpacing = 0.5.sp
                        )
                    }

                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.btn_close),
                            tint = SynthMutedGray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Title
                Text(
                    text = stringResource(R.string.pro_dialog_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    lineHeight = 23.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = stringResource(R.string.pro_dialog_subtitle),
                    fontSize = 12.sp,
                    color = SynthMutedGray,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Feature Highlights
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SynthCardBackground)
                        .border(1.dp, SynthCardBorder, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ProFeatureRow(
                        icon = Icons.Default.Equalizer,
                        iconTint = YamahaCyan,
                        title = stringResource(R.string.pro_feature_16parts_title),
                        description = stringResource(R.string.pro_feature_16parts_desc)
                    )
                    Divider(color = SynthCardBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
                    ProFeatureRow(
                        icon = Icons.Default.LibraryMusic,
                        iconTint = YamahaGold,
                        title = stringResource(R.string.pro_feature_setlist_title),
                        description = stringResource(R.string.pro_feature_setlist_desc)
                    )
                    Divider(color = SynthCardBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
                    ProFeatureRow(
                        icon = Icons.Default.Bluetooth,
                        iconTint = Color(0xFF2979FF),
                        title = stringResource(R.string.pro_feature_bluetooth_title),
                        description = stringResource(R.string.pro_feature_bluetooth_desc)
                    )
                    Divider(color = SynthCardBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
                    ProFeatureRow(
                        icon = Icons.Default.FlashOn,
                        iconTint = YamahaCyan,
                        title = stringResource(R.string.pro_feature_layers_title),
                        description = stringResource(R.string.pro_feature_layers_desc)
                    )
                    Divider(color = SynthCardBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
                    ProFeatureRow(
                        icon = Icons.Default.AllInclusive,
                        iconTint = SynthGreen,
                        title = stringResource(R.string.pro_feature_license_title),
                        description = stringResource(R.string.pro_feature_license_desc)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Price and Buy Button
                Button(
                    onClick = onBuyClicked,
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
                        imageVector = Icons.Default.ShoppingCart,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.btn_unlock_pro, price),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Restore Purchases Button
                TextButton(
                    onClick = onRestoreClicked,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.btn_restore_purchases),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = SynthMutedGray
                    )
                }
            }
        }
    }
}

@Composable
private fun ProFeatureRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = description,
                fontSize = 10.5.sp,
                color = SynthMutedGray,
                lineHeight = 14.sp
            )
        }
    }
}

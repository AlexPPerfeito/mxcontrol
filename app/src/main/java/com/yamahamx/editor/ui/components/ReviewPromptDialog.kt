package com.yamahamx.editor.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yamahamx.editor.R
import com.yamahamx.editor.ui.theme.*

/**
 * Non-intrusive modal dialog prompting users to rate and review MX Control on Google Play Store.
 * Follows the dark synthesizer visual theme of MX Control with custom Gold and Cyan accents.
 */
@Composable
fun ReviewPromptDialog(
    onRateClicked: () -> Unit,
    onRemindLaterClicked: () -> Unit,
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.border(
            BorderStroke(1.dp, SynthCardBorder),
            shape = RoundedCornerShape(16.dp)
        ),
        icon = {
            Icon(
                imageVector = Icons.Rounded.Star,
                contentDescription = null,
                tint = YamahaGold,
                modifier = Modifier.size(40.dp)
            )
        },
        title = {
            Text(
                text = stringResource(R.string.review_prompt_title),
                color = SynthLightText,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Text(
                text = stringResource(R.string.review_prompt_message),
                color = SynthMutedGray,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center
            )
        },
        confirmButton = {
            Button(
                onClick = onRateClicked,
                colors = ButtonDefaults.buttonColors(
                    containerColor = YamahaCyan,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.review_prompt_btn_rate),
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onRemindLaterClicked
            ) {
                Text(
                    text = stringResource(R.string.review_prompt_btn_later),
                    color = SynthMutedGray
                )
            }
        },
        containerColor = SynthCardBackground,
        shape = RoundedCornerShape(16.dp)
    )
}

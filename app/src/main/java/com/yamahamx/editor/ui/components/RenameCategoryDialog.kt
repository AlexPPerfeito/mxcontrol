package com.yamahamx.editor.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yamahamx.editor.R
import com.yamahamx.editor.ui.theme.YamahaGold
import com.yamahamx.editor.ui.theme.YamahaNavyDark
import com.yamahamx.editor.ui.theme.YamahaNavyLight

@Composable
fun RenameCategoryDialog(
    currentCategory: String,
    existingCategories: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (newName: String) -> Unit
) {
    val context = LocalContext.current
    var textFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = currentCategory,
                selection = TextRange(0, currentCategory.length)
            )
        )
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = YamahaNavyLight,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    tint = YamahaGold
                )
                Text(
                    text = stringResource(R.string.dialog_rename_category_title),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.dialog_rename_category_prompt, currentCategory),
                    color = Color.LightGray,
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = textFieldValue,
                    onValueChange = {
                        textFieldValue = it
                        errorMessage = null
                    },
                    singleLine = true,
                    isError = errorMessage != null,
                    supportingText = {
                        if (errorMessage != null) {
                            Text(
                                text = errorMessage!!,
                                color = Color(0xFFFF5252),
                                fontSize = 11.sp
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = YamahaGold,
                        unfocusedBorderColor = Color(0xFF2A344A),
                        errorBorderColor = Color(0xFFFF5252),
                        focusedContainerColor = YamahaNavyDark,
                        unfocusedContainerColor = YamahaNavyDark,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = textFieldValue.text.trim()
                    when {
                        trimmed.isBlank() -> {
                            errorMessage = context.getString(R.string.error_category_empty)
                        }
                        trimmed.equals("TODAS", ignoreCase = true) -> {
                            errorMessage = context.getString(R.string.error_category_reserved_all)
                        }
                        trimmed.equals(currentCategory, ignoreCase = true) -> {
                            // Sem alteração real, apenas fecha
                            onDismiss()
                        }
                        existingCategories.any { it.equals(trimmed, ignoreCase = true) && !it.equals(currentCategory, ignoreCase = true) } -> {
                            errorMessage = context.getString(R.string.error_category_already_exists)
                        }
                        else -> {
                            onConfirm(trimmed)
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = YamahaGold),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.btn_ok),
                    color = Color.Black,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.btn_cancel),
                    color = Color.Gray
                )
            }
        }
    )
}

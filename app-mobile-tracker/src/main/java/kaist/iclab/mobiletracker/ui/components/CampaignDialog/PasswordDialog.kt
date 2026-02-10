package kaist.iclab.mobiletracker.ui.components.CampaignDialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kaist.iclab.mobiletracker.R
import kaist.iclab.mobiletracker.ui.components.Popup.DialogButtonConfig
import kaist.iclab.mobiletracker.ui.components.Popup.PopupDialog
import kaist.iclab.mobiletracker.ui.theme.AppColors
import kaist.iclab.mobiletracker.utils.AppToast
import kotlinx.coroutines.launch

/**
 * Dialog for entering campaign password
 */
@Composable
fun PasswordDialog(
    campaignName: String,
    onDismiss: () -> Unit,
    onVerify: suspend (String) -> Boolean,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    var password by remember { mutableStateOf("") }
    var isVerifying by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    PopupDialog(
        title = context.getString(R.string.campaign_password_title),
        content = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = context.getString(R.string.campaign_password_description),
                    fontSize = Styles.ExperimentNameFontSize,
                    color = AppColors.TextSecondary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        passwordError = null
                    },
                    label = { Text(context.getString(R.string.campaign_password_input_label)) },
                    singleLine = true,
                    isError = passwordError != null,
                    supportingText = {
                        if (passwordError != null) {
                            Text(
                                text = passwordError!!,
                                color = AppColors.ErrorColor
                            )
                        }
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        primaryButton = DialogButtonConfig(
            text = context.getString(R.string.campaign_password_verify),
            onClick = {
                if (password.isNotEmpty()) {
                    isVerifying = true
                    passwordError = null
                    coroutineScope.launch {
                        try {
                            val isValid = onVerify(password)
                            if (isValid) {
                                onSuccess()
                            } else {
                                passwordError = context.getString(R.string.campaign_password_invalid)
                                AppToast.show(context, R.string.campaign_password_invalid)
                            }
                        } catch (e: Exception) {
                            passwordError = context.getString(R.string.campaign_password_error)
                        } finally {
                            isVerifying = false
                        }
                    }
                }
            },
            enabled = !isVerifying && password.isNotEmpty()
        ),
        secondaryButton = DialogButtonConfig(
            text = context.getString(R.string.campaign_dialog_cancel),
            onClick = {
                onDismiss()
            },
            isPrimary = false
        ),
        onDismiss = onDismiss
    )
}

package dev.pocket.notebook.feature.library

import dev.pocket.notebook.feature.files.LocalPocketTools
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.pocket.notebook.i18n.AppStrings
import dev.pocket.notebook.data.security.SecurityAction

/** Password is submitted as CharArray and never stored in SavedStateHandle or preference files. */
@Composable
fun NotebookSecurityDialog(
    id: String, action: SecurityAction, strings: AppStrings,
    busy: Boolean, error: String?, onDismiss: () -> Unit,
    targetLabel:String? = null,
    onSubmit: (CharArray) -> Unit
) {
    // NOT rememberSaveable: never put a credential into the SavedState Bundle.
    var repeated by remember(id, action) { mutableStateOf("") }
    var password by androidx.compose.runtime.remember(id, action) { mutableStateOf("") }
    val needsPassword = action != SecurityAction.REMOVE
    val baseValid = !needsPassword || (if (action == SecurityAction.UNLOCK) password.isNotEmpty() else password.length >= 8)
    val valid=baseValid && (action !in setOf(SecurityAction.SET,SecurityAction.CHANGE) || password==repeated)
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(when (action) {
            SecurityAction.UNLOCK -> dev.pocket.notebook.feature.files.LocalPocketTools.current.text(dev.pocket.notebook.R.string.fs_unlock)
            SecurityAction.SET -> strings.setPassword
            SecurityAction.CHANGE -> strings.changePassword
            SecurityAction.REMOVE -> strings.removePassword
        }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if(targetLabel!=null) Text(targetLabel)
                Text(if (action == SecurityAction.REMOVE) strings.removePasswordWarning else strings.passwordHelp)
                if (needsPassword) OutlinedTextField(value = password, onValueChange = { password = it },
                    label = { Text(strings.passwordLabel) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation())
                if(action in setOf(SecurityAction.SET,SecurityAction.CHANGE)) OutlinedTextField(repeated,{repeated=it},label={Text(dev.pocket.notebook.feature.files.LocalPocketTools.current.text(dev.pocket.notebook.R.string.fs_repeat_password))},singleLine=true,visualTransformation=PasswordVisualTransformation())
                if (busy) CircularProgressIndicator()
                if (error != null) Text(error)
            }
        },
        confirmButton = {
            TextButton(enabled = valid && !busy, onClick = {
                val secret = password.toCharArray()
                password = ""
                onSubmit(secret)
            }) { Text(strings.confirm) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(strings.cancel) } }
    )
}

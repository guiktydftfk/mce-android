package cn.mce.starlight.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import cn.mce.starlight.data.AuthClient
import kotlinx.coroutines.launch

/**
 * 原生登录弹窗：直接 POST Discuz 登录表单，无需 WebView。
 */
@Composable
fun LoginDialog(
    onDismiss: () -> Unit,
    onSuccess: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text("登录 MCE 星光班", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("UID / 用户名 / 邮箱") },
                    singleLine = true,
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码") },
                    singleLine = true,
                    enabled = !loading,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                if (loading) {
                    Spacer(Modifier.height(12.dp))
                    CircularProgressIndicator(Modifier.height(20.dp))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !loading,
                onClick = {
                    loading = true
                    error = null
                    scope.launch {
                        when (val result = AuthClient.login(username.trim(), password)) {
                            is AuthClient.Result.Success -> {
                                loading = false
                                onSuccess()
                            }
                            is AuthClient.Result.Failure -> {
                                loading = false
                                error = result.message
                            }
                        }
                    }
                },
            ) { Text("登录") }
        },
        dismissButton = {
            TextButton(enabled = !loading, onClick = onDismiss) { Text("稍后") }
        },
    )
}

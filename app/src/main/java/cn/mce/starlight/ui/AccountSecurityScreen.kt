package cn.mce.starlight.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import cn.mce.starlight.core.UiError
import cn.mce.starlight.data.ProfileEditClient
import kotlinx.coroutines.launch

/**
 * v0.4.8：账号安全 — 修改密码 / 修改邮箱 / 安全问题。
 *
 * 复用个人资料编辑的通用表单解析（spacecp&ac=password），
 * 一个页面里按 Discuz 表单分组渲染并提交。
 */
@Composable
fun AccountSecurityScreen(
    onMenu: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var edit by remember { mutableStateOf<ProfileEditClient.EditForm?>(null) }
    val values = remember { mutableStateMapOf<String, String>() }

    LaunchedEffect(Unit) {
        loading = true
        error = null
        runCatching { ProfileEditClient.load("password") }
            .onSuccess { form ->
                edit = form
                if (form == null) error = "账号安全页加载失败，请稍后重试"
                form?.sections?.forEach { section ->
                    section.fields.forEach { f ->
                        // v0.4.8：密码类字段绝不预填（避免把邮箱等旧值误提交成新密码）
                        val passwordish = f.name.contains("password", true) || f.name.contains("pass", true)
                        if (f.kind != ProfileEditClient.Kind.HIDDEN && !passwordish && f.value.isNotBlank()) {
                            values.putIfAbsent(f.name, f.value)
                        }
                    }
                }
            }
            .onFailure { error = UiError.friendly(it) }
        loading = false
    }

    Box(Modifier.fillMaxSize()) {
        when {
            loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            error != null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(error!!, style = MaterialTheme.typography.bodyMedium)
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(
                    top = clampTopInset(WindowInsets.statusBars.asPaddingValues().calculateTopPadding()) + 96.dp,
                    start = 14.dp,
                    end = 14.dp,
                    bottom = 110.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                val form = edit
                if (form != null) {
                    items(form.sections.size) { si ->
                        val section = form.sections[si]
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(12.dp),
                                )
                                .padding(12.dp),
                        ) {
                            Text(
                                section.title.ifBlank { "账号设置" },
                                style = MaterialTheme.typography.titleSmall,
                            )
                            section.fields
                                .filter { it.kind != ProfileEditClient.Kind.HIDDEN }
                                .forEach { field ->
                                    val current = values[field.name] ?: field.value
                                    when (field.kind) {
                                        ProfileEditClient.Kind.OPTIONS, ProfileEditClient.Kind.SELECT -> {
                                            Text(
                                                field.label.ifBlank { field.name },
                                                style = MaterialTheme.typography.labelMedium,
                                            )
                                            Row(
                                                Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 4.dp),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                field.options.forEach { (value, label) ->
                                                    Text(
                                                        text = label.ifBlank { value },
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = if (current == value) MaterialTheme.colorScheme.onPrimaryContainer
                                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(10.dp))
                                                            .background(
                                                                if (current == value) MaterialTheme.colorScheme.primaryContainer
                                                                else MaterialTheme.colorScheme.surface,
                                                            )
                                                            .clickable { values[field.name] = value }
                                                            .padding(horizontal = 10.dp, vertical = 4.dp),
                                                    )
                                                }
                                            }
                                        }
                                        else -> {
                                            OutlinedTextField(
                                                value = current,
                                                onValueChange = { values[field.name] = it },
                                                label = { Text(field.label.ifBlank { field.name }) },
                                                singleLine = field.kind != ProfileEditClient.Kind.TEXTAREA,
                                                visualTransformation = if (
                                                    field.name.contains("password", true) ||
                                                    field.name.contains("pass", true)
                                                ) {
                                                    PasswordVisualTransformation()
                                                } else {
                                                    androidx.compose.ui.text.input.VisualTransformation.None
                                                },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 6.dp)
                                                    .heightIn(min = 52.dp),
                                            )
                                        }
                                    }
                                }
                            Button(
                                onClick = {
                                    scope.launch {
                                        // v0.4.8：密码类字段为空则不提交，避免误改密码
                                        val passwordFields = form.sections[si].fields.filter {
                                            it.kind != ProfileEditClient.Kind.HIDDEN &&
                                                (it.name.contains("password", true) || it.name.contains("pass", true))
                                        }
                                        val missing = passwordFields.any { (values[it.name] ?: "").isBlank() }
                                        if (passwordFields.isNotEmpty() && missing) {
                                            android.widget.Toast.makeText(ctx, "请先填写完整密码字段", android.widget.Toast.LENGTH_SHORT).show()
                                            return@launch
                                        }
                                        val snapshot = form.sections[si]
                                        val result = runCatching {
                                            ProfileEditClient.submit(
                                                ProfileEditClient.EditForm(listOf(snapshot)),
                                                values.toMap(),
                                            )
                                        }.getOrNull()
                                        android.widget.Toast.makeText(
                                            ctx,
                                            result?.second ?: if (result?.first == true) "保存成功" else "保存失败，请重试",
                                            android.widget.Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp),
                            ) { Text("保存「${section.title.ifBlank { "账号设置" }}」") }
                        }
                    }
                }
            }
        }
        ImmersiveTopBar(
            title = "账号安全",
            modifier = Modifier.align(Alignment.TopStart),
            onMenu = onMenu,
        )
    }
}

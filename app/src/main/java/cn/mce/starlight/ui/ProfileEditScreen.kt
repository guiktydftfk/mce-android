package cn.mce.starlight.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.mce.starlight.data.GenericListClient
import cn.mce.starlight.data.ProfileEditClient
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

/** 个人资料编辑：通用表单（联系方式 / 个人信息） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditScreen(
    onMenu: () -> Unit,
    onSaved: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var form by remember { mutableStateOf<ProfileEditClient.EditForm?>(null) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val values = remember { mutableStateMapOf<String, String>() }
    var op by remember { mutableStateOf("") }

    LaunchedEffect(op) {
        loading = true
        message = null
        values.clear()
        val f = ProfileEditClient.load(op)
        form = f
        f?.sections?.flatMap { it.fields }?.forEach { values[it.name] = it.value }
        loading = false
    }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(Modifier.fillMaxSize()) {
        if (loading) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        } else {
            val f = form
            if (f == null) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("无法加载资料编辑表单（可能未登录）")
                }
            } else {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(top = topInset + 70.dp, start = 16.dp, end = 16.dp, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val fields = f.sections.flatMap { it.fields }
                    fields.filter { it.kind != ProfileEditClient.Kind.HIDDEN }.forEach { field ->
                        when (field.kind) {
                            ProfileEditClient.Kind.SELECT, ProfileEditClient.Kind.OPTIONS -> {
                                var expanded by remember(field.name) { mutableStateOf(false) }
                                val options = if (field.options.isNotEmpty()) field.options
                                else listOf(field.value to field.value)
                                ExposedDropdownMenuBox(
                                    expanded = expanded,
                                    onExpandedChange = { expanded = !expanded },
                                ) {
                                    OutlinedTextField(
                                        value = options.firstOrNull { it.first == values[field.name] }
                                            ?.second ?: field.value,
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text(field.label) },
                                        trailingIcon = {
                                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                                        },
                                        modifier = Modifier
                                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                            .fillMaxWidth(),
                                    )
                                    ExposedDropdownMenu(
                                        expanded = expanded,
                                        onDismissRequest = { expanded = false },
                                    ) {
                                        options.forEach { option ->
                                            DropdownMenuItem(
                                                text = { Text(option.second.ifBlank { option.first }) },
                                                onClick = {
                                                    values[field.name] = option.first
                                                    expanded = false
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                            ProfileEditClient.Kind.TEXTAREA -> OutlinedTextField(
                                value = values[field.name] ?: "",
                                onValueChange = { values[field.name] = it },
                                label = { Text(field.label) },
                                minLines = 3,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            else -> OutlinedTextField(
                                value = values[field.name] ?: "",
                                onValueChange = { values[field.name] = it },
                                label = { Text(field.label) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    message?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    Button(
                        enabled = !saving,
                        onClick = {
                            saving = true
                            scope.launch {
                                val (ok, msg) = ProfileEditClient.submit(f, values.toMap())
                                saving = false
                                message = if (ok) msg else "\u4fdd\u5b58\u5931\u8d25\uff1a$msg"
                                if (ok) onSaved()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (saving) "保存中…" else "保存") }
                }
            }
        }

        ImmersiveTopBar(title = "编辑资料", modifier = Modifier.align(Alignment.TopStart), onMenu = onMenu)
    }
}

/** 通用列表页（勋章 / 好友 / 道具） */
@Composable
fun GenericListScreen(
    title: String,
    path: String,
    onMenu: () -> Unit,
    onOpenThread: (Int, String) -> Unit,
    onOpenUser: (Int, String) -> Unit,
) {
    var items by remember { mutableStateOf<List<GenericListClient.Item>?>(null) }

    LaunchedEffect(path) { items = GenericListClient.load(path) }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(Modifier.fillMaxSize()) {
        when (val list = items) {
            null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            else -> if (list.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("暂无内容", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(top = topInset + 64.dp, bottom = 110.dp)) {
                    itemsIndexed(list, key = { index, item -> "${item.url ?: item.title}#$index" }) { _, item ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = item.tid != null || item.uid != null) {
                                    when {
                                        item.tid != null -> onOpenThread(item.tid, item.title)
                                        item.uid != null -> onOpenUser(item.uid, item.title)
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            item.image?.let {
                                AsyncImage(
                                    model = it,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant,
                                            RoundedCornerShape(8.dp),
                                        ),
                                )
                                Spacer(Modifier.width(12.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    item.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                item.subtitle?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                        androidx.compose.material3.HorizontalDivider()
                    }
                }
            }
        }

        ImmersiveTopBar(title = title, modifier = Modifier.align(Alignment.TopStart), onMenu = onMenu)
    }
}

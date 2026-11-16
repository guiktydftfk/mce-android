package cn.mce.starlight.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cn.mce.starlight.core.AppLock
import cn.mce.starlight.core.SettingsStore

/**
 * v0.4.8：应用锁入口。
 *
 * - 开启后，冷启动 / 从后台回来需要先通过系统锁屏验证；
 * - 使用系统 Keyguard 确认页，不额外保存密码；
 * - 设备未设置锁屏时不允许开启（设置页会提示）。
 */
@Composable
fun LockGate(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    var locked by remember {
        mutableStateOf(SettingsStore.appLockEnabled && AppLock.isDeviceSecure(ctx))
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) locked = false
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && SettingsStore.appLockEnabled) {
                locked = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(Modifier.fillMaxSize()) {
        if (!locked) {
            content()
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("🔒", style = MaterialTheme.typography.displaySmall)
                Text(
                    "应用已锁定",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
                )
                Text(
                    "请验证设备锁屏以继续使用",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = {
                        val intent = AppLock.unlockIntent(ctx)
                        if (intent != null) {
                            launcher.launch(intent)
                        } else {
                            SettingsStore.updateAppLock(false)
                            locked = false
                        }
                    },
                    modifier = Modifier.padding(top = 20.dp),
                ) { Text("解锁") }
                TextButton(
                    onClick = {
                        SettingsStore.updateAppLock(false)
                        locked = false
                    },
                ) { Text("关闭应用锁并继续") }
            }
        }
    }
}

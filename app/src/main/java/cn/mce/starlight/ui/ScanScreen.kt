package cn.mce.starlight.ui

import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * 扫一扫（v0.3.4）。
 *
 * 环境说明：本项目离线构建，Gradle 缓存没有任何二维码解码库（zxing/MLKit 均不可得），
 * 因此用系统 WebView 自带的 BarcodeDetector 在网页层解码：
 *  - 用户从相册选图 / 现场拍照（`<input type=file capture>` 交给系统相机）
 *  - 网页内 canvas 读图 + `BarcodeDetector.detect()` 识别，零第三方依赖
 *  - WebView 不支持时退化为手动输入好友链接/UID（下方输入框始终可用）
 * 识别到内容后解析 `uid=` → 打开对方主页（主页里可加好友）。
 */
@Composable
fun ScanScreen(
    onMenu: () -> Unit,
    onScanned: (Int, String) -> Unit,
) {
    val context = LocalContext.current
    var manual by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("请选择一张二维码图片（可从相册选，也可现场拍照）") }
    var webReady by remember { mutableStateOf(true) }
    var fileCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

    fun handleResult(text: String) {
        when {
            text.startsWith("__UNSUPPORTED__") -> {
                status = "当前系统 WebView 不支持二维码识别，请用下方输入框粘贴好友链接或 UID"
                webReady = false
            }
            text.startsWith("__NO_FILE__") -> status = "没有选择到可用图片"
            else -> {
                val uid = extractUid(text)
                if (uid != null && uid > 0) onScanned(uid, "用户 $uid")
                else status = "识别到内容但无法解析 UID：$text"
            }
        }
    }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        val cb = fileCallback
        fileCallback = null
        if (uri == null) cb?.onReceiveValue(null) else cb?.onReceiveValue(arrayOf(uri))
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = true
                    settings.allowContentAccess = true
                    webChromeClient = object : WebChromeClient() {
                        override fun onShowFileChooser(
                            webView: WebView?,
                            filePathCallback: ValueCallback<Array<Uri>>?,
                            fileChooserParams: FileChooserParams?,
                        ): Boolean {
                            fileCallback = filePathCallback
                            runCatching { pickImage.launch("image/*") }
                            return true
                        }
                    }
                    addJavascriptInterface(
                        object {
                            @JavascriptInterface
                            fun result(value: String) {
                                android.os.Handler(android.os.Looper.getMainLooper())
                                    .post { handleResult(value) }
                            }
                        },
                        "MceScan",
                    )
                    loadDataWithBaseURL(
                        "https://mce.msi.cn/",
                        SCAN_HTML,
                        "text/html",
                        "UTF-8",
                        null,
                    )
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            if (!webReady) {
                Text(
                    status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
            }
            OutlinedTextField(
                value = manual,
                onValueChange = { manual = it },
                label = { Text("好友链接或 UID（手动兜底）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Button(
                enabled = manual.isNotBlank(),
                onClick = {
                    val uid = extractUid(manual)
                    if (uid != null && uid > 0) onScanned(uid, "用户 $uid")
                    else status = "无法从输入内容中解析出 UID"
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("打开好友主页") }
        }

        ImmersiveTopBar(title = "扫一扫", modifier = Modifier.align(Alignment.TopStart), onMenu = onMenu)
    }
}

private val uidRegex = Regex("""uid=(\d+)""")
private val pureUidRegex = Regex("""^\s*(\d{1,8})\s*$""")

/** 从扫码内容/手动输入中解析 uid（支持完整链接、uid=xxx、纯数字） */
internal fun extractUid(text: String): Int? =
    uidRegex.find(text)?.groupValues?.get(1)?.toIntOrNull()
        ?: pureUidRegex.find(text)?.groupValues?.get(1)?.toIntOrNull()

private const val SCAN_HTML = """
<!DOCTYPE html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<style>body{margin:0;background:#111;color:#ddd;font-family:sans-serif;text-align:center}
#pick{margin:40px auto;padding:14px 22px;font-size:16px;border-radius:10px;border:0;
background:#6c8cff;color:#fff;display:block}
#hint{margin:10px 18px;font-size:13px;color:#888}
#res{margin:8px 18px;font-size:14px;color:#9fe0a0;word-break:break-all}
</style></head><body>
<button id="pick">选择二维码图片 / 拍照</button>
<div id="hint">支持从相册选择，或现场拍照识别</div>
<canvas id="cv" style="display:none"></canvas>
<div id="res"></div>
<input type="file" id="file" accept="image/*" style="display:none">
<script>
(function(){
  function post(x){ try{ window.MceScan.result(x); }catch(e){} }
  var file=document.getElementById('file');
  var cv=document.getElementById('cv');
  var res=document.getElementById('res');
  if(!('BarcodeDetector' in window)){ post('__UNSUPPORTED__'); }
  document.getElementById('pick').onclick=function(){ file.click(); };
  file.onchange=function(){
    var f=file.files && file.files[0];
    if(!f){ post('__NO_FILE__'); return; }
    var img=new Image();
    img.onload=function(){
      cv.width=img.naturalWidth; cv.height=img.naturalHeight;
      cv.getContext('2d').drawImage(img,0,0);
      if(!('BarcodeDetector' in window)){ post('__UNSUPPORTED__'); return; }
      var det=new BarcodeDetector({formats:['qr_code']});
      det.detect(cv).then(function(codes){
        if(codes && codes.length>0){ res.textContent='已识别：'+codes[0].rawValue; post(codes[0].rawValue); }
        else { post('__UNSUPPORTED__'); }
      }).catch(function(){ post('__UNSUPPORTED__'); });
    };
    img.onerror=function(){ post('__NO_FILE__'); };
    img.src=URL.createObjectURL(f);
  };
})();
</script></body></html>
"""

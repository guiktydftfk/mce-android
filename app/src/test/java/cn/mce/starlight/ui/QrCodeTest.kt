package cn.mce.starlight.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File

/**
 * 自研二维码生成器回归（v0.3.4）。
 *
 * 参考矩阵由标准实现（node `qrcode` 库，version=4 / ECC=L / mask=0）离线生成并存为
 * `qr_reference.txt`，逐模块比对，确保生成的是**标准 QR 码**（微信/系统相机可识别）。
 */
class QrCodeTest {

    private fun load(name: String): String {
        val candidates = listOf(
            File("src/test/resources/$name"),
            File("app/src/test/resources/$name"),
        )
        val file = candidates.firstOrNull { it.exists() } ?: error("fixture not found: $name")
        return file.readText(Charsets.UTF_8)
    }

    @Test
    fun `encoder output matches reference QR matrices`() {
        val lines = load("qr_reference.txt").lines().filter { it.isNotBlank() }
        var index = 0
        var checked = 0
        while (index < lines.size) {
            val header = lines[index++]
            val payload = header.substringAfter("PAYLOAD=").substringBefore(" SIZE=")
            val size = header.substringAfter("SIZE=").trim().toInt()
            val expected = (0 until size).map { lines[index++] }
            val matrix = QrCode.encode(payload)
            assertNotNull("应能编码：$payload", matrix)
            val actual = matrix!!.map { row -> row.joinToString("") { if (it) "1" else "0" } }
            if (expected != actual) {
                val diffs = StringBuilder()
                for (r in expected.indices) {
                    if (expected[r] != actual[r]) {
                        val cols = expected[r].indices.filter { expected[r][it] != actual[r][it] }
                        diffs.appendLine("row $r diff cols=$cols")
                    }
                }
                error("二维码矩阵不一致：$payload\n$diffs")
            }
            checked++
        }
        assertEquals(3, checked)
    }

    @Test
    fun `too long payload is rejected`() {
        val longText = "x".repeat(100)
        assertEquals(null, QrCode.encode(longText))
    }
}
